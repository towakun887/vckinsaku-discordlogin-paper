package net.niwa.kinsaku.discordsystem;

import net.niwa.kinsaku.discordsystem.api.ApiServer;
import net.niwa.kinsaku.discordsystem.api.PlayersHandler;
import net.niwa.kinsaku.discordsystem.api.UniversityHandler;
import net.niwa.kinsaku.discordsystem.api.WhitelistHandler;
import net.niwa.kinsaku.discordsystem.command.KinsakuCommand;
import net.niwa.kinsaku.discordsystem.database.DatabaseManager;
import net.niwa.kinsaku.discordsystem.database.UniversityCache;
import net.niwa.kinsaku.discordsystem.listener.PlayerLoginListener;
import org.bukkit.plugin.java.JavaPlugin;
import java.sql.SQLException;

public final class KinsakuDiscordSystem extends JavaPlugin {

    private int apiPort;
    private String apiKey;
    private String dbFile;
    private String bedrockPrefix;
    private boolean useFloodgate;
    private String whitelistKickMessage;

    private DatabaseManager dbManager;
    private UniversityCache uniCache;
    private ApiServer apiServer;

    @Override
    public void onEnable() {
        String version = getPluginMeta().getVersion();
        getLogger().info("KinsakuDiscordSystem v" + version + " が有効化されました。");
        
        // 設定ファイルのロード
        saveDefaultConfig();
        loadConfigValues();

        // Folia互換性の確認
        if (isFolia()) {
            getLogger().info("Folia サーバー環境を検出しました。");
        } else {
            getLogger().info("Paper サーバー環境を検出しました。");
        }

        // データベース初期化
        dbManager = new DatabaseManager(this);
        try {
            dbManager.initialize();
            getLogger().info("SQLite データベースが正常に接続されました。");
        } catch (SQLException e) {
            getLogger().severe("データベース初期化エラー: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 大学リストキャッシュの初期化
        uniCache = new UniversityCache(this, dbManager);
        uniCache.load();

        // API サーバー初期化
        apiServer = new ApiServer(this);
        apiServer.start();

        // API ハンドラの登録
        registerApiHandlers();

        // 管理コマンド登録
        KinsakuCommand kinsakuCmd = new KinsakuCommand(this, uniCache);
        if (getCommand("kinsaku-usersystem") != null) {
            getCommand("kinsaku-usersystem").setExecutor(kinsakuCmd);
            getCommand("kinsaku-usersystem").setTabCompleter(kinsakuCmd);
        }

        // イベントリスナー登録
        getServer().getPluginManager().registerEvents(new PlayerLoginListener(this, dbManager), this);
    }

    private void loadConfigValues() {
        this.apiPort = getConfig().getInt("api.port", 25580);
        this.apiKey = getConfig().getString("api.key", "your-shared-api-key-here");
        this.dbFile = getConfig().getString("database.file", "kinsaku.db");
        this.bedrockPrefix = getConfig().getString("crossplay.bedrock-prefix", ".");
        this.useFloodgate = getConfig().getBoolean("crossplay.use-floodgate", true);
        this.whitelistKickMessage = getConfig().getString("messages.whitelist-kick", "あなたはホワイトリストに登録されていません。");
    }

    private void registerApiHandlers() {
        if (apiServer == null) return;

        // エンドポイント登録
        apiServer.registerHandler("/api/whitelist", new WhitelistHandler(this, dbManager, uniCache));
        
        PlayersHandler playersHandler = new PlayersHandler(this, dbManager);
        apiServer.registerHandler("/api/players", playersHandler);
        apiServer.registerHandler("/api/statistics", playersHandler);
        apiServer.registerHandler("/api/accounts", playersHandler);

        UniversityHandler uniHandler = new UniversityHandler(this, dbManager, uniCache);
        apiServer.registerHandler("/api/universities", uniHandler);
    }

    @Override
    public void onDisable() {
        // API サーバーの停止
        if (apiServer != null) {
            apiServer.stop();
        }

        // データベース接続の切断
        if (dbManager != null) {
            dbManager.close();
        }

        getLogger().info("KinsakuDiscordSystem が無効化されました。");
    }

    public boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // ゲッター
    public int getApiPort() { return apiPort; }
    public String getApiKey() { return apiKey; }
    public String getDbFile() { return dbFile; }
    public String getBedrockPrefix() { return bedrockPrefix; }
    public boolean isUseFloodgate() { return useFloodgate; }
    public DatabaseManager getDbManager() { return dbManager; }
    public UniversityCache getUniCache() { return uniCache; }
    public String getWhitelistKickMessage() { return whitelistKickMessage; }
}
