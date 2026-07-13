package net.niwa.kinsaku.discordsystem.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;
import net.niwa.kinsaku.discordsystem.config.BotConfig;
import net.niwa.kinsaku.discordsystem.util.EmbedTemplates;

public class UniversityAddCommand extends ListenerAdapter {

    private final PluginApiClient apiClient;

    public UniversityAddCommand(PluginApiClient apiClient) {
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
        if (!event.getName().equals("server-university-add")) {
            return;
        }

        if (!checkPermission(event.getMember())) {
            event.reply("このコマンドを実行する権限がありません。管理者ロールが必要です。")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply(false).queue(hook -> {
            String name = event.getOption("name").getAsString().trim();
            Role role = event.getOption("role").getAsRole();
            String roleId = role.getId();
            String iconUrl = null;
            if (event.getOption("icon_url") != null) {
                iconUrl = event.getOption("icon_url").getAsString().trim();
            }

            apiClient.addUniversity(name, roleId, iconUrl).thenAccept(success -> {
                if (success) {
                    apiClient.refreshUniversityCache(); // reload is handled by backend internally
                    hook.editOriginalEmbeds(EmbedTemplates.createResultEmbed(
                            true,
                            "✅ 大学追加完了",
                            "大学 **" + name + "** を正常に追加し、連携ロール <@&" + roleId
                                    + "> を設定しました（Pluginのキャッシュも更新されました）。",
                            true)).queue();
                } else {
                    hook.editOriginal("❌ 大学の追加に失敗しました。APIサーバーでエラーが発生しました。").queue();
                }
            }).exceptionally(ex -> {
                hook.editOriginal("❌ エラーが発生しました: " + ex.getMessage()).queue();
                return null;
            });
        });
    }
}
