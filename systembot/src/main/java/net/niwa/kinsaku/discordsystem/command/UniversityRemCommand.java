package net.niwa.kinsaku.discordsystem.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient.UniversityInfo;
import net.niwa.kinsaku.discordsystem.config.BotConfig;

import java.awt.Color;

public class UniversityRemCommand extends ListenerAdapter {

    private final PluginApiClient apiClient;

    public UniversityRemCommand(PluginApiClient apiClient) {
        this.apiClient = apiClient;
    }

    private boolean checkPermission(Member member) {
        String adminRoleId = BotConfig.getInstance().getAdminRoleId();
        boolean hasRole = member != null && member.getRoles().stream()
                .anyMatch(role -> role.getId().equals(adminRoleId));
        boolean isAdministrator = member != null && member.hasPermission(Permission.ADMINISTRATOR);
        return hasRole || isAdministrator;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("server-university-rem")) {
            return;
        }

        if (!checkPermission(event.getMember())) {
            event.reply("このコマンドを実行する権限がありません。管理者ロールが必要です。")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply(false).queue(hook -> {
            String target = event.getOption("university").getAsString();
            handleRem(hook, target);
        });
    }

    private void handleRem(net.dv8tion.jda.api.interactions.InteractionHook hook, String target) {
        int uniId = -1;
        try {
            uniId = Integer.parseInt(target);
        } catch (NumberFormatException ignored) {
        }

        final int finalUniId = uniId;
        apiClient.getUniversities().thenAccept(unis -> {
            UniversityInfo found = null;
            for (UniversityInfo uni : unis) {
                if (uni.id == finalUniId || uni.name.equalsIgnoreCase(target)) {
                    found = uni;
                    break;
                }
            }
            if (found == null) {
                hook.editOriginal("❌ 指定された大学が見つかりませんでした。IDまたは選択肢から指定してください。").queue();
                return;
            }

            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("⚠️ 大学の削除確認");
            eb.setDescription("以下の大学を削除します。本当によろしいですか？\n削除すると復元できません。");
            eb.addField("大学名", found.name, true);
            eb.addField("大学ID", String.valueOf(found.id), true);
            eb.setColor(Color.RED);

            hook.editOriginalEmbeds(eb.build())
                    .setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(
                            Button.danger("admin:unidelete:confirm:" + found.id, "✅ 削除を実行"),
                            Button.secondary("admin:unidelete:cancel:" + found.id, "❌ キャンセル")))
                    .queue();
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("admin:unidelete:cancel:")) {
            if (!checkPermission(event.getMember())) {
                event.reply("権限がありません。").setEphemeral(true).queue();
                return;
            }
            event.editMessageEmbeds(new EmbedBuilder().setTitle("キャンセルしました").setColor(Color.GRAY).build())
                    .setComponents()
                    .queue();

        } else if (componentId.startsWith("admin:unidelete:confirm:")) {
            if (!checkPermission(event.getMember())) {
                event.reply("権限がありません。").setEphemeral(true).queue();
                return;
            }

            String idStr = componentId.substring("admin:unidelete:confirm:".length());
            int uniId;
            try {
                uniId = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                return;
            }

            event.deferEdit().queue(hook -> {
                apiClient.removeUniversity(uniId).thenAccept(success -> {
                    if (success) {
                        apiClient.refreshUniversityCache(); // Backend already reloaded
                        hook.editOriginalEmbeds(new EmbedBuilder().setTitle("✅ 削除完了")
                                .setDescription("大学(ID: " + uniId + ")を削除しました。").setColor(Color.GREEN).build())
                                .setComponents()
                                .queue();
                    } else {
                        hook.editOriginalEmbeds(new EmbedBuilder().setTitle("❌ 削除失敗")
                                .setDescription("大学の削除に失敗しました。APIサーバーでエラーが発生しました。").setColor(Color.RED).build())
                                .setComponents()
                                .queue();
                    }
                }).exceptionally(ex -> {
                    hook.editOriginalEmbeds(new EmbedBuilder().setTitle("❌ エラー").setDescription(ex.getMessage())
                            .setColor(Color.RED).build())
                            .setComponents()
                            .queue();
                    return null;
                });
            });
        }
    }
}
