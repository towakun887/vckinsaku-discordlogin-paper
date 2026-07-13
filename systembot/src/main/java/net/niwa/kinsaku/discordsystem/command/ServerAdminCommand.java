package net.niwa.kinsaku.discordsystem.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.niwa.kinsaku.discordsystem.config.BotConfig;
import net.niwa.kinsaku.discordsystem.util.EmbedTemplates;

public class ServerAdminCommand extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("server-admin")) {
            return;
        }

        // ロールチェック
        String adminRoleId = BotConfig.getInstance().getAdminRoleId();
        boolean hasRole = event.getMember() != null && event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(adminRoleId));
        boolean isAdministrator = event.getMember() != null && event.getMember().hasPermission(Permission.ADMINISTRATOR);

        if (!hasRole && !isAdministrator) {
            event.reply("このコマンドを実行する権限がありません。管理者ロールが必要です。")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // 管理メニュー送信 (全体公開)
        event.replyEmbeds(EmbedTemplates.createAdminMenuEmbed())
                .setComponents(ActionRow.of(createAdminMenu()))
                .setEphemeral(false)
                .queue();
    }

    // 管理メニューのプルダウンメニューを作成
    public static StringSelectMenu createAdminMenu() {
        return StringSelectMenu.create("admin-menu")
                .setPlaceholder("実行する操作を選択してください...")
                .addOption("📋 登録プレイヤー一覧", "admin:list", "登録されている全プレイヤーの一覧を表示します。")
                .addOption("📊 登録統計", "admin:stats", "大学別・エディション別の人数統計を表示します。")
                .addOption("🔍 プレイヤー検索ヘルプ", "admin:search_help", "プレイヤー検索と個別アカウント情報の確認方法を表示します。")
                .addOption("🏫 大学一覧表示", "admin:uni_list", "登録大学および連携ロールIDの一覧を表示します。")
                .addOption("🏫 大学新規追加", "admin:uni_add_help", "新しい大学の登録手順を表示します。")
                .addOption("🔄 大学キャッシュリロード", "admin:uni_reload", "Plugin側の大学リストキャッシュを強制再ロードします。")
                .addOption("⚙️ ホワイトリスト設定（オン/オフ）", "admin:whitelist_toggle", "Minecraftサーバーのホワイトリスト機能をオン/オフします。")
                .build();
    }
}
