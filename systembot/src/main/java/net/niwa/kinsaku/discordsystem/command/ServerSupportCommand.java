package net.niwa.kinsaku.discordsystem.command;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.niwa.kinsaku.discordsystem.util.EmbedTemplates;

public class ServerSupportCommand extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("server-support")) {
            return;
        }

        event.replyEmbeds(EmbedTemplates.createSupportEmbed())
                .setComponents(ActionRow.of(createSupportMenu()))
                .setEphemeral(false)
                .queue();
    }

    public static StringSelectMenu createSupportMenu() {
        return StringSelectMenu.create("support-menu")
                .setPlaceholder("確認したい情報を選択してください...")
                .addOption("📥 ホワイトリスト登録手順", "support:whitelist_add", "サーバーに参加するための登録方法を表示します。")
                .addOption("🗑️ ホワイトリスト解除手順", "support:whitelist_del", "登録した自分のアカウントを解除する方法を表示します。")
                .addOption("🔍 プレイヤー・大学の検索手順", "support:player_search_guide", "登録状況の検索コマンドの使い方を表示します。")
                .addOption("🎮 接続方法 & サーバー情報", "support:server_info", "Java版/統合版の接続アドレスとポートを表示します。")
                .addOption("🟢 オンラインプレイヤー一覧", "support:online_players", "現在Minecraftサーバーにログインしているプレイヤー一覧を表示します。")
                .addOption("🏫 大学一覧", "support:university_list", "現在登録されている大学の一覧を表示します。")
                .build();
    }
}
