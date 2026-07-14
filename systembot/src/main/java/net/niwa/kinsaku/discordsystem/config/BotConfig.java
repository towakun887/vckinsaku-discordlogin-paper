package net.niwa.kinsaku.discordsystem.config;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.File;

public class BotConfig {
    private static BotConfig instance;

    private final String botToken;
    private final String guildId;
    private final String adminRoleId;
    private final String whitelistedRoleId;
    private final String pluginApiUrl;
    private final String pluginApiKey;
    private final boolean botTestMode;
    private final String javaServerAddress;
    private final String bedrockServerAddress;
    private final String bedrockServerPort;

    private BotConfig() {
        Dotenv dotenv = null;
        try {
            // まずはカレントディレクトリの.envを読み込む
            dotenv = Dotenv.configure().ignoreIfMissing().load();

            // もしカレントディレクトリに .env がなく、一つ上のディレクトリにある場合はそちらを読み込む
            if (dotenv.get("DISCORD_BOT_TOKEN") == null) {
                File parentEnv = new File("../.env");
                if (parentEnv.exists()) {
                    dotenv = Dotenv.configure().directory("..").ignoreIfMissing().load();
                }
            }
        } catch (Exception e) {
            System.out.println("警告: .env ファイルの読み込み中にエラーが発生しました。環境変数を使用します: " + e.getMessage());
        }

        // 環境変数優先で読み込み、なければ.envから、それもなければデフォルト値
        this.botToken = getEnvOrDotenv(dotenv, "DISCORD_BOT_TOKEN", "");
        this.guildId = getEnvOrDotenv(dotenv, "DISCORD_GUILD_ID", "");
        this.adminRoleId = getEnvOrDotenv(dotenv, "ADMIN_ROLE_ID", "");
        this.whitelistedRoleId = getEnvOrDotenv(dotenv, "WHITELISTED_ROLE_ID", "");
        this.pluginApiUrl = getEnvOrDotenv(dotenv, "PLUGIN_API_URL", "http://localhost:25580");
        this.pluginApiKey = getEnvOrDotenv(dotenv, "PLUGIN_API_KEY", "your_shared_api_key_here");
        this.botTestMode = Boolean.parseBoolean(getEnvOrDotenv(dotenv, "BOT_TEST_MODE", "false"));
        this.javaServerAddress = getEnvOrDotenv(dotenv, "JAVA_SERVER_ADDRESS", "play.example.com");
        this.bedrockServerAddress = getEnvOrDotenv(dotenv, "BEDROCK_SERVER_ADDRESS", "play.example.com");
        this.bedrockServerPort = getEnvOrDotenv(dotenv, "BEDROCK_SERVER_PORT", "19132");
    }

    private String getEnvOrDotenv(Dotenv dotenv, String key, String defaultValue) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        if (dotenv != null) {
            String dotenvValue = dotenv.get(key);
            if (dotenvValue != null && !dotenvValue.isEmpty()) {
                return dotenvValue;
            }
        }
        return defaultValue;
    }

    public static synchronized BotConfig getInstance() {
        if (instance == null) {
            instance = new BotConfig();
        }
        return instance;
    }

    public String getBotToken() {
        return botToken;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getAdminRoleId() {
        return adminRoleId;
    }

    public String getWhitelistedRoleId() {
        return whitelistedRoleId;
    }

    public String getPluginApiUrl() {
        return pluginApiUrl;
    }

    public String getPluginApiKey() {
        return pluginApiKey;
    }

    public boolean isBotTestMode() {
        return botTestMode;
    }

    public String getJavaServerAddress() {
        return javaServerAddress;
    }

    public String getBedrockServerAddress() {
        return bedrockServerAddress;
    }

    public String getBedrockServerPort() {
        return bedrockServerPort;
    }
}
