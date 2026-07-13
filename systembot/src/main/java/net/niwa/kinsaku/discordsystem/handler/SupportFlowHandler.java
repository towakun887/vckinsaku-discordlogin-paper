package net.niwa.kinsaku.discordsystem.handler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.niwa.kinsaku.discordsystem.command.ServerSupportCommand;
import net.niwa.kinsaku.discordsystem.util.CommandMentionHelper;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;

public class SupportFlowHandler extends ListenerAdapter {

    private final net.niwa.kinsaku.discordsystem.api.PluginApiClient apiClient;

    public SupportFlowHandler(net.niwa.kinsaku.discordsystem.api.PluginApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("support-menu")) {
            return;
        }

        String value = event.getValues().get(0);
        event.deferEdit().queue(hook -> {
            Message message = event.getMessage();
            MessageEmbed originalEmbed = message.getEmbeds().isEmpty() ? null : message.getEmbeds().get(0);
            
            if (originalEmbed == null) {
                hook.editOriginal("エラー: 元の案内が見つかりませんでした。").queue();
                return;
            }

            EmbedBuilder builder = new EmbedBuilder(originalEmbed);
            builder.clearFields();

            switch (value) {
                case "support:whitelist_add":
                    builder.setDescription("📥 **ホワイトリストの登録手順**\n\n" +
                            "当サーバーに参加するには、以下の手順で登録を行ってください。\n\n" +
                            "1️⃣ " + CommandMentionHelper.getMention("server-whitelist-add") + " コマンドを実行します。\n" +
                            "2️⃣ 以下の引数を指定して送信します：\n" +
                            "   • `university`: あなたの所属する大学を選択します。\n" +
                            "   • `edition`: `Java Edition` または `Bedrock Edition (統合版)` を選択します。\n" +
                            "   • `minecraft_id`: あなたの Minecraft アカウントの正確な ID を入力します。\n" +
                            "3️⃣ システムの検証が完了すると、自動的にホワイトリストに登録され、大学ロールが付与されます。");
                    break;

                case "support:whitelist_del":
                    builder.setDescription("🗑️ **ホワイトリストの登録解除手順**\n\n" +
                            "登録したアカウントの紐付けを解除したい場合は、以下の手順で行います。\n\n" +
                            "1️⃣ " + CommandMentionHelper.getMention("server-whitelist-delete") + " コマンドを実行します。\n" +
                            "2️⃣ 送信されたアカウント選択画面で、解除したいアカウントの番号リアクション（1️⃣、2️⃣など）を押します。\n" +
                            "3️⃣ 最後に ✅ リアクションを押すことで、削除処理が実行されます。");
                    break;

                case "support:server_info":
                    net.niwa.kinsaku.discordsystem.config.BotConfig botConfig = net.niwa.kinsaku.discordsystem.config.BotConfig.getInstance();
                    builder.setDescription("🎮 **サーバー接続情報 & アドレス**\n\n" +
                            "サーバーへ接続するためのアドレスおよびポート情報は以下の通りです。\n\n" +
                            "💻 **Java Edition**\n" +
                            "• アドレス: `" + botConfig.getJavaServerAddress() + "`\n\n" +
                            "📱 **Bedrock Edition (統合版 / スマホ・タブレット・Switch等)**\n" +
                            "• アドレス: `" + botConfig.getBedrockServerAddress() + "`\n" +
                            "• ポート: `" + botConfig.getBedrockServerPort() + "`\n\n" +
                            "*※接続には、事前に " + CommandMentionHelper.getMention("server-whitelist-add") + " でのID登録が必要です。*");
                    break;

                case "support:online_players":
                    builder.setDescription("🟢 **現在のオンラインプレイヤー一覧**\n\n" +
                            "オンラインプレイヤーの一覧は、 " + CommandMentionHelper.getMention("server-list") + " コマンドを使用して確認できます。");
                    break;

                case "support:university_list":
                    builder.setTitle("🏫 登録大学一覧");
                    apiClient.getUniversities().thenAccept(universities -> {
                        StringBuilder sb = new StringBuilder();
                        if (universities.isEmpty()) {
                            sb.append("現在登録されている大学はありません。");
                        } else {
                            for (PluginApiClient.UniversityInfo uni : universities) {
                                if (uni.discordRoleId != null && !uni.discordRoleId.isEmpty()) {
                                    sb.append("- <@&").append(uni.discordRoleId).append(">\n");
                                } else {
                                    sb.append("- ").append(uni.name).append("\n");
                                }
                            }
                        }
                        sb.append("\n大学の所属プレイヤーを検索するには、以下のコマンドを使用してください。\n")
                                .append("コマンド: ").append(CommandMentionHelper.getMention("university-search"));
                        builder.setDescription(sb.toString());

                        hook.editOriginalEmbeds(builder.build())
                                .setComponents(ActionRow.of(ServerSupportCommand.createSupportMenu()))
                                .queue();
                    }).exceptionally(ex -> {
                        builder.setDescription("大学一覧の取得に失敗しました: " + ex.getMessage());
                        hook.editOriginalEmbeds(builder.build())
                                .setComponents(ActionRow.of(ServerSupportCommand.createSupportMenu()))
                                .queue();
                        return null;
                    });
                    return; // async handler handles reply edit, return early

                case "support:player_search_guide":
                    builder.setTitle("🔍 プレイヤー・大学の検索手順");
                    builder.setDescription("以下のスラッシュコマンドを使用して、登録情報の検索や大学ごとのプレイヤー一覧を表示できます。\n\n" +
                            "**■ Discordユーザーから検索**\n" +
                            "コマンド: " + CommandMentionHelper.getMention("player-search-by-discord") + "\n" +
                            "指定したユーザーが登録しているMinecraftアカウント情報を確認できます。\n\n" +
                            "**■ Minecraft IDから検索**\n" +
                            "コマンド: " + CommandMentionHelper.getMention("player-search-by-minecraft") + "\n" +
                            "指定したMinecraft IDのプレイヤー情報や所属大学を確認できます。\n\n" +
                            "**■ 大学の所属プレイヤー検索**\n" +
                            "コマンド: " + CommandMentionHelper.getMention("university-search") + "\n" +
                            "指定した大学のロールに所属するメンバー一覧を表示します。");
                    break;

                default:
                    builder.setDescription("不明なアクションです。");
                    break;
            }

            hook.editOriginalEmbeds(builder.build())
                    .setComponents(ActionRow.of(ServerSupportCommand.createSupportMenu()))
                    .queue();
        });
    }
}
