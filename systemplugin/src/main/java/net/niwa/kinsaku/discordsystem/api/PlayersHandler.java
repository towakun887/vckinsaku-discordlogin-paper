package net.niwa.kinsaku.discordsystem.api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.niwa.kinsaku.discordsystem.KinsakuDiscordSystem;
import net.niwa.kinsaku.discordsystem.database.DatabaseManager;
import net.niwa.kinsaku.discordsystem.database.DatabaseManager.MinecraftAccount;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.entity.Player;

public class PlayersHandler implements HttpHandler {

    private final KinsakuDiscordSystem plugin;
    private final DatabaseManager dbManager;
    private final Gson gson = new Gson();

    public PlayersHandler(KinsakuDiscordSystem plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if ("GET".equalsIgnoreCase(method)) {
                if (path.startsWith("/api/players/search")) {
                    handleSearch(exchange);
                } else if (path.equals("/api/players/online")) {
                    handleGetOnlinePlayers(exchange);
                } else if (path.startsWith("/api/players/")) {
                    // /api/players/{discordId}
                    handleGetByDiscordId(exchange, path.substring("/api/players/".length()));
                } else if (path.equals("/api/players")) {
                    handleGetList(exchange);
                } else if (path.equals("/api/statistics")) {
                    handleGetStatistics(exchange);
                } else {
                    ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "エンドポイントが見つかりません。");
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                if (path.equals("/api/players/update-names")) {
                    handleUpdateNames(exchange);
                } else {
                    ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "エンドポイントが見つかりません。");
                }
            } else if ("DELETE".equalsIgnoreCase(method)) {
                if (path.startsWith("/api/accounts/by-discord/")) {
                    handleDeleteByDiscord(exchange, path.substring("/api/accounts/by-discord/".length()));
                } else if (path.startsWith("/api/accounts/")) {
                    handleDeleteById(exchange, path.substring("/api/accounts/".length()));
                } else {
                    ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "エンドポイントが見つかりません。");
                }
            } else {
                ApiServer.sendResponse(exchange, 405,
                        "{\"error\": \"Method Not Allowed\", \"message\": \"許可されていないHTTPメソッドです。\"}");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("プレイヤー情報API処理中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
            ApiServer.sendErrorResponse(exchange, SystemApiError.INTERNAL_ERROR, e.getMessage());
        }
    }

    // GET /api/players
    private void handleGetList(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> params = ApiServer.parseQueryParams(exchange.getRequestURI().getQuery());
        String university = params.get("university");
        String edition = params.get("edition");

        CompletableFuture.supplyAsync(() -> {
            try {
                return dbManager.getAllActiveAccounts(university, edition);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(accounts -> {
            try {
                String response = gson.toJson(accounts);
                ApiServer.sendResponse(exchange, 200, response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).exceptionally(ex -> {
            sendError(exchange, "データベースの取得に失敗しました: " + ex.getMessage());
            return null;
        });
    }

    // GET /api/players/{discordId}
    private void handleGetByDiscordId(HttpExchange exchange, String discordId) throws IOException {
        if (discordId == null || discordId.isEmpty()) {
            ApiServer.sendErrorResponse(exchange, SystemApiError.MISSING_FIELD, "discordId が指定されていません。");
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                return dbManager.getActiveAccountsByDiscordId(discordId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(accounts -> {
            try {
                String response = gson.toJson(accounts);
                ApiServer.sendResponse(exchange, 200, response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).exceptionally(ex -> {
            sendError(exchange, "データベースの取得に失敗しました: " + ex.getMessage());
            return null;
        });
    }

    // GET /api/players/search?q=...
    private void handleSearch(HttpExchange exchange) throws IOException {
        Map<String, String> params = ApiServer.parseQueryParams(exchange.getRequestURI().getQuery());
        String query = params.get("q");

        if (query == null || query.isEmpty()) {
            ApiServer.sendErrorResponse(exchange, SystemApiError.MISSING_FIELD, "クエリパラメータ 'q' が必要です。");
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                return dbManager.searchAccounts(query);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(accounts -> {
            try {
                String response = gson.toJson(accounts);
                ApiServer.sendResponse(exchange, 200, response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).exceptionally(ex -> {
            sendError(exchange, "検索に失敗しました: " + ex.getMessage());
            return null;
        });
    }

    // GET /api/statistics
    private void handleGetStatistics(HttpExchange exchange) throws IOException {
        CompletableFuture.supplyAsync(() -> {
            try {
                return dbManager.getStatistics();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(stats -> {
            try {
                String response = gson.toJson(stats);
                ApiServer.sendResponse(exchange, 200, response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).exceptionally(ex -> {
            sendError(exchange, "統計の取得に失敗しました: " + ex.getMessage());
            return null;
        });
    }

    // DELETE /api/accounts/{accountId} (個別削除)
    private void handleDeleteById(HttpExchange exchange, String accountIdStr) throws IOException {
        int accountId;
        try {
            accountId = Integer.parseInt(accountIdStr);
        } catch (NumberFormatException e) {
            ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "無効な accountId です。");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // まずアカウント情報を取得
                MinecraftAccount target = dbManager.getAccountById(accountId);

                if (target == null) {
                    ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "対象のアカウントが見つかりません。");
                    return;
                }

                // ホワイトリストから削除 (メインスレッド)
                MinecraftAccount finalTarget = target;
                runOnMainThread(() -> {
                    boolean wlSuccess;
                    if ("JAVA".equalsIgnoreCase(finalTarget.edition)) {
                        // 独自ホワイトリスト機能を利用しているため、Bukkit側の削除は不要
                        wlSuccess = true;
                    } else {
                        if (plugin.isUseFloodgate()) {
                            wlSuccess = Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                    "fwhitelist remove \"" + finalTarget.minecraftId + "\"");
                        } else {
                            // 【非常処理】コンセプト上の運用では走らない処理 (設定でFloodgate連携がオフの場合のフォールバック)
                            String targetName = finalTarget.minecraftId;
                            String prefix = plugin.getBedrockPrefix();
                            if (prefix != null && !prefix.isEmpty() && !targetName.startsWith(prefix)) {
                                targetName = prefix + targetName;
                            }
                            targetName = targetName.replace(" ", "_");

                            // minecraftUuid (BE版はXUIDが格納されているため) からは直接UUIDへ変換できないため、名前からオフラインUUIDを再計算して処理する
                            UUID offlineUuid = UUID.nameUUIDFromBytes(
                                    ("OfflinePlayer:" + targetName).getBytes(StandardCharsets.UTF_8));
                            OfflinePlayer player = Bukkit.getOfflinePlayer(offlineUuid);
                            player.setWhitelisted(false);
                            Bukkit.reloadWhitelist();
                            wlSuccess = true;
                        }
                    }

                    // DB上の論理削除を実行 (非同期)
                    if (wlSuccess) {
                        CompletableFuture.runAsync(() -> {
                            try {
                                boolean dbSuccess = dbManager.deactivateAccount(accountId);
                                if (dbSuccess) {
                                    ApiServer.sendResponse(exchange, 200,
                                            "{\"success\": true, \"message\": \"アカウントを削除し、ホワイトリストから解除しました。\"}");
                                } else {
                                    ApiServer.sendResponse(exchange, 500,
                                            "{\"success\": false, \"message\": \"ホワイトリストは解除されましたが、DBの更新に失敗しました。\"}");
                                }
                            } catch (Exception e) {
                                plugin.getLogger().severe("DB削除処理エラー: " + e.getMessage());
                                try {
                                    ApiServer.sendResponse(exchange, 500,
                                            "{\"success\": false, \"message\": \"DB削除処理中にエラーが発生しました。\"}");
                                } catch (IOException ignored) {
                                }
                            }
                        });
                    } else {
                        try {
                            ApiServer.sendResponse(exchange, 500,
                                    "{\"success\": false, \"message\": \"ホワイトリストからの削除コマンド実行に失敗しました。\"}");
                        } catch (IOException ignored) {
                        }
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().severe("削除処理中にエラーが発生しました: " + e.getMessage());
                try {
                    ApiServer.sendResponse(exchange, 500,
                            "{\"error\": \"Internal Server Error\", \"message\": \""
                                    + ApiServer.escapeJson(e.getMessage()) + "\"}");
                } catch (IOException ignored) {
                }
            }
        });
    }

    // DELETE /api/accounts/by-discord/{discordId} (Discordユーザーに紐づく全アカウント削除)
    private void handleDeleteByDiscord(HttpExchange exchange, String discordId) throws IOException {
        if (discordId == null || discordId.isEmpty()) {
            ApiServer.sendResponse(exchange, 400,
                    "{\"error\": \"Bad Request\", \"message\": \"discordId が指定されていません。\"}");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // Discord ID に紐づくアクティブアカウント一覧を取得
                List<MinecraftAccount> accounts = dbManager.getActiveAccountsByDiscordId(discordId);
                if (accounts.isEmpty()) {
                    ApiServer.sendResponse(exchange, 404,
                            "{\"error\": \"Not Found\", \"message\": \"削除対象のアカウントが見つかりません。\"}");
                    return;
                }

                // メメインスレッドで全アカウントをホワイトリストから削除
                runOnMainThread(() -> {
                    try {
                        for (MinecraftAccount acc : accounts) {
                            if ("JAVA".equalsIgnoreCase(acc.edition)) {
                                // 独自ホワイトリスト機能を利用しているため、Bukkit側の削除は不要
                            } else {
                                if (plugin.isUseFloodgate()) {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                            "fwhitelist remove \"" + acc.minecraftId + "\"");
                                } else {
                                    // 【非常処理】コンセプト上の運用では走らない処理 (設定でFloodgate連携がオフの場合のフォールバック)
                                    String targetName = acc.minecraftId;
                                    String prefix = plugin.getBedrockPrefix();
                                    if (prefix != null && !prefix.isEmpty() && !targetName.startsWith(prefix)) {
                                        targetName = prefix + targetName;
                                    }
                                    targetName = targetName.replace(" ", "_");

                                    // minecraftUuid (BE版はXUIDが格納されているため) からは直接UUIDへ変換できないため、名前からオフラインUUIDを再計算して処理する
                                    UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName)
                                            .getBytes(StandardCharsets.UTF_8));
                                    OfflinePlayer player = Bukkit.getOfflinePlayer(offlineUuid);
                                    player.setWhitelisted(false);
                                }
                            }
                        }
                        Bukkit.reloadWhitelist();

                        // DBの論理削除を実行 (非同期)
                        CompletableFuture.runAsync(() -> {
                            try {
                                DatabaseManager.DiscordUser user = dbManager.getDiscordUserByDiscordId(discordId);
                                if (user != null) {
                                    dbManager.deactivateAllAccounts(user.id);
                                    ApiServer.sendResponse(exchange, 200,
                                            "{\"success\": true, \"message\": \"ユーザーに紐づく全アカウントを削除し、ホワイトリストから解除しました。\"}");
                                } else {
                                    ApiServer.sendResponse(exchange, 500,
                                            "{\"success\": false, \"message\": \"ホワイトリストは解除されましたが、DBユーザーの特定に失敗しました。\"}");
                                }
                            } catch (Exception e) {
                                plugin.getLogger().severe("DB削除(全件)エラー: " + e.getMessage());
                                try {
                                    ApiServer.sendResponse(exchange, 500,
                                            "{\"success\": false, \"message\": \"DB削除(全件)処理中にエラーが発生しました。\"}");
                                } catch (IOException ignored) {
                                }
                            }
                        });

                    } catch (Exception e) {
                        plugin.getLogger().severe("全件削除中のメインスレッド処理エラー: " + e.getMessage());
                        try {
                            ApiServer.sendResponse(exchange, 500,
                                    "{\"success\": false, \"message\": \"全件削除のメインスレッド処理中にエラーが発生しました。\"}");
                        } catch (IOException ignored) {
                        }
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().severe("全件削除処理エラー: " + e.getMessage());
                try {
                    ApiServer.sendResponse(exchange, 500,
                            "{\"error\": \"Internal Server Error\", \"message\": \""
                                    + ApiServer.escapeJson(e.getMessage()) + "\"}");
                } catch (IOException ignored) {
                }
            }
        });
    }

    private void runOnMainThread(Runnable runnable) {
        if (plugin.isFolia()) {
            Bukkit.getServer().getGlobalRegionScheduler().run(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    private void sendError(HttpExchange exchange, String message) {
        try {
            ApiServer.sendResponse(exchange, 500,
                    "{\"error\": \"Internal Server Error\", \"message\": \"" + ApiServer.escapeJson(message) + "\"}");
        } catch (IOException ignored) {
        }
    }

    @SuppressWarnings("null") // online一覧を回すだけ
    private void handleGetOnlinePlayers(HttpExchange exchange) throws IOException {
        java.util.Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Player p : online) {
            names.add(p.getName());
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                return dbManager.getOnlinePlayerDetails(names);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(details -> {
            try {
                String response = gson.toJson(details);
                ApiServer.sendResponse(exchange, 200, response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).exceptionally(ex -> {
            sendError(exchange, "オンラインプレイヤー詳細の取得に失敗しました: " + ex.getMessage());
            return null;
        });
    }

    private void handleUpdateNames(HttpExchange exchange) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(),
                StandardCharsets.UTF_8)) {
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {
            }.getType();
            List<Map<String, String>> updates = gson.fromJson(reader, listType);
            if (updates == null) {
                ApiServer.sendResponse(exchange, 400, "{\"error\": \"Bad Request\", \"message\": \"リクエストボディが空です。\"}");
                return;
            }

            CompletableFuture.runAsync(() -> {
                try {
                    dbManager.updateDiscordUsernames(updates);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }).thenRun(() -> {
                try {
                    ApiServer.sendResponse(exchange, 200, "{\"success\": true, \"message\": \"表示名を一括更新しました。\"}");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).exceptionally(ex -> {
                sendError(exchange, "表示名の一括更新に失敗しました: " + ex.getMessage());
                return null;
            });
        } catch (Exception e) {
            ApiServer.sendResponse(exchange, 500, "{\"error\": \"Internal Server Error\", \"message\": \""
                    + ApiServer.escapeJson(e.getMessage()) + "\"}");
        }
    }
}