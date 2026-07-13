package net.niwa.kinsaku.discordsystem.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient.UniversityInfo;
import net.niwa.kinsaku.discordsystem.config.BotConfig;
import net.niwa.kinsaku.discordsystem.util.EmbedTemplates;

import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ServerUniversityCommand extends ListenerAdapter {

    private final PluginApiClient apiClient;

    public ServerUniversityCommand(PluginApiClient apiClient) {
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
        if (!event.getName().equals("server-university")) {
            return;
        }

        if (!checkPermission(event.getMember())) {
            event.reply("このコマンドを実行する権限がありません。管理者ロールが必要です。")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("サブコマンドを指定してください。").setEphemeral(true).queue();
            return;
        }

        event.deferReply(false).queue(hook -> {
            if (subcommand.equals("add")) {
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

            } else if (subcommand.equals("rem")) {
                String target = event.getOption("university").getAsString();
                handleRem(hook, target);

            } else if (subcommand.equals("edit")) {
                String target = event.getOption("university").getAsString();
                String field = event.getOption("field").getAsString();
                String newValue = event.getOption("new_value").getAsString().trim();
                handleEdit(hook, event, target, field, newValue);
            }
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
