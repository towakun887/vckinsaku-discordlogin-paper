package net.niwa.kinsaku.discordsystem.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;
import net.niwa.kinsaku.discordsystem.model.WhitelistRequest;
import net.niwa.kinsaku.discordsystem.util.EmbedTemplates;

public class WhitelistAddCommand extends ListenerAdapter {

    private final PluginApiClient apiClient;

    public WhitelistAddCommand(PluginApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("server-whitelist-add")) {
            return;
        }

        String university = event.getOption("university").getAsString();
        String edition = event.getOption("edition").getAsString().toUpperCase();
        String minecraftId = event.getOption("minecraft_id").getAsString().trim();
        String discordId = event.getUser().getId();

        // 1. まず公開メッセージで処理中Embedを送信 (公開かつ即時返答)
        event.replyEmbeds(EmbedTemplates.createPendingEmbed(university, edition, minecraftId))
                 .queue(hook -> {
                     // 2. 非同期でPlugin APIにホワイトリスト登録リクエストを送信
                     String discordUsername = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();
                     WhitelistRequest request = new WhitelistRequest(discordId, university, minecraftId, edition, discordUsername);

                     apiClient.addToWhitelist(request).thenAccept(response -> {
                         if (response.isSuccess()) {
                             // 成功時にホワイトリストロールを付与
                             String roleId = net.niwa.kinsaku.discordsystem.config.BotConfig.getInstance().getWhitelistedRoleId();
                             if (roleId != null && !roleId.isEmpty() && event.getGuild() != null && event.getMember() != null) {
                                 net.dv8tion.jda.api.entities.Role role = event.getGuild().getRoleById(roleId);
                                 if (role != null) {
                                     java.util.function.Consumer<Throwable> errorHandler = e -> {
                                         System.err.println("ロール付与に失敗しました: " + e.getMessage());
                                         String adminRoleId = net.niwa.kinsaku.discordsystem.config.BotConfig.getInstance().getAdminRoleId();
                                         String mention = (adminRoleId != null && !adminRoleId.isEmpty()) ? "<@&" + adminRoleId + "> " : "";
                                         hook.editOriginal(mention + "⚠️ Discord連携（ロール付与）に失敗しました。")
                                             .setEmbeds(EmbedTemplates.createResultEmbed(
                                                 false,
                                                 "⚠️ 部分的成功 (Discord連携エラー)",
                                                 "Minecraftサーバーへの登録は成功しましたが、Discordのロール「" + role.getName() + "」の付与に失敗しました。管理者に連絡してください。\nエラー内容: " + e.getMessage(),
                                                 false
                                             )).queue();
                                     };
                                     try {
                                         event.getGuild().addRoleToMember(event.getMember(), role).queue(
                                             v -> {
                                                 System.out.println("ホワイトリストロールを付与しました: " + event.getMember().getEffectiveName());
                                                 hook.editOriginalEmbeds(EmbedTemplates.createSuccessEmbed(
                                                         university, edition, minecraftId, response.getMinecraftUuid())).queue();
                                             },
                                             errorHandler
                                         );
                                     } catch (net.dv8tion.jda.api.exceptions.HierarchyException e) {
                                         errorHandler.accept(e);
                                     }
                                 } else {
                                     hook.editOriginalEmbeds(EmbedTemplates.createSuccessEmbed(
                                             university, edition, minecraftId, response.getMinecraftUuid())).queue();
                                 }
                             } else {
                                 hook.editOriginalEmbeds(EmbedTemplates.createSuccessEmbed(
                                         university, edition, minecraftId, response.getMinecraftUuid())).queue();
                             }
                         } else {
                             // 失敗Embedに更新
                             String errorMsg = response.getMessage() != null ? response.getMessage()
                                     : "原因不明のエラーが発生しました。";
                             boolean isUserError = errorMsg.startsWith("[ERR_INVALID_REQUEST]")
                                     || errorMsg.startsWith("[ERR_MISSING_FIELD]")
                                     || errorMsg.startsWith("[ERR_INVALID_EDITION]")
                                     || errorMsg.startsWith("[ERR_INVALID_MC_ID]")
                                     || errorMsg.startsWith("[ERR_UNIVERSITY_NOT_FOUND]")
                                     || errorMsg.startsWith("[ERR_PLAYER_DUPLICATE]")
                                     || errorMsg.startsWith("[ERR_MC_ID_DUPLICATE]")
                                     || errorMsg.startsWith("[ERR_MC_NOT_FOUND]");
                             String adminRoleId = net.niwa.kinsaku.discordsystem.config.BotConfig.getInstance().getAdminRoleId();
                             String mention = (adminRoleId != null && !adminRoleId.isEmpty() && !isUserError) ? "<@&" + adminRoleId + "> " : "";
                             hook.editOriginal(mention + "❌ 登録エラー")
                                 .setEmbeds(EmbedTemplates.createFailureEmbed(university, edition, minecraftId, errorMsg))
                                 .queue();
                         }
                     }).exceptionally(ex -> {
                         // 通信エラー時などのハンドリング
                         String adminRoleId = net.niwa.kinsaku.discordsystem.config.BotConfig.getInstance().getAdminRoleId();
                         String mention = (adminRoleId != null && !adminRoleId.isEmpty()) ? "<@&" + adminRoleId + "> " : "";
                         hook.editOriginal(mention + "❌ ゲームサーバー連携エラー")
                             .setEmbeds(EmbedTemplates.createFailureEmbed(
                                 university, edition, minecraftId, "APIサーバーへの接続に失敗しました。サーバーが起動していない可能性があります。\nエラー内容: " + ex.getMessage()))
                             .queue();
                         return null;
                     });
                 });
    }
}
