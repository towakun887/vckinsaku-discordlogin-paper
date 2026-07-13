package net.niwa.kinsaku.discordsystem.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient.UniversityInfo;
import net.niwa.kinsaku.discordsystem.config.BotConfig;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class UniversityEditCommand extends ListenerAdapter {

    private final PluginApiClient apiClient;

    public UniversityEditCommand(PluginApiClient apiClient) {
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
        if (!event.getName().equals("server-university-edit")) {
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
            String field = event.getOption("field").getAsString();
            String newValue = event.getOption("new_value").getAsString().trim();
            handleEdit(hook, event, target, field, newValue);
        });
    }

    private void handleEdit(net.dv8tion.jda.api.interactions.InteractionHook hook, SlashCommandInteractionEvent event,
            String target, String field, String newValue) {
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

            String newName = found.name;
            String newRoleId = found.discordRoleId;
            String newIconUrl = found.iconUrl;

            if ("name".equals(field)) {
                newName = newValue;
            } else if ("icon_url".equals(field)) {
                newIconUrl = newValue;
            } else if ("role".equals(field)) {
                newRoleId = newValue.replaceAll("[^0-9]", "");
                if (newRoleId.isEmpty()) {
                    hook.editOriginal("❌ ロールIDが正しく認識できませんでした。").queue();
                    return;
                }
            }

            final String finalNewRoleId = newRoleId;
            final UniversityInfo finalFound = found;

            apiClient.updateUniversity(found.id, newName, newRoleId, newIconUrl).thenAccept(success -> {
                if (success) {
                    apiClient.refreshUniversityCache(); // Reload handled by backend internally

                    if ("role".equals(field) && !finalNewRoleId.equals(finalFound.discordRoleId)) {
                        hook.editOriginal("✅ 大学情報を更新しました。所属メンバーのロールを付け替えています...").queue();
                        updateRolesForUniversity(event.getGuild(), finalFound.name, finalFound.discordRoleId,
                                finalNewRoleId)
                                .thenAccept(v -> {
                                    hook.editOriginal("✅ 大学情報の更新と、所属メンバーへのロール再付与が完了しました！").queue();
                                });
                    } else {
                        hook.editOriginal("✅ 大学情報を更新しました！").queue();
                    }
                } else {
                    hook.editOriginal("❌ 大学情報の更新に失敗しました。名前が重複している可能性があります。").queue();
                }
            }).exceptionally(ex -> {
                hook.editOriginal("❌ エラーが発生しました: " + ex.getMessage()).queue();
                return null;
            });
        });
    }

    private CompletableFuture<Void> updateRolesForUniversity(net.dv8tion.jda.api.entities.Guild guild, String uniName,
            String oldRoleId, String newRoleId) {
        if (guild == null)
            return CompletableFuture.completedFuture(null);

        return apiClient.getPlayerList(uniName, null).thenCompose(players -> {
            Set<String> discordIds = players.stream()
                    .map(p -> p.discordId)
                    .filter(id -> id != null && !id.isEmpty())
                    .collect(Collectors.toSet());

            Role oldRole = oldRoleId != null ? guild.getRoleById(oldRoleId) : null;
            Role newRole = newRoleId != null ? guild.getRoleById(newRoleId) : null;

            if (newRole == null)
                return CompletableFuture.completedFuture(null); // skip if new role doesn't exist

            List<CompletableFuture<Void>> futures = discordIds.stream().map(discordId -> {
                CompletableFuture<Void> future = new CompletableFuture<>();
                guild.retrieveMemberById(discordId).queue(member -> {
                    if (oldRole != null && member.getRoles().contains(oldRole)) {
                        guild.removeRoleFromMember(member, oldRole).queue(v1 -> {
                            guild.addRoleToMember(member, newRole).queue(v2 -> future.complete(null),
                                    err -> future.complete(null));
                        }, err -> future.complete(null));
                    } else {
                        guild.addRoleToMember(member, newRole).queue(v -> future.complete(null),
                                err -> future.complete(null));
                    }
                }, err -> future.complete(null));
                return future;
            }).collect(Collectors.toList());

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        });
    }
}
