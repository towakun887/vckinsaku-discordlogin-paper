package net.niwa.kinsaku.discordsystem.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient.PlayerAccount;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient.UniversityInfo;
import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class EmbedTemplates {

    // カラーパレット
    private static final Color COLOR_GREEN = new Color(87, 242, 135); // #57F287 (Green)
    private static final Color COLOR_RED = new Color(237, 66, 69); // #ED4245 (Red)
    private static final Color COLOR_YELLOW = new Color(254, 231, 92); // #FEE75C (Yellow)

    // icon
    public static final String KINSAKU_ICON = "https://cdn.discordapp.com/icons/1521474860306530314/87d9fc1be80ddf84b1db2eb3224758a9.webp";

    /**
     * 共通の基本EmbedBuilderを作成する（デフォルトカラーは黄色に設定）
     */
    public static EmbedBuilder createBaseBuilder(boolean isAdmin) {
        String footerText = isAdmin ? "金策サバイバルシステム（管理者）" : "金策サバイバルシステム";
        return new EmbedBuilder()
                .setFooter(footerText, KINSAKU_ICON)
                .setTimestamp(Instant.now())
                .setColor(COLOR_YELLOW);
    }

    /**
     * サーバーサポート案内Embed of standard user layout
     */
    public static MessageEmbed createSupportEmbed() {
        EmbedBuilder builder = createBaseBuilder(false)
                .setTitle("🎮 Minecraft サーバー サポートメニュー")
                .setDescription("サーバーへの接続やアカウントの紐付けを管理・検索するメニューです。以下のスラッシュコマンドを実行してください。")
                .addField("📥 新しくホワイトリストへ登録する",
                        "サーバーに参加するためにMinecraft IDを申請します。\n" +
                                "コマンド: " + CommandMentionHelper.getMention("server-whitelist-add"),
                        false)
                .addField("🗑️ 自分のホワイトリストを削除する",
                        "紐付けられているあなたのアカウントを解除します。\n" +
                                "コマンド: " + CommandMentionHelper.getMention("server-whitelist-rem"),
                        false)
                .addField("🔍 プレイヤー・大学を検索する",
                        "登録されているプレイヤーや大学の所属情報を検索できます。\n" +
                                "・Discordユーザーから検索: " + CommandMentionHelper.getMention("player-search-by-discord")
                                + "\n" +
                                "・Minecraft IDから検索: " + CommandMentionHelper.getMention("player-search-by-minecraft")
                                + "\n" +
                                "・大学の所属プレイヤー検索: " + CommandMentionHelper.getMention("university-search"),
                        false)
                .setThumbnail(
                        "https://raw.githubusercontent.com/GeyserMC/Geyser/master/geyser-spigot/src/main/resources/logo.png"); // Geyserロゴ等のプレースホルダー

        return builder.build();
    }

    /**
     * 処理中（Pending）の公開Embed
     */
    public static MessageEmbed createPendingEmbed(String university, String edition, String minecraftId) {
        return createBaseBuilder(false)
                .setTitle("⏳ ホワイトリスト申請 処理中...")
                .setDescription("申請内容を検証し、Minecraft サーバーへ反映しています。しばらくお待ちください。")
                .addField("🏫 申請大学", PluginApiClient.getUniversityDisplay(university), true)
                .addField("👾 エディション", edition, true)
                .addField("🆔 Minecraft ID", "`" + minecraftId + "`", true)
                .build();
    }

    /**
     * 申請成功の公開Embed
     */
    public static MessageEmbed createSuccessEmbed(String university, String edition, String minecraftId, String uuid) {
        return createBaseBuilder(false)
                .setTitle("✅ ホワイトリスト登録完了")
                .setDescription("Minecraft サーバーのホワイトリストへの追加が完了しました！\n" +
                        "ログイン可能になりました。ログイン時、最終ログイン時間が自動更新されます。")
                .setColor(COLOR_GREEN)
                .addField("🏫 申請大学", PluginApiClient.getUniversityDisplay(university), true)
                .addField("👾 エディション", edition, true)
                .addField("🆔 Minecraft ID", "`" + minecraftId + "`", true)
                .build();
    }

    /**
     * 申請失敗の公開Embed
     */
    public static MessageEmbed createFailureEmbed(String university, String edition, String minecraftId,
            String reason) {
        return createBaseBuilder(false)
                .setTitle("❌ ホワイトリスト登録失敗")
                .setDescription("申請を処理できませんでした。エラー詳細は以下の通りです。\n" +
                        "内容を確認の上、再度実行してください。")
                .setColor(COLOR_RED)
                .addField("🏫 申請大学", PluginApiClient.getUniversityDisplay(university), true)
                .addField("👾 エディション", edition, true)
                .addField("🆔 Minecraft ID", "`" + minecraftId + "`", true)
                .addField("⚠️ 失敗理由", "**" + reason + "**", false)
                .build();
    }

    /**
     * 自己アカウント一覧Embed (削除用)
     */
    public static MessageEmbed createAccountListEmbed(List<PlayerAccount> accounts, String titleDescription) {
        return createAccountListEmbed(accounts, titleDescription, false);
    }

    public static MessageEmbed createAccountListEmbed(List<PlayerAccount> accounts, String titleDescription,
            boolean isAdmin) {
        EmbedBuilder builder = createBaseBuilder(isAdmin)
                .setTitle("🗑️ ホワイトリスト アカウント選択");

        if (accounts.isEmpty()) {
            builder.setDescription("登録されているアカウントが見つかりません。");
        } else {
            builder.setDescription(titleDescription + "\n\n" +
                    "削除したいアカウントの番号リアクションを押して、最後に ✅ を押してください。");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < accounts.size(); i++) {
                PlayerAccount acc = accounts.get(i);
                sb.append(String.format("%d\uFE0F\u20E3 **%s** (%s) - 大学: %s\n",
                        (i + 1), acc.minecraftId, acc.edition,
                        PluginApiClient.getUniversityDisplay(acc.universityName)));
            }
            builder.addField("対象アカウント一覧", sb.toString(), false);
        }

        return builder.build();
    }

    /**
     * 削除確認Embed
     */
    public static MessageEmbed createRemConfirmEmbed(PlayerAccount account) {
        return createRemConfirmEmbed(account, false);
    }

    public static MessageEmbed createRemConfirmEmbed(PlayerAccount account, boolean isAdmin) {
        return createBaseBuilder(isAdmin)
                .setTitle("⚠️ アカウント削除の確認")
                .setDescription("以下のアカウントをホワイトリストから削除しますか？\n" +
                        "✅ で削除を実行、❌ でキャンセルします。")
                .addField("🆔 Minecraft ID", "`" + account.minecraftId + "`", true)
                .addField("👾 エディション", account.edition, true)
                .addField("🏫 大学", PluginApiClient.getUniversityDisplay(account.universityName), true)
                .addField("👤 所有者 (Discord ID)", "<@" + account.discordId + "> (" + account.discordId + ")", false)
                .build();
    }

    /**
     * 削除結果Embed
     */
    public static MessageEmbed createRemResultEmbed(boolean success, String message) {
        return createRemResultEmbed(success, message, false);
    }

    public static MessageEmbed createRemResultEmbed(boolean success, String message, boolean isAdmin) {
        return createBaseBuilder(isAdmin)
                .setTitle(success ? "✅ 削除完了" : "❌ 削除失敗")
                .setDescription(message)
                .setColor(success ? COLOR_GREEN : COLOR_RED)
                .build();
    }

    /**
     * 汎用的な結果通知Embed (成功/失敗など)
     */
    public static MessageEmbed createResultEmbed(boolean success, String title, String description) {
        return createResultEmbed(success, title, description, false);
    }

    public static MessageEmbed createResultEmbed(boolean success, String title, String description, boolean isAdmin) {
        return createBaseBuilder(isAdmin)
                .setTitle(title)
                .setDescription(description)
                .setColor(success ? COLOR_GREEN : COLOR_RED)
                .build();
    }

    /**
     * 管理者メニューEmbed
     */
    public static MessageEmbed createAdminMenuEmbed() {
        return createBaseBuilder(true)
                .setTitle("⚙️ Kinsaku Discord System 管理メニュー")
                .setDescription("サーバー管理用のコンソールメニューです。実行するメニューを選択してください。\n" +
                        "*(管理者専用機能)*")
                .build();
    }

    /**
     * プレイヤー一覧表示Embed (ページネーション対応)
     */
    public static MessageEmbed createPlayerListEmbed(List<PlayerAccount> players, int page, String universityFilter,
            String editionFilter) {
        EmbedBuilder builder = createBaseBuilder(true)
                .setTitle("📋 登録プレイヤー一覧");

        String description = "フィルタ: 大学=" + (universityFilter != null ? universityFilter : "すべて")
                + ", エディション=" + (editionFilter != null ? editionFilter : "すべて") + "\n\n";

        int itemsPerPage = 25;
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, players.size());

        if (players.isEmpty() || startIndex >= players.size()) {
            description += "*登録プレイヤーはいません。*";
            builder.setDescription(description);
        } else {
            builder.setDescription(description);
            for (int i = startIndex; i < endIndex; i++) {
                PlayerAccount acc = players.get(i);
                boolean isBe = "BEDROCK".equalsIgnoreCase(acc.edition);
                String key = String.format("%s (%s)", acc.minecraftId, isBe ? "B" : "J");
                String label = isBe ? "XUID" : "UUID";
                String value = String.format("<@%s> %s\n%s: `%s`\n最終ログイン: %s",
                        acc.discordId,
                        PluginApiClient.getUniversityDisplay(acc.universityName),
                        label,
                        acc.minecraftUuid != null ? acc.minecraftUuid : "不明",
                        acc.lastLoginAt != null ? acc.lastLoginAt : "未ログイン");
                builder.addField(key, value, true);
            }
            int totalPages = (int) Math.ceil((double) players.size() / itemsPerPage);
            String currentFooter = "金策サバイバルシステム（管理者）";
            builder.setFooter(
                    currentFooter + " | " + String.format("ページ %d/%d (合計 %d 件)", page, totalPages, players.size()),
                    KINSAKU_ICON);
        }

        return builder.build();
    }

    /**
     * 検索時のプレイヤー詳細Embed (紐づく全アカウント表示)
     */
    public static MessageEmbed createPlayerInfoEmbed(String targetDiscordId, List<PlayerAccount> accounts) {
        EmbedBuilder builder = createBaseBuilder(true)
                .setTitle("🔍 ユーザー検索結果");

        if (accounts.isEmpty()) {
            builder.setDescription("指定されたユーザー、またはMinecraft IDに紐づくデータが見つかりませんでした。");
        } else {
            PlayerAccount sample = accounts.get(0);
            builder.setDescription("Discord ユーザー: <@" + targetDiscordId + "> (" + targetDiscordId + ")\n" +
                    "所属大学: " + PluginApiClient.getUniversityDisplay(sample.universityName));

            StringBuilder sb = new StringBuilder();
            for (PlayerAccount acc : accounts) {
                sb.append(String.format("• **%s** (%s) | UUID: `%s` (最終ログイン: %s)\n",
                        acc.minecraftId, acc.edition, acc.minecraftUuid,
                        acc.lastLoginAt != null ? acc.lastLoginAt : "未ログイン"));
            }
            builder.addField("登録済みMinecraftアカウント", sb.toString(), false);
        }

        return builder.build();
    }

    /**
     * 統計情報Embed
     */
    public static MessageEmbed createStatisticsEmbed(Map<String, Object> stats) {
        EmbedBuilder builder = createBaseBuilder(true)
                .setTitle("📊 登録統計レポート");

        if (stats.isEmpty()) {
            builder.setDescription("統計データをロードできませんでした。");
            return builder.build();
        }

        int totalAccounts = ((Double) stats.get("total_accounts")).intValue();
        int totalUsers = ((Double) stats.get("total_users")).intValue();

        builder.addField("登録アカウント総数", "**" + totalAccounts + "** 件", true);
        builder.addField("紐づくユニークユーザー数", "**" + totalUsers + "** 人", true);

        // エディション別
        // 設計的にMapのキーはStringで、値はDoubleなのが確実
        Map<String, Double> editions = (Map<String, Double>) stats.get("editions");
        StringBuilder edSb = new StringBuilder();
        if (editions != null) {
            editions.forEach((ed, cnt) -> {
                int count = cnt.intValue();
                double percent = totalAccounts > 0 ? ((double) count / totalAccounts) * 100 : 0;
                edSb.append(String.format("• **%s:** %d 件 (%.1f%%)\n", ed, count, percent));
            });
        }
        builder.addField("👾 エディション別分布", edSb.length() > 0 ? edSb.toString() : "*データなし*", true);

        // 大学別
        Map<String, Double> universities = (Map<String, Double>) stats.get("universities");
        StringBuilder uniSb = new StringBuilder();
        if (universities != null) {
            universities.forEach((uni, cnt) -> {
                int count = cnt.intValue();
                double percent = totalAccounts > 0 ? ((double) count / totalAccounts) * 100 : 0;
                uniSb.append(String.format("• **%s:** %d 件 (%.1f%%)\n", uni, count, percent));
            });
        }
        builder.addField("🏫 大学別登録者", uniSb.length() > 0 ? uniSb.toString() : "*データなし*", true);

        return builder.build();
    }

    /**
     * 大学一覧管理Embed
     */
    public static MessageEmbed createUniversityListEmbed(List<UniversityInfo> universities) {
        EmbedBuilder builder = createBaseBuilder(true)
                .setTitle("🏫 登録大学一覧")
                .setDescription("現在システムに登録されている大学と紐づくDiscordロールです。");

        if (universities.isEmpty()) {
            builder.setDescription("*大学は登録されていません。*");
        } else {
            StringBuilder sb = new StringBuilder();
            for (UniversityInfo uni : universities) {
                String roleMention = uni.discordRoleId != null ? "<@&" + uni.discordRoleId + ">" : "*なし*";
                sb.append(String.format("• ID: `%d` | **%s** - ロール: %s (連携ロールID: `%s`)\n",
                        uni.id, uni.name, roleMention, uni.discordRoleId != null ? uni.discordRoleId : "なし"));
            }
            builder.addField("大学リスト", sb.toString(), false);
        }

        return builder.build();
    }
}
