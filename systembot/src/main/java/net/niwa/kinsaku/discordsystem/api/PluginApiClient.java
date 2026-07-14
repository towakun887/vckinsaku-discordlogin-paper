package net.niwa.kinsaku.discordsystem.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.niwa.kinsaku.discordsystem.config.BotConfig;
import net.niwa.kinsaku.discordsystem.model.WhitelistRequest;
import net.niwa.kinsaku.discordsystem.model.WhitelistResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PluginApiClient {

    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;
    private final Gson gson = new Gson();

    private static final Map<String, String> universityRoleMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final List<UniversityInfo> cachedUniversities = new java.util.concurrent.CopyOnWriteArrayList<>();

    public List<UniversityInfo> getCachedUniversities() {
        return cachedUniversities;
    }

    public static String getUniversityDisplay(String universityName) {
        if (universityName == null) {
            return "";
        }
        String roleId = universityRoleMap.get(universityName);
        if (roleId != null && !roleId.isEmpty()) {
            return "<@&" + roleId + ">";
        }
        return universityName;
    }

    public CompletableFuture<Void> refreshUniversityCache() {
        return getUniversities().thenAccept(unis -> {
        });
    }

    // アカウント情報用のモデルクラス
    public static class PlayerAccount {
        public int id;
        public int discordUserId;
        public String discordId;
        public String universityName;
        public String minecraftId;
        public String minecraftUuid;
        public String edition;
        public String registeredAt;
        public String lastLoginAt;
        public boolean isActive;
    }

    // 大学情報用のモデルクラス
    public static class UniversityInfo {
        public int id;
        public String name;
        public String discordRoleId;
        public String iconUrl;
        public boolean isActive;
        public String createdAt;
    }

    // テストデータ用のモデルクラス
    public static class TestData {
        public List<PlayerAccount> playerInfo;
        public List<PlayerAccount> searchPlayer;
        public List<PlayerAccount> playerList;
        public Map<String, Object> statistics;
        public List<UniversityInfo> universities;
        public List<OnlinePlayerDetail> onlinePlayersDetail;
    }

    private TestData testData;

    public PluginApiClient() {
        this.httpClient = HttpClient.newHttpClient();
        BotConfig config = BotConfig.getInstance();
        this.apiUrl = config.getPluginApiUrl();
        this.apiKey = config.getPluginApiKey();

        if (config.isBotTestMode()) {
            try {
                String json = java.nio.file.Files.readString(java.nio.file.Path.of("test_data.json"));
                this.testData = gson.fromJson(json, TestData.class);
            } catch (Exception e) {
                System.err.println("test_data.json の読み込みに失敗しました: " + e.getMessage());
            }
        }
    }

    private HttpRequest.Builder createRequestBuilder(String endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + endpoint))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=utf-8");
    }

    /**
     * ホワイトリスト追加
     */
    public CompletableFuture<WhitelistResponse> addToWhitelist(WhitelistRequest request) {
        if (BotConfig.getInstance().isBotTestMode()) {
            WhitelistResponse resp = gson.fromJson(
                    "{\"success\":true,\"message\":\"[TEST MODE] ホワイトリスト登録を偽装しました。\",\"minecraft_uuid\":\""
                            + java.util.UUID.randomUUID() + "\"}",
                    WhitelistResponse.class);
            return CompletableFuture.completedFuture(resp);
        }

        String json = gson.toJson(request);
        HttpRequest httpRequest = createRequestBuilder("/api/whitelist")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), WhitelistResponse.class);
                    } else {
                        // エラーハンドリング
                        WhitelistResponse errResp = gson.fromJson(response.body(), WhitelistResponse.class);
                        if (errResp != null) {
                            return errResp;
                        }
                        // 予期せぬエラー
                        return gson.fromJson("{\"success\":false,\"error\":\"HTTP " + response.statusCode()
                                + "\",\"message\":\"APIサーバーがエラーを返しました。\"}", WhitelistResponse.class);
                    }
                });
    }

    /**
     * アカウントの個別削除
     */
    public CompletableFuture<WhitelistResponse> removeAccount(int accountId) {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture.completedFuture(
                    gson.fromJson("{\"success\":true,\"message\":\"[TEST MODE]\"}", WhitelistResponse.class));
        }

        HttpRequest httpRequest = createRequestBuilder("/api/accounts/" + accountId)
                .DELETE()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), WhitelistResponse.class);
                    } else {
                        WhitelistResponse errResp = gson.fromJson(response.body(), WhitelistResponse.class);
                        if (errResp != null && errResp.getMessage() != null) {
                            return errResp;
                        }
                        return gson.fromJson("{\"success\":false,\"error\":\"HTTP " + response.statusCode()
                                + "\",\"message\":\"APIサーバーがエラーを返しました。\"}", WhitelistResponse.class);
                    }
                });
    }

    /**
     * Discordユーザーの全アカウント削除
     */
    public CompletableFuture<WhitelistResponse> removeAllAccounts(String discordId) {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture.completedFuture(
                    gson.fromJson("{\"success\":true,\"message\":\"[TEST MODE]\"}", WhitelistResponse.class));
        }

        HttpRequest httpRequest = createRequestBuilder("/api/accounts/by-discord/" + discordId)
                .DELETE()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), WhitelistResponse.class);
                    } else {
                        WhitelistResponse errResp = gson.fromJson(response.body(), WhitelistResponse.class);
                        if (errResp != null && errResp.getMessage() != null) {
                            return errResp;
                        }
                        return gson.fromJson("{\"success\":false,\"error\":\"HTTP " + response.statusCode()
                                + "\",\"message\":\"APIサーバーがエラーを返しました。\"}", WhitelistResponse.class);
                    }
                });
    }

    /**
     * Discord ID に紐づく全アカウント情報の取得
     */
    public CompletableFuture<List<PlayerAccount>> getPlayerInfo(String discordId) {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture
                    .completedFuture(testData != null && testData.playerInfo != null ? testData.playerInfo : List.of());
        }

        HttpRequest httpRequest = createRequestBuilder("/api/players/" + discordId)
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        java.lang.reflect.Type listType = new TypeToken<List<PlayerAccount>>() {
                        }.getType();
                        return gson.fromJson(response.body(), listType);
                    }
                    return List.of();
                });
    }

    /**
     * プレイヤー検索（あいまい検索）
     */
    public CompletableFuture<List<PlayerAccount>> searchPlayer(String query) {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture.completedFuture(
                    testData != null && testData.searchPlayer != null ? testData.searchPlayer : List.of());
        }

        HttpRequest httpRequest = createRequestBuilder(
                "/api/players/search?q=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8))
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        java.lang.reflect.Type listType = new TypeToken<List<PlayerAccount>>() {
                        }.getType();
                        return gson.fromJson(response.body(), listType);
                    }
                    return List.of();
                });
    }

    /**
     * プレイヤー一覧取得（フィルタ付き）
     */
    public CompletableFuture<List<PlayerAccount>> getPlayerList(String university, String edition) {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture
                    .completedFuture(testData != null && testData.playerList != null ? testData.playerList : List.of());
        }

        StringBuilder query = new StringBuilder("?");
        if (university != null && !university.isEmpty()) {
            query.append("university=").append(java.net.URLEncoder.encode(university, StandardCharsets.UTF_8))
                    .append("&");
        }
        if (edition != null && !edition.isEmpty()) {
            query.append("edition=").append(java.net.URLEncoder.encode(edition, StandardCharsets.UTF_8)).append("&");
        }

        HttpRequest httpRequest = createRequestBuilder("/api/players" + query)
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        java.lang.reflect.Type listType = new TypeToken<List<PlayerAccount>>() {
                        }.getType();
                        return gson.fromJson(response.body(), listType);
                    }
                    return List.of();
                });
    }

    /**
     * 統計情報の取得
     */
    public CompletableFuture<Map<String, Object>> getStatistics() {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture
                    .completedFuture(testData != null && testData.statistics != null ? testData.statistics : Map.of());
        }

        HttpRequest httpRequest = createRequestBuilder("/api/statistics")
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        java.lang.reflect.Type mapType = new TypeToken<Map<String, Object>>() {
                        }.getType();
                        return gson.fromJson(response.body(), mapType);
                    }
                    return Map.of();
                });
    }

    /**
     * 大学一覧の取得
     */
    public CompletableFuture<List<UniversityInfo>> getUniversities() {
        if (BotConfig.getInstance().isBotTestMode()) {
            List<UniversityInfo> list = testData != null && testData.universities != null ? testData.universities
                    : List.of();
            universityRoleMap.clear();
            cachedUniversities.clear();
            for (UniversityInfo uni : list) {
                if (uni.discordRoleId != null) {
                    universityRoleMap.put(uni.name, uni.discordRoleId);
                }
                cachedUniversities.add(uni);
            }
            return CompletableFuture.completedFuture(list);
        }

        HttpRequest httpRequest = createRequestBuilder("/api/universities")
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        java.lang.reflect.Type listType = new TypeToken<List<UniversityInfo>>() {
                        }.getType();
                        List<UniversityInfo> list = gson.fromJson(response.body(), listType);
                        universityRoleMap.clear();
                        cachedUniversities.clear();
                        for (UniversityInfo uni : list) {
                            if (uni.discordRoleId != null) {
                                universityRoleMap.put(uni.name, uni.discordRoleId);
                            }
                            cachedUniversities.add(uni);
                        }
                        return list;
                    }
                    return List.of();
                });
    }

    /**
     * 大学の追加
     */
    public CompletableFuture<Boolean> addUniversity(String name, String roleId, String iconUrl) {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture.completedFuture(true);
        }

        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        if (roleId != null && !roleId.isEmpty()) {
            body.addProperty("discord_role_id", roleId);
        }
        if (iconUrl != null && !iconUrl.isEmpty()) {
            body.addProperty("icon_url", iconUrl);
        }

        HttpRequest httpRequest = createRequestBuilder("/api/universities")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> response.statusCode() == 201);
    }

    /**
     * 大学情報の更新
     */
    public CompletableFuture<Boolean> updateUniversity(int id, String name, String roleId, String iconUrl) {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture.completedFuture(true);
        }

        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        if (roleId != null && !roleId.isEmpty()) {
            body.addProperty("discord_role_id", roleId);
        }
        if (iconUrl != null && !iconUrl.isEmpty()) {
            body.addProperty("icon_url", iconUrl);
        }

        HttpRequest httpRequest = createRequestBuilder("/api/universities/" + id)
                .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> response.statusCode() == 200);
    }

    /**
     * 大学の削除
     */
    public CompletableFuture<Boolean> removeUniversity(int universityId) {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture.completedFuture(true);
        }

        HttpRequest httpRequest = createRequestBuilder("/api/universities/" + universityId)
                .DELETE()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> response.statusCode() == 200);
    }

    public CompletableFuture<Boolean> reloadUniversities() {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture.completedFuture(true);
        }

        HttpRequest httpRequest = createRequestBuilder("/api/universities/reload")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> response.statusCode() == 200);
    }

    public static class OnlinePlayerDetail {
        public String minecraftId;
        public String discordId;
        public String universityRoleId;
        public String edition;
    }

    /**
     * ホワイトリストの有効/無効状態を取得する
     */
    public CompletableFuture<Boolean> isWhitelistEnabled() {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture.completedFuture(true);
        }

        HttpRequest httpRequest = createRequestBuilder("/api/whitelist/status")
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        JsonObject obj = gson.fromJson(response.body(), JsonObject.class);
                        return obj.has("enabled") && obj.get("enabled").getAsBoolean();
                    }
                    return false;
                });
    }

    /**
     * ホワイトリストの有効/無効状態を切り替える
     */
    public CompletableFuture<Boolean> setWhitelistEnabled(boolean enabled) {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture.completedFuture(true);
        }

        JsonObject body = new JsonObject();
        body.addProperty("enabled", enabled);

        HttpRequest httpRequest = createRequestBuilder("/api/whitelist/toggle")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> response.statusCode() == 200);
    }

    /**
     * 現在オンライン中のプレイヤー詳細情報（Minecraft ID, Discord ID, 大学ロールID）一覧を取得
     */
    public CompletableFuture<List<OnlinePlayerDetail>> getOnlinePlayersDetail() {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture.completedFuture(
                    testData != null && testData.onlinePlayersDetail != null ? testData.onlinePlayersDetail
                            : List.of());
        }

        HttpRequest httpRequest = createRequestBuilder("/api/players/online")
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        java.lang.reflect.Type listType = new TypeToken<List<OnlinePlayerDetail>>() {
                        }.getType();
                        return gson.fromJson(response.body(), listType);
                    }
                    return List.of();
                });
    }

    /**
     * Discord IDとDisplayNameの対応リストを送信し、プラグイン側の表示名キャッシュを一括更新する
     */
    public CompletableFuture<Boolean> updateDiscordNames(List<Map<String, String>> nameUpdates) {
        if (BotConfig.getInstance().isBotTestMode()) {
            return CompletableFuture.completedFuture(true);
        }

        String json = gson.toJson(nameUpdates);
        HttpRequest httpRequest = createRequestBuilder("/api/players/update-names")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> response.statusCode() == 200);
    }
}
