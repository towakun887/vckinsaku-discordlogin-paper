package net.niwa.kinsaku.discordsystem.handler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;
import net.niwa.kinsaku.discordsystem.command.ServerAdminCommand;
import net.niwa.kinsaku.discordsystem.config.BotConfig;
import net.niwa.kinsaku.discordsystem.util.CommandMentionHelper;
import net.niwa.kinsaku.discordsystem.util.EmbedTemplates;

public class AdminFlowHandler extends ListenerAdapter {

    private final PluginApiClient apiClient;

    public AdminFlowHandler(PluginApiClient apiClient) {
        this.apiClient = apiClient;
    }

    private MessageEmbed mergeEmbed(MessageEmbed original, MessageEmbed newEmbed) {
        if (original == null) {
            return newEmbed;
        }
        EmbedBuilder builder = new EmbedBuilder(original);
        builder.clearFields();

        String desc = newEmbed.getDescription();
        if (newEmbed.getFooter() != null && newEmbed.getFooter().getText() != null) {
            String footerText = newEmbed.getFooter().getText();
            if (!footerText.equals("管理者専用機能") && !footerText.contains("リアルタイム集計")) {
                desc = (desc != null ? desc : "") + "\n\n*💡 " + footerText + "*";
            }
        }
        builder.setDescription(desc);

        for (MessageEmbed.Field field : newEmbed.getFields()) {
            builder.addField(field);
        }
        return builder.build();
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("admin-menu")) {
            return;
        }

        // 実行者ロールの検証
        String adminRoleId = BotConfig.getInstance().getAdminRoleId();
        boolean hasRole = event.getMember() != null && event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(adminRoleId));
        boolean isAdministrator = event.getMember() != null
                && event.getMember().hasPermission(Permission.ADMINISTRATOR);

        if (!hasRole && !isAdministrator) {
            event.reply("この操作を実行する権限がありません。")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String value = event.getValues().get(0);
        event.deferEdit().queue(hook -> {
            Message message = event.getMessage();
            MessageEmbed originalEmbed = message.getEmbeds().isEmpty() ? null : message.getEmbeds().get(0);

            switch (value) {
                case "admin:list":
                    apiClient.getPlayerList(null, null).thenAccept(players -> {
                        MessageEmbed newEmbed = EmbedTemplates.createPlayerListEmbed(players, 1, null, null);
                        MessageEmbed merged = mergeEmbed(originalEmbed, newEmbed);
                        hook.editOriginalEmbeds(merged)
                                .setComponents(
                                        ActionRow.of(ServerAdminCommand.createAdminMenu()),
                                        ActionRow.of(
                                                Button.primary("admin:list:page:1", "🔄 更新"),
                                                Button.secondary("admin:list:page:2", "▶️ 次のページ")))
                                .queue();
                    }).exceptionally(ex -> {
                        hook.editOriginal("エラーが発生しました: " + ex.getMessage()).queue();
                        return null;
                    });
                    break;

                case "admin:stats":
                    apiClient.getStatistics().thenAccept(stats -> {
                        MessageEmbed newEmbed = EmbedTemplates.createStatisticsEmbed(stats);
                        MessageEmbed merged = mergeEmbed(originalEmbed, newEmbed);
                        hook.editOriginalEmbeds(merged)
                                .setComponents(ActionRow.of(ServerAdminCommand.createAdminMenu()))
                                .queue();
                    }).exceptionally(ex -> {
                        hook.editOriginal("統計データの取得に失敗しました: " + ex.getMessage()).queue();
                        return null;
                    });
                    break;

                case "admin:search_help":
                    String searchHelp = "🔍 **プレイヤー検索ヘルプ**\n\n" +
                            "特定のプレイヤー情報（紐づく全てのMinecraft IDとエディション）を検索・確認するには、管理者用-add/rem等のコマンドを使用するのが最も簡単です。\n" +
                            "以下のコマンドに確認したいプレイヤーの情報を入力して実行してください：\n" +
                            "• " + CommandMentionHelper.getMention("server-admin-rem")
                            + " `minecraft_id:<確認したいID>`\n" +
                            "• " + CommandMentionHelper.getMention("server-admin-rem")
                            + " `discord_user:<確認したいユーザーメンション>`\n\n" +
                            "コマンド実行後、確認画面が表示され、そこで対象のアカウント情報と紐付け状態を確認できます。\n" +
                            "確認だけが目的の場合は、削除を実行せずに **[❌ キャンセル]** ボタンを押せば、アカウントは削除されません。";

                    EmbedBuilder searchBuilder = new EmbedBuilder(originalEmbed);
                    searchBuilder.clearFields();
                    searchBuilder.setDescription(searchHelp);

                    hook.editOriginalEmbeds(searchBuilder.build())
                            .setComponents(ActionRow.of(ServerAdminCommand.createAdminMenu()))
                            .queue();
                    break;

                case "admin:uni_list":
                    apiClient.getUniversities().thenAccept(unis -> {
                        MessageEmbed newEmbed = EmbedTemplates.createUniversityListEmbed(unis);
                        MessageEmbed merged = mergeEmbed(originalEmbed, newEmbed);
                        hook.editOriginalEmbeds(merged)
                                .setComponents(ActionRow.of(ServerAdminCommand.createAdminMenu()))
                                .queue();
                    }).exceptionally(ex -> {
                        hook.editOriginal("大学一覧の取得に失敗しました: " + ex.getMessage()).queue();
                        return null;
                    });
                    break;

                case "admin:uni_add_help":
                    String addHelp = "🏫 **新規大学追加手順**\n\n" +
                            "現在、新しい大学を登録するには、以下の方法で行えます：\n\n" +
                            "**スラッシュコマンド**\n" +
                            "• " + CommandMentionHelper.getMention("server-university-add")
                            + " コマンドを使用して、Discord 上から直接追加します。\n" +
                            "• " + CommandMentionHelper.getMention("server-university-rem")
                            + " コマンドを使用して、Discord 上から削除します。\n" +
                            "• " + CommandMentionHelper.getMention("server-university-edit")
                            + " コマンドを使用して、既存の大学情報 (名前、ロール、アイコンURL) を編集します。ロールの編集時は所属メンバーのDiscordロールも自動で更新されます。\n\n" +
                            "追加後、メニューからキャッシュのリロードを実行することで Bot 側の選択肢に即座に反映されます。";

                    EmbedBuilder addBuilder = new EmbedBuilder(originalEmbed);
                    addBuilder.clearFields();
                    addBuilder.setDescription(addHelp);

                    hook.editOriginalEmbeds(addBuilder.build())
                            .setComponents(ActionRow.of(ServerAdminCommand.createAdminMenu()))
                            .queue();
                    break;

                case "admin:uni_reload":
                    EmbedBuilder reloadBuilder = new EmbedBuilder(originalEmbed);
                    reloadBuilder.clearFields();
                    reloadBuilder.setDescription("🔄 **大学キャッシュのリロード**\n\n" +
                            "Plugin 側の大学キャッシュをリロードします。よろしいですか？\n" +
                            "リロードを実行すると、Bot側の選択肢に新しい大学情報が即座に同期されます。");

                    hook.editOriginalEmbeds(reloadBuilder.build())
                            .setComponents(
                                    ActionRow.of(ServerAdminCommand.createAdminMenu()),
                                    ActionRow.of(Button.primary("admin:uni_reload:execute", "🔄 キャッシュリロードを実行")))
                            .queue();
                    break;

                case "admin:whitelist_toggle":
                    apiClient.isWhitelistEnabled().thenAccept(enabled -> {
                        EmbedBuilder wlBuilder = new EmbedBuilder(originalEmbed);
                        wlBuilder.clearFields();
                        wlBuilder.setDescription("⚙️ **ホワイトリスト機能の設定**\n\n" +
                                "現在の設定状態: " + (enabled ? "🟢 **有効 (ON)**" : "🔴 **無効 (OFF)**") + "\n\n" +
                                "下のボタンから、ホワイトリスト機能の有効化・無効化を切り替えることができます。");

                        hook.editOriginalEmbeds(wlBuilder.build())
                                .setComponents(
                                        ActionRow.of(ServerAdminCommand.createAdminMenu()),
                                        ActionRow.of(
                                                Button.success("admin:whitelist:on", "🟢 有効 (ON) にする"),
                                                Button.danger("admin:whitelist:off", "🔴 無効 (OFF) にする")))
                                .queue();
                    }).exceptionally(ex -> {
                        hook.editOriginal("ホワイトリスト状態の取得に失敗しました: " + ex.getMessage()).queue();
                        return null;
                    });
                    break;

                default:
                    hook.editOriginal("不明なアクションです。").queue();
                    break;
            }
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        // ホワイトリスト設定変更ボタンの処理
        if (buttonId.equals("admin:whitelist:on") || buttonId.equals("admin:whitelist:off")) {
            String adminRoleId = BotConfig.getInstance().getAdminRoleId();
            boolean hasRole = event.getMember() != null && event.getMember().getRoles().stream()
                    .anyMatch(role -> role.getId().equals(adminRoleId));
            boolean isAdministrator = event.getMember() != null
                    && event.getMember().hasPermission(Permission.ADMINISTRATOR);

            if (!hasRole && !isAdministrator) {
                event.reply("この操作を実行する権限がありません。")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            boolean enable = buttonId.equals("admin:whitelist:on");
            event.deferEdit().queue(hook -> {
                apiClient.setWhitelistEnabled(enable).thenAccept(success -> {
                    MessageEmbed originalEmbed = event.getMessage().getEmbeds().get(0);
                    EmbedBuilder builder = new EmbedBuilder(originalEmbed);
                    builder.clearFields();
                    if (success) {
                        builder.setDescription("⚙️ **ホワイトリスト機能の設定**\n\n" +
                                "ホワイトリスト機能を " + (enable ? "🟢 **有効 (ON)**" : "🔴 **無効 (OFF)**") + " に変更しました。");
                    } else {
                        builder.setDescription("ホワイトリスト設定の変更に失敗しました。");
                    }
                    hook.editOriginalEmbeds(builder.build())
                            .setComponents(ActionRow.of(ServerAdminCommand.createAdminMenu()))
                            .queue();
                }).exceptionally(ex -> {
                    MessageEmbed originalEmbed = event.getMessage().getEmbeds().get(0);
                    EmbedBuilder builder = new EmbedBuilder(originalEmbed);
                    builder.clearFields();
                    builder.setDescription("エラーが発生しました: " + ex.getMessage());
                    hook.editOriginalEmbeds(builder.build())
                            .setComponents(ActionRow.of(ServerAdminCommand.createAdminMenu()))
                            .queue();
                    return null;
                });
            });
            return;
        }

        // キャッシュリロードボタンの処理
        if (buttonId.equals("admin:uni_reload:execute")) {
            String adminRoleId = BotConfig.getInstance().getAdminRoleId();
            boolean hasRole = event.getMember() != null && event.getMember().getRoles().stream()
                    .anyMatch(role -> role.getId().equals(adminRoleId));
            boolean isAdministrator = event.getMember() != null
                    && event.getMember().hasPermission(Permission.ADMINISTRATOR);

            if (!hasRole && !isAdministrator) {
                event.reply("この操作を実行する権限がありません。")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            event.deferEdit().queue(hook -> {
                apiClient.reloadUniversities().thenAccept(success -> {
                    MessageEmbed originalEmbed = event.getMessage().getEmbeds().get(0);
                    EmbedBuilder builder = new EmbedBuilder(originalEmbed);
                    builder.clearFields();
                    if (success) {
                        builder.setDescription("Plugin 側の大学キャッシュを正常にリロードしました。Bot側の選択肢に新しい大学情報が読み込まれます。");
                        apiClient.refreshUniversityCache();
                    } else {
                        builder.setDescription("キャッシュのリロードに失敗しました。");
                    }
                    hook.editOriginalEmbeds(builder.build())
                            .setComponents(ActionRow.of(ServerAdminCommand.createAdminMenu()))
                            .queue();
                }).exceptionally(ex -> {
                    MessageEmbed originalEmbed = event.getMessage().getEmbeds().get(0);
                    EmbedBuilder builder = new EmbedBuilder(originalEmbed);
                    builder.clearFields();
                    builder.setDescription("エラーが発生しました: " + ex.getMessage());
                    hook.editOriginalEmbeds(builder.build())
                            .setComponents(ActionRow.of(ServerAdminCommand.createAdminMenu()))
                            .queue();
                    return null;
                });
            });
            return;
        }

        if (!buttonId.startsWith("admin:list:page:")) {
            return;
        }

        // 管理者権限の再確認
        String adminRoleId = BotConfig.getInstance().getAdminRoleId();
        boolean hasRole = event.getMember() != null && event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(adminRoleId));
        boolean isAdministrator = event.getMember() != null
                && event.getMember().hasPermission(Permission.ADMINISTRATOR);

        if (!hasRole && !isAdministrator) {
            event.reply("この操作を実行する権限がありません。")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        int targetPage = Integer.parseInt(buttonId.substring("admin:list:page:".length()));
        event.deferEdit().queue(hook -> {
            apiClient.getPlayerList(null, null).thenAccept(players -> {
                int itemsPerPage = 10;
                int maxPages = (int) Math.ceil((double) players.size() / itemsPerPage);
                int page = Math.min(Math.max(targetPage, 1), Math.max(maxPages, 1));

                Button prevBtn = Button.secondary("admin:list:page:" + (page - 1), "◀️ 前のページ");
                Button nextBtn = Button.secondary("admin:list:page:" + (page + 1), "▶️ 次のページ");
                Button updateBtn = Button.primary("admin:list:page:" + page, "🔄 更新");

                if (page <= 1) {
                    prevBtn = prevBtn.asDisabled();
                }
                if (page >= maxPages) {
                    nextBtn = nextBtn.asDisabled();
                }

                MessageEmbed originalEmbed = event.getMessage().getEmbeds().get(0);
                MessageEmbed newEmbed = EmbedTemplates.createPlayerListEmbed(players, page, null, null);
                MessageEmbed merged = mergeEmbed(originalEmbed, newEmbed);

                hook.editOriginalEmbeds(merged)
                        .setComponents(
                                ActionRow.of(ServerAdminCommand.createAdminMenu()),
                                ActionRow.of(prevBtn, updateBtn, nextBtn))
                        .queue();
            }).exceptionally(ex -> {
                hook.editOriginal("更新中にエラーが発生しました: " + ex.getMessage()).queue();
                return null;
            });
        });
    }
}
