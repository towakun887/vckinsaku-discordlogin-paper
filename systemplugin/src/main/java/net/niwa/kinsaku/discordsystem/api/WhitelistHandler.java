package net.niwa.kinsaku.discordsystem.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.niwa.kinsaku.discordsystem.KinsakuDiscordSystem;
import net.niwa.kinsaku.discordsystem.database.DatabaseManager;
import net.niwa.kinsaku.discordsystem.database.UniversityCache;
import net.niwa.kinsaku.discordsystem.util.InputValidator;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.net.HttpURLConnection;
import java.net.URL;

public class WhitelistHandler implements HttpHandler {

    private final KinsakuDiscordSystem plugin;
    private final DatabaseManager dbManager;
    private final UniversityCache uniCache;
    private final Gson gson = new Gson();

    public WhitelistHandler(KinsakuDiscordSystem plugin, DatabaseManager dbManager, UniversityCache uniCache) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        this.uniCache = uniCache;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/api/whitelist/status") && "GET".equalsIgnoreCase(method)) {
            handleStatus(exchange);
            return;
        }

        if (path.equals("/api/whitelist/toggle") && "POST".equalsIgnoreCase(method)) {
            handleToggle(exchange);
            return;
        }

        if (path.equals("/api/whitelist") && "POST".equalsIgnoreCase(method)) {
            handleRegister(exchange);
            return;
        }

        ApiServer.sendResponse(exchange, 404, "{\"error\": \"Not Found\", \"message\": \"エンドポイントが見つかりません。\"}");
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        boolean enabled = Bukkit.hasWhitelist();
        ApiServer.sendResponse(exchange, 200, "{\"enabled\": " + enabled + "}");
    }

    private void handleToggle(HttpExchange exchange) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            JsonObject body = gson.fromJson(reader, JsonObject.class);
            if (body == null || !body.has("enabled")) {
                ApiServer.sendErrorResponse(exchange, SystemApiError.MISSING_FIELD, "enabled フィールドが必要です。");
                return;
            }
            boolean enabled = body.get("enabled").getAsBoolean();
            runOnMainThread(() -> {
                Bukkit.setWhitelist(enabled);
                Bukkit.reloadWhitelist();
                try {
                    ApiServer.sendResponse(exchange, 200, "{\"success\": true, \"enabled\": " + enabled + "}");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            ApiServer.sendErrorResponse(exchange, SystemApiError.INTERNAL_ERROR, e.getMessage());
        }
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            JsonObject body = gson.fromJson(reader, JsonObject.class);
            if (body == null) {
                ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "JSONボディが空です。");
                return;
            }

            // バリデーション
            if (!body.has("discord_id") || !body.has("university") ||
                    !body.has("minecraft_id") || !body.has("edition")) {
                ApiServer.sendErrorResponse(exchange, SystemApiError.MISSING_FIELD,
                        "必要なフィールド (discord_id, university, minecraft_id, edition) が不足しています。");
                return;
            }

            String discordId = body.get("discord_id").getAsString().trim();
            String universityName = body.get("university").getAsString().trim();
            String minecraftId = body.get("minecraft_id").getAsString().trim();
            String edition = body.get("edition").getAsString().trim().toUpperCase();

            // エディション値の確認
            if (!"JAVA".equals(edition) && !"BEDROCK".equals(edition)) {
                ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_EDITION,
                        "edition は 'JAVA' または 'BEDROCK' のみ許可されています。");
                return;
            }

            // 入力バリデーション & サニタイズ
            if (!InputValidator.validateMinecraftId(minecraftId, edition)) {
                ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_MC_ID, "Minecraft IDの形式が正しくありません。");
                return;
            }

            minecraftId = InputValidator.sanitize(minecraftId);
            discordId = InputValidator.sanitize(discordId);
            universityName = InputValidator.sanitize(universityName);
            String discordUsername = body.has("discord_username") ? body.get("discord_username").getAsString().trim()
                    : "";
            discordUsername = InputValidator.sanitize(discordUsername);

            // 大学の存在チェック (インメモリキャッシュから探索)
            DatabaseManager.University university = uniCache.getByName(universityName);
            if (university == null) {
                ApiServer.sendErrorResponse(exchange, SystemApiError.UNIVERSITY_NOT_FOUND, "登録されていない大学名です。");
                return;
            }

            final String finalMinecraftId = minecraftId;
            final String finalDiscordId = discordId;
            final DatabaseManager.University finalUniversity = university;
            final String finalDiscordUsername = discordUsername;

            // すでに別ユーザーでアクティブに登録されていないか重複チェック
            DatabaseManager.MinecraftAccount existingAccount = dbManager.getAccountByMinecraftId(finalMinecraftId);
            if (existingAccount != null && existingAccount.edition.equalsIgnoreCase(edition)) {
                // 重複登録を拒否
                ApiServer.sendErrorResponse(exchange, SystemApiError.MC_ID_DUPLICATE);
                return;
            }

            // 非同期でホワイトリスト設定 & DB保存
            processWhitelist(exchange, finalDiscordId, finalUniversity, finalMinecraftId, edition,
                    finalDiscordUsername);

        } catch (Exception e) {
            plugin.getLogger().severe("ホワイトリスト処理中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
            ApiServer.sendErrorResponse(exchange, SystemApiError.INTERNAL_ERROR, e.getMessage());
        }
    }

    private void processWhitelist(HttpExchange exchange, String discordId, DatabaseManager.University university,
            String minecraftId, String edition, String discordUsername) {
        CompletableFuture<String> uuidFuture = CompletableFuture.supplyAsync(() -> {
            String targetUuid = null;
            if ("JAVA".equals(edition)) {
                String mojangUuid = getMojangUuid(minecraftId);
                if (mojangUuid == null) {
                    throw new RuntimeException(SystemApiError.MC_NOT_FOUND_JE.getFormattedMessage());
                }
                // Mojang上で存在確認できたので、設定通りのオフラインUUIDを算出して使用する
                UUID offlineUuid = UUID
                        .nameUUIDFromBytes(("OfflinePlayer:" + minecraftId).getBytes(StandardCharsets.UTF_8));
                targetUuid = offlineUuid.toString();
            } else {
                // BEDROCK版の場合、GeyserAPI等で存在確認を行う
                String xuid = getGeyserXuid(minecraftId);
                if (xuid == null) {
                    throw new RuntimeException(SystemApiError.MC_NOT_FOUND_BE.getFormattedMessage());
                }
                // 不要なオフラインUUID算出は削除し、生の値（XUID）を記録する
                // （データベース上は minecraft_uuid カラムに生のXUID文字列が保存されます）
                targetUuid = xuid;
            }
            return targetUuid;
        });

        uuidFuture.thenAccept(uuid -> {
            // スケジューラを使ってメインスレッドでMinecraftへの適用を行う
            runOnMainThread(() -> {
                try {
                    boolean success = false;
                    String message = "";

                    if ("JAVA".equals(edition)) {
                        // 完全オリジナルの独自ホワイトリストシステムへ移行したため、Bukkit側のホワイトリストへは追加しない。
                        // PlayerLoginListener の ProfileWhitelistVerifyEvent フックでDBを参照して直接許可する。
                        success = true;
                        message = "Java Edition 独自ホワイトリストに追加しました。";
                    } else {
                        // Bedrock版の処理
                        if (plugin.isUseFloodgate()) {
                            // Floodgateコマンドを使用する (接頭辞なしの元のIDを指定)
                            // コマンド実行 (スペースを含むGamertagに対応するためダブルクォートで囲む)
                            String cmd = "fwhitelist add \"" + minecraftId + "\"";
                            success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                            message = success ? "Bedrock Edition (Floodgate) ホワイトリストに追加しました。"
                                    : "Floodgate コマンドの実行に失敗しました。";
                        } else {
                            // 【非常処理】コンセプト上の運用では走らない処理 (設定でFloodgate連携がオフの場合のフォールバック)
                            // 標準コマンドまたは標準のOfflinePlayerで追加 (接頭辞付きで処理)
                            String targetName = minecraftId;
                            String prefix = plugin.getBedrockPrefix();
                            if (prefix != null && !prefix.isEmpty() && !targetName.startsWith(prefix)) {
                                targetName = prefix + targetName;
                            }
                            targetName = targetName.replace(" ", "_");

                            // uuid (BE版はXUIDが格納されているため) からは直接UUIDへ変換できないため、名前からオフラインUUIDを再計算して登録する
                            UUID offlineUuid = UUID.nameUUIDFromBytes(
                                    ("OfflinePlayer:" + targetName).getBytes(StandardCharsets.UTF_8));
                            OfflinePlayer player = Bukkit.getOfflinePlayer(offlineUuid);
                            player.setWhitelisted(true);
                            Bukkit.reloadWhitelist();
                            success = true;
                            message = "Bedrock Edition (接頭辞付き: " + targetName + ") ホワイトリストに追加しました。";
                        }
                    }

                    if (success) {
                        // DBへの保存 (別スレッドで実行)
                        final String finalMessage = message;
                        CompletableFuture.runAsync(() -> {
                            try {
                                // トランザクション処理はDatabaseManager側で同期
                                DatabaseManager.DiscordUser user = dbManager.getOrCreateDiscordUser(discordId,
                                        university.id, discordUsername);
                                boolean dbSuccess = dbManager.addMinecraftAccount(user.id, minecraftId, uuid, edition);
                                if (dbSuccess) {
                                    ApiServer.sendResponse(exchange, 200, "{\"success\": true, \"message\": \""
                                            + finalMessage + "\", \"minecraft_uuid\": \"" + uuid + "\"}");
                                } else {
                                    ApiServer.sendErrorResponse(exchange, SystemApiError.PLAYER_DUPLICATE);
                                }
                            } catch (SQLException | IOException e) {
                                plugin.getLogger().severe("データベース保存中にエラーが発生しました: " + e.getMessage());
                                try {
                                    ApiServer.sendErrorResponse(exchange, SystemApiError.DB_ERROR, e.getMessage());
                                } catch (IOException ignored) {
                                }
                            }
                        });
                    } else {
                        ApiServer.sendErrorResponse(exchange, SystemApiError.INTERNAL_ERROR, message);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("メインスレッドでのホワイトリスト適用中にエラーが発生しました: " + e.getMessage());
                    try {
                        ApiServer.sendErrorResponse(exchange, SystemApiError.INTERNAL_ERROR, "サーバーエラーが発生しました。");
                    } catch (IOException ignored) {
                    }
                }
            });
        }).exceptionally(ex -> {
            String errMsg = ex.getMessage() != null ? ex.getMessage() : "";
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                errMsg = ex.getCause().getMessage();
            }
            plugin.getLogger().warning("ホワイトリスト追加処理でエラーが発生しました: " + errMsg);
            try {
                if (errMsg.contains("[ERR_MC_NOT_FOUND]")) {
                    ApiServer.sendResponse(exchange, 400,
                            "{\"success\": false, \"error\": \"Not Found\", \"message\": \""
                                    + ApiServer.escapeJson(errMsg) + "\"}");
                } else {
                    ApiServer.sendErrorResponse(exchange, SystemApiError.INTERNAL_ERROR, "処理中にエラーが発生しました。");
                }
            } catch (IOException ignored) {
            }
            return null;
        });
    }

    private String getMojangUuid(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(),
                        StandardCharsets.UTF_8)) {
                    JsonObject json = gson.fromJson(reader, JsonObject.class);
                    if (json != null && json.has("id")) {
                        String rawUuid = json.get("id").getAsString();
                        // 8-4-4-4-12の標準UUIDフォーマットに整形
                        return rawUuid.replaceFirst(
                                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                                "$1-$2-$3-$4-$5");
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to fetch Mojang UUID for " + username + ": " + e.getMessage());
        }
        return null;
    }

    private String getGeyserXuid(String gamertag) {
        try {
            String encoded = java.net.URLEncoder.encode(gamertag, StandardCharsets.UTF_8.toString()).replace("+",
                    "%20");
            URL url = new URL("https://api.geysermc.org/v2/xbox/xuid/" + encoded);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(),
                        StandardCharsets.UTF_8)) {
                    JsonObject json = gson.fromJson(reader, JsonObject.class);
                    if (json != null && json.has("xuid")) {
                        return json.get("xuid").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to fetch Geyser XUID for " + gamertag + ": " + e.getMessage());
        }
        return null;
    }

    private void runOnMainThread(Runnable runnable) {
        if (plugin.isFolia()) {
            Bukkit.getServer().getGlobalRegionScheduler().run(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }
}