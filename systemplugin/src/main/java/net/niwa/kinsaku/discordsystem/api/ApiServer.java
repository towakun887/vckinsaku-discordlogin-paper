package net.niwa.kinsaku.discordsystem.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.niwa.kinsaku.discordsystem.KinsakuDiscordSystem;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiServer {

    private final KinsakuDiscordSystem plugin;
    private HttpServer server;
    private ExecutorService executor;

    public ApiServer(KinsakuDiscordSystem plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int port = plugin.getApiPort();
        try {
            // ローカルホスト(127.0.0.1)にのみバインドし、外部からの直接アクセスを防止
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            
            // サーバー負荷を最小化するため、固定スレッドプール(2スレッド)を使用
            executor = Executors.newFixedThreadPool(2);
            server.setExecutor(executor);
            
            server.start();
            plugin.getLogger().info("API サーバーがポート " + port + " で起動しました。 (127.0.0.1 のみバインド)");
        } catch (IOException e) {
            plugin.getLogger().severe("API サーバーの起動中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        plugin.getLogger().info("API サーバーを停止しました。");
    }

    public void registerHandler(String path, HttpHandler handler) {
        if (server != null) {
            server.createContext(path, new AuthHandlerWrapper(handler));
        }
    }

    /**
     * 認証を行うラッパーハンドラ。
     * リクエストヘッダーの Authorization: Bearer <API_KEY> を検証。
     */
    private class AuthHandlerWrapper implements HttpHandler {
        private final HttpHandler delegate;

        public AuthHandlerWrapper(HttpHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getRequestHeaders();
            String authHeader = headers.getFirst("Authorization");
            String expectedKey = plugin.getApiKey();

            boolean authorized = false;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (token.equals(expectedKey)) {
                    authorized = true;
                }
            }

            if (!authorized) {
                plugin.getLogger().warning("未認証のAPIアクセスを拒否しました: " + exchange.getRemoteAddress());
                sendErrorResponse(exchange, SystemApiError.UNAUTHORIZED);
                return;
            }

            try {
                delegate.handle(exchange);
            } catch (Exception e) {
                plugin.getLogger().severe("APIハンドラ実行中に未キャッチのエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
                sendErrorResponse(exchange, SystemApiError.INTERNAL_ERROR, e.getMessage());
            }
        }
    }

    /**
     * HTTP レスポンス送信のユーティリティメソッド。
     */
    public static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendErrorResponse(HttpExchange exchange, SystemApiError error) throws IOException {
        sendResponse(exchange, error.getStatusCode(), 
            "{\"success\": false, \"error\": \"" + escapeJson(error.name()) + "\", \"message\": \"" + escapeJson(error.getFormattedMessage()) + "\"}");
    }

    public static void sendErrorResponse(HttpExchange exchange, SystemApiError error, String details) throws IOException {
        sendResponse(exchange, error.getStatusCode(), 
            "{\"success\": false, \"error\": \"" + escapeJson(error.name()) + "\", \"message\": \"" + escapeJson(error.getFormattedMessage(details)) + "\"}");
    }

    /**
     * JSON文字列値に埋め込む際に特殊文字をエスケープするユーティリティメソッド。
     * ダブルクォート、バックスラッシュ、改行等をエスケープし、JSON構文の破壊を防止する。
     */
    public static String escapeJson(String value) {
        if (value == null) return "null";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * クエリパラメータを解析するユーティリティメソッド。
     */
    public static java.util.Map<String, String> parseQueryParams(String query) {
        java.util.Map<String, String> params = new java.util.HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                try {
                    params.put(entry[0], java.net.URLDecoder.decode(entry[1], StandardCharsets.UTF_8.name()));
                } catch (Exception e) {
                    params.put(entry[0], entry[1]);
                }
            } else {
                params.put(entry[0], "");
            }
        }
        return params;
    }
}
