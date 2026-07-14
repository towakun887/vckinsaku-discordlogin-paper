package net.niwa.kinsaku.discordsystem.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient.PlayerAccount;
import net.niwa.kinsaku.discordsystem.config.BotConfig;
import net.niwa.kinsaku.discordsystem.model.WhitelistResponse;
import net.niwa.kinsaku.discordsystem.util.EmbedTemplates;
import net.niwa.kinsaku.discordsystem.util.ReactionSelectHelper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.niwa.kinsaku.discordsystem.util.WhitelistRoleHelper;

public class AdminRemCommand extends ListenerAdapter {

    private final PluginApiClient apiClient;

    public AdminRemCommand(PluginApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("server-admin-rem")) {
            return;
        }

        // ロールチェック
        String adminRoleId = BotConfig.getInstance().getAdminRoleId();
        boolean hasRole = event.getMember() != null && event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(adminRoleId));
        boolean isAdministrator = event.getMember() != null
                && event.getMember().hasPermission(Permission.ADMINISTRATOR);

        if (!hasRole && !isAdministrator) {
            event.reply("このコマンドを実行する権限がありません。")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        var mcIdOpt = event.getOption("minecraft_id");
        var userOpt = event.getOption("discord_user");

        if (mcIdOpt == null && userOpt == null) {
            event.reply("エラー: minecraft_id または discord_user のどちらか一方を指定してください。")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply(false).queue(hook -> {
            if (mcIdOpt != null) {
                // Minecraft IDで指定された場合
                String mcId = mcIdOpt.getAsString().trim();
                apiClient.searchPlayer(mcId).thenAccept(accounts -> {
                    // 入力されたIDと厳密に一致するアカウントを検索
                    PlayerAccount target = null;
                    for (PlayerAccount acc : accounts) {
                        if (acc.minecraftId.equalsIgnoreCase(mcId)) {
                            target = acc;
                            break;
                        }
                    }

                    if (target == null) {
                        hook.editOriginal("指定された Minecraft ID (`" + mcId + "`) に紐づく有効なアカウントが見つかりませんでした。").queue();
                        return;
                    }

                    // 確認Embedとボタンを送信
                    PlayerAccount finalTarget = target;
                    hook.editOriginalEmbeds(EmbedTemplates.createRemConfirmEmbed(finalTarget, true))
                            .setComponents(ActionRow.of(
                                    Button.danger("admin:rem:confirm:" + finalTarget.id + ":"
                                            + finalTarget.minecraftId + ":" + finalTarget.discordId, "✅ 削除を実行"),
                                    Button.secondary("admin:rem:cancel:" + finalTarget.minecraftId + ":"
                                            + finalTarget.discordId, "❌ キャンセル")))
                            .queue();
                }).exceptionally(ex -> {
                    hook.editOriginal("エラーが発生しました: " + ex.getMessage()).queue();
                    return null;
                });
            } else {
                // Discord メンションで指定された場合
                User targetUser = userOpt.getAsUser();
                String discordId = targetUser.getId();

                apiClient.getPlayerInfo(discordId).thenAccept(accounts -> {
                    if (accounts.isEmpty()) {
                        hook.editOriginalEmbeds(EmbedTemplates.createResultEmbed(
                                false,
                                "❌ アカウント未検出",
                                "指定されたユーザー <@" + discordId + "> に紐づくアカウントはありませんでした。",
                                true)).queue();
                        return;
                    }

                    // リアクション選択による削除を実行
                    hook.editOriginalEmbeds(EmbedTemplates.createAccountListEmbed(
                            accounts,
                            "ユーザー <@" + discordId + "> に紐づくアカウント一覧です。削除するアカウントを選択してください。",
                            true)).queue(message -> {
                                ReactionSelectHelper helper = new ReactionSelectHelper();
                                // 🅰️ (全選択) オプションを有効化
                                helper.select(message, event.getUser(), accounts.size(), true)
                                        .thenAccept(selectedIndices -> {
                                            try {
                                                message.clearReactions().queue();
                                            } catch (Exception ignored) {
                                            }

                                            if (selectedIndices.isEmpty()) {
                                                hook.editOriginalEmbeds(EmbedTemplates.createRemResultEmbed(false,
                                                        "削除処理がキャンセルされたか、タイムアウトしました。\n対象: ユーザー <@" + discordId + ">",
                                                        true))
                                                        .setComponents().queue();
                                                return;
                                            }

                                            remSelectedAccounts(hook, discordId, accounts, selectedIndices);
                                        });
                            });
                }).exceptionally(ex -> {
                    hook.editOriginal("エラーが発生しました: " + ex.getMessage()).queue();
                    return null;
                });
            }
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith("admin:rem:")) {
            return;
        }

        // 実行者権限の再検証
        String adminRoleId = BotConfig.getInstance().getAdminRoleId();
        boolean hasRole = event.getMember() != null && event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(adminRoleId));
        boolean isAdministrator = event.getMember() != null
                && event.getMember().hasPermission(Permission.ADMINISTRATOR);

        if (!hasRole && !isAdministrator) {
            event.reply("あなたにはこの操作を実行する権限がありません。")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (buttonId.startsWith("admin:rem:cancel")) {
            // admin:rem:cancel:mcId:discordId の形式だが、mcIdにコロンが含まれる可能性に備え安全に分割
            String remainder = buttonId.substring("admin:rem:cancel:".length());
            int lastColon = remainder.lastIndexOf(':');
            String mcId = lastColon > 0 ? remainder.substring(0, lastColon) : remainder;
            String discordId = lastColon > 0 ? remainder.substring(lastColon + 1) : "";
            String targetStr = "`" + mcId + "`" + (!discordId.isEmpty() ? " (Discord: <@" + discordId + ">)" : "");

            event.deferEdit().queue(hook -> {
                hook.editOriginalEmbeds(
                        EmbedTemplates.createRemResultEmbed(false, "削除処理をキャンセルしました。\n対象: " + targetStr, true))
                        .setComponents()
                        .queue(message -> {
                            try {
                                message.clearReactions().queue();
                            } catch (Exception ignored) {
                            }
                        });
            });
            return;
        }

        if (buttonId.startsWith("admin:rem:confirm:")) {
            // admin:rem:confirm:accountId:mcId:discordId の形式で分割
            // accountIdは数値のみなのでコロンを含まない。mcIdの後ろのdiscordIdとの区切りは最後のコロン
            String remainder = buttonId.substring("admin:rem:confirm:".length());
            int firstColon = remainder.indexOf(':');
            int accountId = Integer.parseInt(remainder.substring(0, firstColon));
            String afterAccountId = remainder.substring(firstColon + 1);
            int lastColon = afterAccountId.lastIndexOf(':');
            String mcId = lastColon > 0 ? afterAccountId.substring(0, lastColon) : afterAccountId;
            String discordId = lastColon > 0 ? afterAccountId.substring(lastColon + 1) : "";
            String targetStr = "`" + mcId + "`" + (!discordId.isEmpty() ? " (Discord: <@" + discordId + ">)" : "");

            event.deferEdit().queue(hook -> {
                apiClient.removeAccount(accountId).thenAccept(resp -> {
                    if (resp.isSuccess()) {
                        hook.editOriginalEmbeds(EmbedTemplates.createRemResultEmbed(true,
                                "アカウントの登録解除とホワイトリストの抹消が完了しました。\n対象: " + targetStr,
                                true))
                                .setComponents()
                                .queue(message -> {
                                    try {
                                        message.clearReactions().queue();
                                    } catch (Exception ignored) {
                                    }
                                });
                        if (!discordId.isEmpty()) {
                            WhitelistRoleHelper.checkAndRemoveRole(apiClient,
                                    hook.getJDA(), discordId, hook);
                        }
                    } else {
                        String adminRoleIdLocal = BotConfig.getInstance().getAdminRoleId();
                        boolean isServerError = resp.getError() != null && resp.getError().startsWith("HTTP 5");
                        String mention = (adminRoleIdLocal != null && !adminRoleIdLocal.isEmpty() && isServerError)
                                ? "<@&" + adminRoleIdLocal + "> "
                                : "";
                        String errorMsg = (resp.getMessage() != null && !resp.getMessage().isEmpty()) ? "\nエラー内容: " + resp.getMessage() : "";
                        hook.editOriginal(mention + "❌ 登録削除エラー")
                                .setEmbeds(EmbedTemplates.createRemResultEmbed(false,
                                        "APIサーバーでの削除処理に失敗しました。\n対象: " + targetStr + errorMsg,
                                        true))
                                .setComponents()
                                .queue(message -> {
                                    try {
                                        message.clearReactions().queue();
                                    } catch (Exception ignored) {
                                    }
                                });
                    }
                }).exceptionally(ex -> {
                    String adminRoleIdLocal = BotConfig.getInstance().getAdminRoleId();
                    String mention = (adminRoleIdLocal != null && !adminRoleIdLocal.isEmpty())
                            ? "<@&" + adminRoleIdLocal + "> "
                            : "";
                    hook.editOriginal(mention + "❌ ゲームサーバー連携エラー")
                            .setEmbeds(EmbedTemplates.createRemResultEmbed(false,
                                    "通信エラーが発生しました: " + ex.getMessage() + "\n対象: " + targetStr,
                                    true))
                            .setComponents()
                            .queue(message -> {
                                try {
                                    message.clearReactions().queue();
                                } catch (Exception ignored) {
                                }
                            });
                    return null;
                });
            });
        }
    }

    private void remSelectedAccounts(net.dv8tion.jda.api.interactions.InteractionHook hook, String discordId,
            List<PlayerAccount> accounts, java.util.Set<Integer> selectedIndices) {
        CompletableFuture<?>[] futures = selectedIndices.stream()
                .map(index -> {
                    PlayerAccount acc = accounts.get(index);
                    return apiClient.removeAccount(acc.id);
                })
                .toArray(CompletableFuture<?>[]::new);

        CompletableFuture.allOf(futures).thenRun(() -> {
            boolean hasServerError = false;
            boolean anyFailed = false;
            java.util.List<String> errorMessages = new java.util.ArrayList<>();
            for (CompletableFuture<?> f : futures) {
                try {
                    Object result = f.join();
                    if (result instanceof WhitelistResponse) {
                        WhitelistResponse resp = (WhitelistResponse) result;
                        if (!resp.isSuccess()) {
                            anyFailed = true;
                            if (resp.getError() != null && resp.getError().startsWith("HTTP 5")) {
                                hasServerError = true;
                            }
                            String msg = resp.getMessage();
                            if (msg != null && !msg.isEmpty() && !errorMessages.contains(msg)) {
                                errorMessages.add(msg);
                            }
                        }
                    }
                } catch (Exception e) {
                    anyFailed = true;
                    hasServerError = true;
                    String msg = e.getMessage();
                    if (e.getCause() != null && e.getCause().getMessage() != null) {
                        msg = e.getCause().getMessage();
                    }
                    if (msg != null && !msg.isEmpty() && !errorMessages.contains(msg)) {
                        errorMessages.add(msg);
                    }
                }
            }

            if (anyFailed) {
                String adminRoleId = BotConfig.getInstance().getAdminRoleId();
                String mention = (adminRoleId != null && !adminRoleId.isEmpty() && hasServerError)
                        ? "<@&" + adminRoleId + "> "
                        : "";
                String errorMessageText = errorMessages.isEmpty() ? "" : "\nエラー内容: " + String.join(", ", errorMessages);
                hook.editOriginal(mention + "⚠️ 削除処理エラー")
                        .setEmbeds(EmbedTemplates.createRemResultEmbed(
                                false,
                                "一部またはすべてのアカウント削除処理に失敗しました。時間をおいて再試行してください。" + errorMessageText + "\n対象: ユーザー <@" + discordId + ">",
                                true))
                        .setComponents().queue();
            } else {
                StringBuilder sb = new StringBuilder(
                        "指定された全アカウントの登録解除とホワイトリスト解除処理が完了しました。\n対象: ユーザー <@" + discordId + ">");
                for (int index : selectedIndices) {
                    PlayerAccount acc = accounts.get(index);
                    sb.append("\n• `").append(acc.minecraftId).append("` (").append(acc.edition).append(")");
                }
                hook.editOriginalEmbeds(EmbedTemplates.createRemResultEmbed(
                        true,
                        sb.toString(),
                        true)).setComponents().queue();

                WhitelistRoleHelper.checkAndRemoveRole(apiClient, hook.getJDA(),
                        discordId, hook);
            }
        }).exceptionally(ex -> {
            StringBuilder sb = new StringBuilder(
                    "一部またはすべての削除処理に失敗しました: " + ex.getMessage() + "\n対象: ユーザー <@" + discordId + ">");
            for (int index : selectedIndices) {
                PlayerAccount acc = accounts.get(index);
                sb.append("\n• `").append(acc.minecraftId).append("` (").append(acc.edition).append(")");
            }
            String adminRoleId = BotConfig.getInstance().getAdminRoleId();
            String mention = (adminRoleId != null && !adminRoleId.isEmpty()) ? "<@&" + adminRoleId + "> " : "";
            hook.editOriginal(mention + "❌ ゲームサーバー連携エラー")
                    .setEmbeds(EmbedTemplates.createRemResultEmbed(
                            false,
                            sb.toString(),
                            true))
                    .setComponents().queue();
            return null;
        });
    }
}