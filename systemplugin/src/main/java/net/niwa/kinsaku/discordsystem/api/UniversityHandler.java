package net.niwa.kinsaku.discordsystem.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.niwa.kinsaku.discordsystem.KinsakuDiscordSystem;
import net.niwa.kinsaku.discordsystem.database.DatabaseManager;
import net.niwa.kinsaku.discordsystem.database.UniversityCache;
import net.niwa.kinsaku.discordsystem.util.InputValidator;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public class UniversityHandler implements HttpHandler {

    private final KinsakuDiscordSystem plugin;
    private final DatabaseManager dbManager;
    private final UniversityCache uniCache;
    private final Gson gson = new Gson();

    public UniversityHandler(KinsakuDiscordSystem plugin, DatabaseManager dbManager, UniversityCache uniCache) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        this.uniCache = uniCache;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if ("GET".equalsIgnoreCase(method)) {
                if (path.equals("/api/universities")) {
                    handleGetList(exchange);
                } else {
                    ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "エンドポイントが見つかりません。");
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                if (path.equals("/api/universities")) {
                    handleCreate(exchange);
                } else if (path.equals("/api/universities/reload")) {
                    handleReload(exchange);
                } else {
                    ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "エンドポイントが見つかりません。");
                }
            } else if ("DELETE".equalsIgnoreCase(method)) {
                if (path.startsWith("/api/universities/")) {
                    handleDelete(exchange, path.substring("/api/universities/".length()));
                } else {
                    ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "エンドポイントが見つかりません。");
                }
            } else if ("PUT".equalsIgnoreCase(method)) {
                if (path.startsWith("/api/universities/")) {
                    handleUpdate(exchange, path.substring("/api/universities/".length()));
                } else {
                    ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "エンドポイントが見つかりません。");
                }
            } else {
                ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "許可されていないHTTPメソッドです。");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("大学管理API処理中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
            ApiServer.sendErrorResponse(exchange, SystemApiError.INTERNAL_ERROR, e.getMessage());
        }
    }

    // GET /api/universities
    private void handleGetList(HttpExchange exchange) throws IOException {
        String json = gson.toJson(uniCache.getAll());
        ApiServer.sendResponse(exchange, 200, json);
    }

    // POST /api/universities
    private void handleCreate(HttpExchange exchange) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            JsonObject body = gson.fromJson(reader, JsonObject.class);
            if (body == null || !body.has("name")) {
                ApiServer.sendResponse(exchange, 400,
                        "{\"error\": \"Bad Request\", \"message\": \"'name' フィールドが必要です。\"}");
                return;
            }

            String name = body.get("name").getAsString().trim();
            String discordRoleId = body.has("discord_role_id") ? body.get("discord_role_id").getAsString().trim()
                    : null;
            String iconUrl = body.has("icon_url") ? body.get("icon_url").getAsString().trim() : null;

            if (!InputValidator.validateUniversityName(name)) {
                ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "大学名が無効、または文字数が範囲外(2〜100文字)です。");
                return;
            }

            String finalName = InputValidator.sanitize(name);
            String finalRoleId = InputValidator.sanitize(discordRoleId);
            String finalIconUrl = iconUrl != null ? InputValidator.sanitize(iconUrl) : null;

            CompletableFuture.runAsync(() -> {
                try {
                    boolean success = dbManager.addUniversity(finalName, finalRoleId, finalIconUrl);
                    if (success) {
                        uniCache.reload(); // キャッシュのリロード
                        ApiServer.sendResponse(exchange, 201, "{\"success\": true, \"message\": \"大学を追加しました。\"}");
                    } else {
                        ApiServer.sendErrorResponse(exchange, SystemApiError.UNIVERSITY_DUPLICATE);
                    }
                } catch (SQLException | IOException e) {
                    plugin.getLogger().severe("大学登録エラー: " + e.getMessage());
                    try {
                        ApiServer.sendErrorResponse(exchange, SystemApiError.DB_ERROR, e.getMessage());
                    } catch (IOException ignored) {
                    }
                }
            });
        }
    }

    // DELETE /api/universities/{id}
    private void handleDelete(HttpExchange exchange, String idStr) throws IOException {
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "無効な大学IDです。");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                boolean success = dbManager.removeUniversity(id);
                if (success) {
                    uniCache.reload(); // キャッシュリロード
                    ApiServer.sendResponse(exchange, 200, "{\"success\": true, \"message\": \"大学を削除しました。\"}");
                } else {
                    ApiServer.sendErrorResponse(exchange, SystemApiError.UNIVERSITY_NOT_FOUND, "対象の大学が見つかりません。");
                }
            } catch (SQLException | IOException e) {
                plugin.getLogger().severe("大学削除エラー: " + e.getMessage());
                try {
                    ApiServer.sendErrorResponse(exchange, SystemApiError.DB_ERROR, e.getMessage());
                } catch (IOException ignored) {
                }
            }
        });
    }

    // PUT /api/universities/{id}
    private void handleUpdate(HttpExchange exchange, String idStr) throws IOException {
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "無効な大学IDです。");
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            JsonObject body = gson.fromJson(reader, JsonObject.class);
            if (body == null || !body.has("name")) {
                ApiServer.sendErrorResponse(exchange, SystemApiError.MISSING_FIELD, "'name' フィールドが必要です。");
                return;
            }

            String name = body.get("name").getAsString().trim();
            String discordRoleId = body.has("discord_role_id") ? body.get("discord_role_id").getAsString().trim()
                    : null;
            String iconUrl = body.has("icon_url") ? body.get("icon_url").getAsString().trim() : null;

            if (!InputValidator.validateUniversityName(name)) {
                ApiServer.sendErrorResponse(exchange, SystemApiError.INVALID_REQUEST, "大学名が無効、または文字数が範囲外(2〜100文字)です。");
                return;
            }

            String finalName = InputValidator.sanitize(name);
            String finalRoleId = InputValidator.sanitize(discordRoleId);
            String finalIconUrl = iconUrl != null ? InputValidator.sanitize(iconUrl) : null;

            CompletableFuture.runAsync(() -> {
                try {
                    boolean success = dbManager.updateUniversity(id, finalName, finalRoleId, finalIconUrl);
                    if (success) {
                        uniCache.reload(); // キャッシュリロード
                        ApiServer.sendResponse(exchange, 200, "{\"success\": true, \"message\": \"大学情報を更新しました。\"}");
                    } else {
                        ApiServer.sendErrorResponse(exchange, SystemApiError.UNIVERSITY_NOT_FOUND,
                                "対象の大学が見つからないか、既に同じ名前が存在します。");
                    }
                } catch (SQLException | IOException e) {
                    plugin.getLogger().severe("大学更新エラー: " + e.getMessage());
                    try {
                        ApiServer.sendErrorResponse(exchange, SystemApiError.DB_ERROR, e.getMessage());
                    } catch (IOException ignored) {
                    }
                }
            });
        }
    }

    // POST /api/universities/reload
    private void handleReload(HttpExchange exchange) throws IOException {
        CompletableFuture.runAsync(() -> {
            uniCache.reload();
            try {
                ApiServer.sendResponse(exchange, 200, "{\"success\": true, \"message\": \"大学キャッシュをリフレッシュしました。\"}");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
