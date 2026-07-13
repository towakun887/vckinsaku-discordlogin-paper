package net.niwa.kinsaku.discordsystem.database;

import net.niwa.kinsaku.discordsystem.KinsakuDiscordSystem;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UniversityCache {

    private final KinsakuDiscordSystem plugin;
    private final DatabaseManager databaseManager;
    private final List<DatabaseManager.University> cache = new CopyOnWriteArrayList<>();

    public UniversityCache(KinsakuDiscordSystem plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    // DBから大学リストをロードしてキャッシュを初期化
    public synchronized void load() {
        try {
            List<DatabaseManager.University> activeUniversities = databaseManager.getActiveUniversities();
            cache.clear();
            cache.addAll(activeUniversities);
            plugin.getLogger().info("大学リストのキャッシュをロードしました（件数: " + cache.size() + "）");
        } catch (SQLException e) {
            plugin.getLogger().severe("大学リストキャッシュのロード中にエラーが発生しました: " + e.getMessage());
        }
    }

    // キャッシュのリロード
    public void reload() {
        load();
    }

    // キャッシュされた大学リストを返す（読み取り専用）
    public List<DatabaseManager.University> getAll() {
        return Collections.unmodifiableList(cache);
    }

    // ID指定でキャッシュから取得
    public DatabaseManager.University getById(int id) {
        for (DatabaseManager.University uni : cache) {
            if (uni.id == id) {
                return uni;
            }
        }
        return null;
    }

    // 名前指定でキャッシュから取得
    public DatabaseManager.University getByName(String name) {
        for (DatabaseManager.University uni : cache) {
            if (uni.name.equalsIgnoreCase(name)) {
                return uni;
            }
        }
        return null;
    }
}
