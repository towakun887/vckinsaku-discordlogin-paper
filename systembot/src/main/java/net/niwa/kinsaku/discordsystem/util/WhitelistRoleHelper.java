package net.niwa.kinsaku.discordsystem.util;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;
import net.niwa.kinsaku.discordsystem.config.BotConfig;

public class WhitelistRoleHelper {

    public static void checkAndRemoveRole(PluginApiClient apiClient, JDA jda, String discordId, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        if (discordId == null || discordId.isEmpty()) {
            return;
        }
        apiClient.getPlayerInfo(discordId).thenAccept(accounts -> {
            if (accounts == null || accounts.isEmpty()) {
                String guildId = BotConfig.getInstance().getGuildId();
                String roleId = BotConfig.getInstance().getWhitelistedRoleId();
                if (guildId == null || guildId.isEmpty() || roleId == null || roleId.isEmpty()) {
                    return;
                }
                Guild guild = jda.getGuildById(guildId);
                if (guild != null) {
                    guild.retrieveMemberById(discordId).queue(member -> {
                        Role role = guild.getRoleById(roleId);
                        if (role != null && member.getRoles().contains(role)) {
                            java.util.function.Consumer<Throwable> errorHandler = e -> {
                                System.err.println("Failed to remove whitelist role: " + e.getMessage());
                                if (hook != null) {
                                    String adminRoleId = BotConfig.getInstance().getAdminRoleId();
                                    String mention = (adminRoleId != null && !adminRoleId.isEmpty()) ? "<@&" + adminRoleId + "> " : "";
                                    hook.editOriginal(mention + "⚠️ Discord連携（ロール剥奪）に失敗しました。")
                                        .setEmbeds(EmbedTemplates.createResultEmbed(
                                            false,
                                            "⚠️ 部分的成功 (Discord連携エラー)",
                                            "Minecraftサーバーの登録解除は成功しましたが、Discordのロール「" + role.getName() + "」の剥奪に失敗しました。管理者に連絡してください。\nエラー内容: " + e.getMessage(),
                                            false
                                        )).queue();
                                }
                            };
                            try {
                                guild.removeRoleFromMember(member, role).queue(
                                    v -> System.out.println("Removed whitelist role from " + member.getUser().getName() + " because they have 0 registered accounts."),
                                    errorHandler
                                );
                            } catch (net.dv8tion.jda.api.exceptions.HierarchyException e) {
                                errorHandler.accept(e);
                            }
                        }
                    }, error -> {
                        System.err.println("Failed to retrieve member for whitelist role removal check: " + error.getMessage());
                    });
                }
            }
        }).exceptionally(ex -> {
            System.err.println("Error while checking remaining accounts for role removal: " + ex.getMessage());
            if (hook != null) {
                String adminRoleId = BotConfig.getInstance().getAdminRoleId();
                String mention = (adminRoleId != null && !adminRoleId.isEmpty()) ? "<@&" + adminRoleId + "> " : "";
                hook.editOriginal(mention + "❌ ゲームサーバー連携エラー")
                    .setEmbeds(EmbedTemplates.createResultEmbed(
                        false,
                        "❌ ゲームサーバー連携エラー",
                        "ロール解除判定のためのアカウント一覧取得に失敗しました。サーバーが起動していない可能性があります。\nエラー内容: " + ex.getMessage(),
                        false
                    )).queue();
            }
            return null;
        });
    }
}
