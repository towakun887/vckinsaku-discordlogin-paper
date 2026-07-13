package net.niwa.kinsaku.discordsystem.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient.PlayerAccount;
import net.niwa.kinsaku.discordsystem.util.EmbedTemplates;
import net.niwa.kinsaku.discordsystem.util.ReactionSelectHelper;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WhitelistDeleteCommand extends ListenerAdapter {

    private final PluginApiClient apiClient;

    public WhitelistDeleteCommand(PluginApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("server-whitelist-delete")) {
            return;
        }

        String discordId = event.getUser().getId();

        // 1. レスポンスの保留 (公開)
        event.deferReply(false).queue(hook -> {
            // 2. ユーザーに紐づくアカウント一覧を取得
            apiClient.getPlayerInfo(discordId).thenAccept(accounts -> {
                if (accounts.isEmpty()) {
                    hook.editOriginal("あなたに紐づいている Minecraft アカウントは見つかりませんでした。").queue();
                    return;
                }

                // 3. アカウント一覧Embedを送信
                hook.editOriginalEmbeds(EmbedTemplates.createAccountListEmbed(
                        accounts,
                        "あなたに紐づいている Minecraft アカウント一覧です。登録を解除するアカウントを選択してください。")).queue(message -> {
                            // 4. リアクション選択ヘルパーの起動
                            ReactionSelectHelper helper = new ReactionSelectHelper();
                            helper.select(message, event.getUser(), accounts.size(), false)
                                    .thenAccept(selectedIndices -> {
                                        if (selectedIndices.isEmpty()) {
                                            // 選択なし（タイムアウトなど）
                                            hook.editOriginal("削除がキャンセルされたか、操作タイムアウトしました。").queue();
                                            try {
                                                message.clearReactions().queue();
                                            } catch (Exception ignored) {
                                            }
                                            return;
                                        }

                                        // 削除処理
                                        try {
                                            message.clearReactions().queue();
                                        } catch (Exception ignored) {
                                        }
                                        deleteSelectedAccounts(hook, discordId, accounts, selectedIndices);
                                    });
                        });
            }).exceptionally(ex -> {
                hook.editOriginal("アカウント一覧の取得中にエラーが発生しました: " + ex.getMessage()).queue();
                return null;
            });
        });
    }

    private void deleteSelectedAccounts(net.dv8tion.jda.api.interactions.InteractionHook hook,
            String discordId, List<PlayerAccount> accounts, java.util.Set<Integer> selectedIndices) {
        // 非同期で選択されたアカウントをすべて削除
        CompletableFuture<?>[] futures = selectedIndices.stream()
                .map(index -> {
                    PlayerAccount acc = accounts.get(index);
                    return apiClient.removeAccount(acc.id);
                })
                .toArray(CompletableFuture<?>[]::new);

        CompletableFuture.allOf(futures).thenRun(() -> {
            boolean hasServerError = false;
            boolean anyFailed = false;
            for (CompletableFuture<?> f : futures) {
                try {
                    Object result = f.join();
                    if (result instanceof Integer) {
                        int status = (Integer) result;
                        if (status != 200) {
                            anyFailed = true;
                            if (status >= 500) {
                                hasServerError = true;
                            }
                        }
                    }
                } catch (Exception e) {
                    anyFailed = true;
                    hasServerError = true;
                }
            }

            if (anyFailed) {
                String adminRoleId = net.niwa.kinsaku.discordsystem.config.BotConfig.getInstance().getAdminRoleId();
                String mention = (adminRoleId != null && !adminRoleId.isEmpty() && hasServerError) ? "<@&" + adminRoleId + "> " : "";
                hook.editOriginal(mention + "⚠️ 削除処理エラー")
                    .setEmbeds(EmbedTemplates.createDeleteResultEmbed(
                        false,
                        "一部またはすべてのアカウント削除処理に失敗しました。時間をおいて再試行してください。"))
                    .queue();
            } else {
                hook.editOriginalEmbeds(EmbedTemplates.createDeleteResultEmbed(
                        true,
                        "選択した Minecraft アカウントの登録解除処理が完了しました。")).queue();
                
                // アカウントが0になったかチェックしてロールを外す
                net.niwa.kinsaku.discordsystem.util.WhitelistRoleHelper.checkAndRemoveRole(apiClient, hook.getJDA(), discordId, hook);
            }
        }).exceptionally(ex -> {
            String adminRoleId = net.niwa.kinsaku.discordsystem.config.BotConfig.getInstance().getAdminRoleId();
            String mention = (adminRoleId != null && !adminRoleId.isEmpty()) ? "<@&" + adminRoleId + "> " : "";
            hook.editOriginal(mention + "❌ ゲームサーバー連携エラー")
                .setEmbeds(EmbedTemplates.createDeleteResultEmbed(
                    false,
                    "一部またはすべてのアカウント削除処理に失敗しました。サーバーが起動していない可能性があります。\nエラー内容: " + ex.getMessage()))
                .queue();
            return null;
        });
    }
}
