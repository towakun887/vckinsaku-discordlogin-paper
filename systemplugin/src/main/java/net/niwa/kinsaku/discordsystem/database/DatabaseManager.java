package net.niwa.kinsaku.discordsystem.database;

import net.niwa.kinsaku.discordsystem.KinsakuDiscordSystem;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {

    private final KinsakuDiscordSystem plugin;
    private final String dbPath;
    private Connection connection;

    // データモデルクラス
    public static class University {
        public int id;
        public String name;
        public String discordRoleId;
        public String iconUrl;
        public boolean isActive;
        public String createdAt;

        public University(int id, String name, String discordRoleId, String iconUrl, boolean isActive, String createdAt) {
            this.id = id;
            this.name = name;
            this.discordRoleId = discordRoleId;
            this.iconUrl = iconUrl;
            this.isActive = isActive;
            this.createdAt = createdAt;
        }
    }

    public static class DiscordUser {
        public int id;
        public String discordId;
        public int universityId;
        public String discordUsername;
        public String registeredAt;

        public DiscordUser(int id, String discordId, int universityId, String discordUsername, String registeredAt) {
            this.id = id;
            this.discordId = discordId;
            this.universityId = universityId;
            this.discordUsername = discordUsername;
            this.registeredAt = registeredAt;
        }
    }

    public static class MinecraftAccount {
        public int id;
        public int discordUserId;
        public String discordId; // JOIN用
        public String universityName; // JOIN用
        public String minecraftId;
        public String minecraftUuid; // JAVAの場合はオフラインUUID、BEDROCKの場合は生のXUIDが格納される
        public String edition;
        public String registeredAt;
        public String lastLoginAt;
        public boolean isActive;

        public MinecraftAccount(int id, int discordUserId, String discordId, String universityName,
                                String minecraftId, String minecraftUuid, String edition,
                                String registeredAt, String lastLoginAt, boolean isActive) {
            this.id = id;
            this.discordUserId = discordUserId;
            this.discordId = discordId;
            this.universityName = universityName;
            this.minecraftId = minecraftId;
            this.minecraftUuid = minecraftUuid;
            this.edition = edition;
            this.registeredAt = registeredAt;
            this.lastLoginAt = lastLoginAt;
            this.isActive = isActive;
        }
    }

    public DatabaseManager(KinsakuDiscordSystem plugin) {
        this.plugin = plugin;
        this.dbPath = new File(plugin.getDataFolder(), plugin.getDbFile()).getAbsolutePath();
    }

    // DB接続とテーブル初期化
    public synchronized void initialize() throws SQLException {
        // データフォルダが存在しない場合は作成
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC ドライバが見つかりません。", e);
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        
        // 外部キー制約の有効化
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }

        createTables();
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // universities テーブル
            stmt.execute("CREATE TABLE IF NOT EXISTS universities (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL UNIQUE," +
                    "discord_role_id TEXT," +
                    "is_active BOOLEAN DEFAULT 1," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");");

            // discord_users テーブル
            stmt.execute("CREATE TABLE IF NOT EXISTS discord_users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "discord_id TEXT NOT NULL UNIQUE," +
                    "university_id INTEGER NOT NULL," +
                    "discord_username TEXT," +
                    "registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (university_id) REFERENCES universities(id)" +
                    ");");

            // minecraft_accounts テーブル
            stmt.execute("CREATE TABLE IF NOT EXISTS minecraft_accounts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "discord_user_id INTEGER NOT NULL," +
                    "minecraft_id TEXT NOT NULL," +
                    "minecraft_uuid TEXT," +
                    "edition TEXT NOT NULL CHECK(edition IN ('JAVA', 'BEDROCK'))," +
                    "registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "lastlogin_at TIMESTAMP," +
                    "is_active BOOLEAN DEFAULT 1," +
                    "FOREIGN KEY (discord_user_id) REFERENCES discord_users(id)" +
                    ");");

            // インデックスの作成
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_du_discord_id ON discord_users(discord_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mc_discord_user ON minecraft_accounts(discord_user_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mc_minecraft_id ON minecraft_accounts(minecraft_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mc_edition ON minecraft_accounts(edition);");
            
            // アクティブなアカウントに対するユニーク制約（同じIDとエディションの重複登録を防止）
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_mc_unique_active ON minecraft_accounts(minecraft_id, edition) WHERE is_active = 1;");

            // 既存DBへのマイグレーション: カラムが存在しない場合に discord_username を追加
            try (Statement migrationStmt = connection.createStatement()) {
                migrationStmt.execute("ALTER TABLE discord_users ADD COLUMN discord_username TEXT;");
            } catch (SQLException e) {
                // カラムが既に存在する場合は例外が発生するので無視してよい
            }

            // 既存DBへのマイグレーション: カラムが存在しない場合に icon_url を追加
            try (Statement migrationStmt = connection.createStatement()) {
                migrationStmt.execute("ALTER TABLE universities ADD COLUMN icon_url TEXT;");
            } catch (SQLException e) {
                // カラムが既に存在する場合は例外が発生するので無視してよい
            }
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("SQLite データベース接続をクローズしました。");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("データベースクローズ中にエラーが発生しました: " + e.getMessage());
        }
    }

    // --- 大学管理メソッド ---

    public synchronized boolean addUniversity(String name, String discordRoleId, String iconUrl) throws SQLException {
        // すでに存在して非アクティブな場合は再アクティブ化、存在しなければ挿入
        String selectSql = "SELECT id, is_active FROM universities WHERE name = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(selectSql)) {
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    boolean isActive = rs.getBoolean("is_active");
                    if (!isActive) {
                        String updateSql = "UPDATE universities SET is_active = 1, discord_role_id = ?, icon_url = ? WHERE id = ?;";
                        try (PreparedStatement updPstmt = connection.prepareStatement(updateSql)) {
                            updPstmt.setString(1, discordRoleId);
                            updPstmt.setString(2, iconUrl);
                            updPstmt.setInt(3, id);
                            updPstmt.executeUpdate();
                            return true;
                        }
                    }
                    return false; // すでにアクティブな状態で存在
                }
            }
        }

        String insertSql = "INSERT INTO universities (name, discord_role_id, icon_url) VALUES (?, ?, ?);";
        try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, discordRoleId);
            pstmt.setString(3, iconUrl);
            pstmt.executeUpdate();
            return true;
        }
    }

    public synchronized boolean removeUniversity(int id) throws SQLException {
        // 論理削除
        String sql = "UPDATE universities SET is_active = 0 WHERE id = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    public synchronized boolean updateUniversity(int id, String name, String discordRoleId, String iconUrl) throws SQLException {
        String sql = "UPDATE universities SET name = ?, discord_role_id = ?, icon_url = ? WHERE id = ? AND is_active = 1;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, discordRoleId);
            pstmt.setString(3, iconUrl);
            pstmt.setInt(4, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    public synchronized List<University> getActiveUniversities() throws SQLException {
        List<University> list = new ArrayList<>();
        String sql = "SELECT * FROM universities WHERE is_active = 1 ORDER BY name ASC;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new University(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("discord_role_id"),
                        rs.getString("icon_url"),
                        rs.getBoolean("is_active"),
                        rs.getString("created_at")
                ));
            }
        }
        return list;
    }

    public synchronized University getUniversityById(int id) throws SQLException {
        String sql = "SELECT * FROM universities WHERE id = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new University(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("discord_role_id"),
                            rs.getString("icon_url"),
                            rs.getBoolean("is_active"),
                            rs.getString("created_at")
                    );
                }
            }
        }
        return null;
    }

    public synchronized University getUniversityByName(String name) throws SQLException {
        String sql = "SELECT * FROM universities WHERE name = ? AND is_active = 1;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new University(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("discord_role_id"),
                            rs.getString("icon_url"),
                            rs.getBoolean("is_active"),
                            rs.getString("created_at")
                    );
                }
            }
        }
        return null;
    }

    // --- Discordユーザー管理メソッド ---

    public synchronized DiscordUser getOrCreateDiscordUser(String discordId, int universityId, String discordUsername) throws SQLException {
        String selectSql = "SELECT * FROM discord_users WHERE discord_id = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(selectSql)) {
            pstmt.setString(1, discordId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    int oldUniId = rs.getInt("university_id");
                    String oldUsername = rs.getString("discord_username");
                    
                    boolean needUpdate = false;
                    if (oldUniId != universityId || 
                        (discordUsername != null && !discordUsername.equals(oldUsername))) {
                        String updateSql = "UPDATE discord_users SET university_id = ?, discord_username = ? WHERE discord_id = ?;";
                        try (PreparedStatement updPstmt = connection.prepareStatement(updateSql)) {
                            updPstmt.setInt(1, universityId);
                            updPstmt.setString(2, discordUsername);
                            updPstmt.setString(3, discordId);
                            updPstmt.executeUpdate();
                        }
                        needUpdate = true;
                    }
                    
                    return new DiscordUser(id, discordId, universityId, 
                            needUpdate ? discordUsername : oldUsername, 
                            rs.getString("registered_at"));
                }
            }
        }

        String insertSql = "INSERT INTO discord_users (discord_id, university_id, discord_username) VALUES (?, ?, ?);";
        try (PreparedStatement pstmt = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, discordId);
            pstmt.setInt(2, universityId);
            pstmt.setString(3, discordUsername);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int generatedId = keys.getInt(1);
                    return new DiscordUser(generatedId, discordId, universityId, discordUsername, null);
                }
            }
        }
        return null;
    }

    public synchronized DiscordUser getDiscordUserByDiscordId(String discordId) throws SQLException {
        String sql = "SELECT * FROM discord_users WHERE discord_id = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, discordId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new DiscordUser(
                            rs.getInt("id"),
                            rs.getString("discord_id"),
                            rs.getInt("university_id"),
                            rs.getString("discord_username"),
                            rs.getString("registered_at")
                    );
                }
            }
        }
        return null;
    }

    public synchronized void updateUserUniversity(String discordId, int universityId) throws SQLException {
        String sql = "UPDATE discord_users SET university_id = ? WHERE discord_id = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, universityId);
            pstmt.setString(2, discordId);
            pstmt.executeUpdate();
        }
    }

    // --- Minecraftアカウント管理メソッド ---

    public synchronized boolean addMinecraftAccount(int discordUserId, String minecraftId, String minecraftUuid, String edition) throws SQLException {
        // 重複チェック及び既存レコード取得（同じ minecraft_id + edition）
        String checkSql = "SELECT id, is_active FROM minecraft_accounts WHERE minecraft_id = ? AND edition = ?;";
        int inactiveId = -1;
        try (PreparedStatement pstmt = connection.prepareStatement(checkSql)) {
            pstmt.setString(1, minecraftId);
            pstmt.setString(2, edition);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    boolean isActive = rs.getBoolean("is_active");
                    if (isActive) {
                        return false; // すでにアクティブなアカウントが存在する
                    }
                    inactiveId = rs.getInt("id"); // 非アクティブなレコードのIDを保持
                }
            }
        }

        if (inactiveId != -1) {
            // 非アクティブな既存レコードを再アクティブ化
            String updateSql = "UPDATE minecraft_accounts SET discord_user_id = ?, minecraft_uuid = ?, is_active = 1, registered_at = CURRENT_TIMESTAMP WHERE id = ?;";
            try (PreparedStatement pstmt = connection.prepareStatement(updateSql)) {
                pstmt.setInt(1, discordUserId);
                pstmt.setString(2, minecraftUuid);
                pstmt.setInt(3, inactiveId);
                pstmt.executeUpdate();
                return true;
            }
        }

        // 新規追加
        String insertSql = "INSERT INTO minecraft_accounts (discord_user_id, minecraft_id, minecraft_uuid, edition) VALUES (?, ?, ?, ?);";
        try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
            pstmt.setInt(1, discordUserId);
            pstmt.setString(2, minecraftId);
            pstmt.setString(3, minecraftUuid);
            pstmt.setString(4, edition);
            pstmt.executeUpdate();
            return true;
        }
    }

    public synchronized boolean deactivateAccount(int accountId) throws SQLException {
        String sql = "UPDATE minecraft_accounts SET is_active = 0 WHERE id = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, accountId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public synchronized boolean deactivateAllAccounts(int discordUserId) throws SQLException {
        String sql = "UPDATE minecraft_accounts SET is_active = 0 WHERE discord_user_id = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, discordUserId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public synchronized List<MinecraftAccount> getActiveAccountsByDiscordId(String discordId) throws SQLException {
        List<MinecraftAccount> list = new ArrayList<>();
        String sql = "SELECT ma.*, du.discord_id, u.name as university_name " +
                "FROM minecraft_accounts ma " +
                "JOIN discord_users du ON ma.discord_user_id = du.id " +
                "JOIN universities u ON du.university_id = u.id " +
                "WHERE du.discord_id = ? AND ma.is_active = 1;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, discordId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new MinecraftAccount(
                            rs.getInt("id"),
                            rs.getInt("discord_user_id"),
                            rs.getString("discord_id"),
                            rs.getString("university_name"),
                            rs.getString("minecraft_id"),
                            rs.getString("minecraft_uuid"),
                            rs.getString("edition"),
                            rs.getString("registered_at"),
                            rs.getString("lastlogin_at"),
                            rs.getBoolean("is_active")
                    ));
                }
            }
        }
        return list;
    }

    public synchronized MinecraftAccount getAccountByMinecraftId(String minecraftId) throws SQLException {
        String sql = "SELECT ma.*, du.discord_id, u.name as university_name " +
                "FROM minecraft_accounts ma " +
                "JOIN discord_users du ON ma.discord_user_id = du.id " +
                "JOIN universities u ON du.university_id = u.id " +
                "WHERE ma.minecraft_id = ? AND ma.is_active = 1;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, minecraftId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new MinecraftAccount(
                            rs.getInt("id"),
                            rs.getInt("discord_user_id"),
                            rs.getString("discord_id"),
                            rs.getString("university_name"),
                            rs.getString("minecraft_id"),
                            rs.getString("minecraft_uuid"),
                            rs.getString("edition"),
                            rs.getString("registered_at"),
                            rs.getString("lastlogin_at"),
                            rs.getBoolean("is_active")
                    );
                }
            }
        }
        return null;
    }

    public synchronized MinecraftAccount getAccountById(int id) throws SQLException {
        String sql = "SELECT ma.*, du.discord_id, u.name as university_name " +
                "FROM minecraft_accounts ma " +
                "JOIN discord_users du ON ma.discord_user_id = du.id " +
                "JOIN universities u ON du.university_id = u.id " +
                "WHERE ma.id = ? AND ma.is_active = 1;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new MinecraftAccount(
                            rs.getInt("id"),
                            rs.getInt("discord_user_id"),
                            rs.getString("discord_id"),
                            rs.getString("university_name"),
                            rs.getString("minecraft_id"),
                            rs.getString("minecraft_uuid"),
                            rs.getString("edition"),
                            rs.getString("registered_at"),
                            rs.getString("lastlogin_at"),
                            rs.getBoolean("is_active")
                    );
                }
            }
        }
        return null;
    }



    public synchronized List<MinecraftAccount> searchAccounts(String query) throws SQLException {
        List<MinecraftAccount> list = new ArrayList<>();
        String sql = "SELECT ma.*, du.discord_id, u.name as university_name " +
                "FROM minecraft_accounts ma " +
                "JOIN discord_users du ON ma.discord_user_id = du.id " +
                "JOIN universities u ON du.university_id = u.id " +
                "WHERE (ma.minecraft_id LIKE ? OR du.discord_id = ?) AND ma.is_active = 1;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, "%" + query + "%");
            pstmt.setString(2, query);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new MinecraftAccount(
                            rs.getInt("id"),
                            rs.getInt("discord_user_id"),
                            rs.getString("discord_id"),
                            rs.getString("university_name"),
                            rs.getString("minecraft_id"),
                            rs.getString("minecraft_uuid"),
                            rs.getString("edition"),
                            rs.getString("registered_at"),
                            rs.getString("lastlogin_at"),
                            rs.getBoolean("is_active")
                    ));
                }
            }
        }
        return list;
    }

    public synchronized List<MinecraftAccount> getAllActiveAccounts(String universityFilter, String editionFilter) throws SQLException {
        List<MinecraftAccount> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT ma.*, du.discord_id, u.name as university_name " +
                "FROM minecraft_accounts ma " +
                "JOIN discord_users du ON ma.discord_user_id = du.id " +
                "JOIN universities u ON du.university_id = u.id " +
                "WHERE ma.is_active = 1"
        );

        if (universityFilter != null && !universityFilter.isEmpty()) {
            sql.append(" AND u.name = ?");
        }
        if (editionFilter != null && !editionFilter.isEmpty()) {
            sql.append(" AND ma.edition = ?");
        }
        sql.append(" ORDER BY ma.registered_at DESC;");

        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            if (universityFilter != null && !universityFilter.isEmpty()) {
                pstmt.setString(paramIndex++, universityFilter);
            }
            if (editionFilter != null && !editionFilter.isEmpty()) {
                pstmt.setString(paramIndex, editionFilter.toUpperCase());
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new MinecraftAccount(
                            rs.getInt("id"),
                            rs.getInt("discord_user_id"),
                            rs.getString("discord_id"),
                            rs.getString("university_name"),
                            rs.getString("minecraft_id"),
                            rs.getString("minecraft_uuid"),
                            rs.getString("edition"),
                            rs.getString("registered_at"),
                            rs.getString("lastlogin_at"),
                            rs.getBoolean("is_active")
                    ));
                }
            }
        }
        return list;
    }

    public synchronized void updateLastLogin(String minecraftId) throws SQLException {
        String sql = "UPDATE minecraft_accounts SET lastlogin_at = CURRENT_TIMESTAMP WHERE minecraft_id = ? AND is_active = 1;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, minecraftId);
            pstmt.executeUpdate();
        }
    }

    // --- 統計メソッド ---

    public synchronized Map<String, Object> getStatistics() throws SQLException {
        Map<String, Object> stats = new HashMap<>();

        // 総アカウント数
        String totalSql = "SELECT COUNT(*) as cnt FROM minecraft_accounts WHERE is_active = 1;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(totalSql)) {
            if (rs.next()) {
                stats.put("total_accounts", rs.getInt("cnt"));
            }
        }

        // 総ユーザー数（Discordユーザー数）
        String totalUsersSql = "SELECT COUNT(DISTINCT discord_user_id) as cnt FROM minecraft_accounts WHERE is_active = 1;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(totalUsersSql)) {
            if (rs.next()) {
                stats.put("total_users", rs.getInt("cnt"));
            }
        }

        // エディション別集計
        String editionSql = "SELECT edition, COUNT(*) as cnt FROM minecraft_accounts WHERE is_active = 1 GROUP BY edition;";
        Map<String, Integer> editions = new HashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(editionSql)) {
            while (rs.next()) {
                editions.put(rs.getString("edition"), rs.getInt("cnt"));
            }
        }
        stats.put("editions", editions);

        // 大学別集計
        String uniSql = "SELECT u.name as uni_name, COUNT(*) as cnt " +
                "FROM minecraft_accounts ma " +
                "JOIN discord_users du ON ma.discord_user_id = du.id " +
                "JOIN universities u ON du.university_id = u.id " +
                "WHERE ma.is_active = 1 GROUP BY u.name;";
        Map<String, Integer> universities = new HashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(uniSql)) {
            while (rs.next()) {
                universities.put(rs.getString("uni_name"), rs.getInt("cnt"));
            }
        }
        stats.put("universities", universities);

        return stats;
    }

    public synchronized void updateDiscordUsernames(List<java.util.Map<String, String>> updates) throws SQLException {
        String sql = "UPDATE discord_users SET discord_username = ? WHERE discord_id = ?;";
        connection.setAutoCommit(false);
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (java.util.Map<String, String> update : updates) {
                String discordId = update.get("discord_id");
                String username = update.get("discord_username");
                if (discordId != null && username != null) {
                    pstmt.setString(1, username);
                    pstmt.setString(2, discordId);
                    pstmt.addBatch();
                }
            }
            pstmt.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public static class OnlinePlayerDetail {
        public String minecraftId;
        public String discordId;
        public String universityRoleId;
        public String edition;

        public OnlinePlayerDetail(String minecraftId, String discordId, String universityRoleId, String edition) {
            this.minecraftId = minecraftId;
            this.discordId = discordId;
            this.universityRoleId = universityRoleId;
            this.edition = edition;
        }
    }

    public synchronized List<OnlinePlayerDetail> getOnlinePlayerDetails(List<String> onlineNames) throws SQLException {
        List<OnlinePlayerDetail> list = new ArrayList<>();
        if (onlineNames == null || onlineNames.isEmpty()) {
            return list;
        }

        String prefix = plugin.getBedrockPrefix();
        
        // DB問い合わせ用のクエリ名リストを作成（プレフィックスを除去したもの）
        List<String> queryNames = new ArrayList<>();
        // 元の名前からクエリ名（プレフィックス除去後）へのマッピング
        Map<String, String> rawToCleanMap = new HashMap<>(); 
        
        for (String name : onlineNames) {
            String cleanName = name;
            if (prefix != null && !prefix.isEmpty() && name.startsWith(prefix)) {
                cleanName = name.substring(prefix.length());
            }
            queryNames.add(cleanName);
            rawToCleanMap.put(name.toLowerCase(), cleanName.toLowerCase());
        }

        // 動的プレースホルダーの生成
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ma.minecraft_id, du.discord_id, u.discord_role_id, ma.edition ")
          .append("FROM minecraft_accounts ma ")
          .append("JOIN discord_users du ON ma.discord_user_id = du.id ")
          .append("JOIN universities u ON du.university_id = u.id ")
          .append("WHERE ma.is_active = 1 AND ma.minecraft_id IN (");
        
        for (int i = 0; i < queryNames.size(); i++) {
            sb.append("?");
            if (i < queryNames.size() - 1) {
                sb.append(",");
            }
        }
        sb.append(");");

        Map<String, OnlinePlayerDetail> foundPlayers = new HashMap<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sb.toString())) {
            for (int i = 0; i < queryNames.size(); i++) {
                pstmt.setString(i + 1, queryNames.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String mcId = rs.getString("minecraft_id");
                    foundPlayers.put(mcId.toLowerCase(), new OnlinePlayerDetail(
                            mcId,
                            rs.getString("discord_id"),
                            rs.getString("discord_role_id"),
                            rs.getString("edition")
                    ));
                }
            }
        }

        // 入力された元のすべてのオンラインプレイヤー名に対して、DBから見つかったデータまたは未連携データを作成
        for (String name : onlineNames) {
            String cleanName = name;
            String edition = "JAVA";
            if (prefix != null && !prefix.isEmpty() && name.startsWith(prefix)) {
                cleanName = name.substring(prefix.length());
                edition = "BEDROCK";
            }
            
            String cleanNameLower = rawToCleanMap.get(name.toLowerCase());
            OnlinePlayerDetail detail = foundPlayers.get(cleanNameLower);
            if (detail != null) {
                list.add(new OnlinePlayerDetail(
                        cleanName, // 接頭辞を除去した名前
                        detail.discordId,
                        detail.universityRoleId,
                        detail.edition // DBから引いた実際のエディション
                ));
            } else {
                list.add(new OnlinePlayerDetail(cleanName, null, null, edition));
            }
        }

        return list;
    }
}
