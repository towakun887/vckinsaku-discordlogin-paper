package net.niwa.kinsaku.discordsystem.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient.UniversityInfo;
import net.niwa.kinsaku.discordsystem.util.EmbedTemplates;

import java.util.List;
import java.util.stream.Collectors;

public class UniversitySearchCommand extends ListenerAdapter {

    private final PluginApiClient apiClient;

    public UniversitySearchCommand(PluginApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String cmdName = event.getName();
        if (!cmdName.equals("university-search") && !cmdName.equals("admin-university-search")) {
            return;
        }

        boolean isAdmin = cmdName.equals("admin-university-search");
        Role role = event.getOption("role").getAsRole();
        String roleId = role.getId();

        event.deferReply(isAdmin).queue(hook -> {
            apiClient.getUniversities().thenAccept(universities -> {
                UniversityInfo foundUni = null;
                for (UniversityInfo uni : universities) {
                    if (roleId.equals(uni.discordRoleId)) {
                        foundUni = uni;
                        break;
                    }
                }

                if (foundUni == null) {
                    hook.editOriginalEmbeds(EmbedTemplates.createResultEmbed(
                            false,
                            "❌ 大学が見つかりません",
                            "指定されたロール (<@&" + roleId + ">) に紐づく大学が登録されていません。",
                            isAdmin)).queue();
                    return;
                }

                final UniversityInfo uniInfo = foundUni;

                apiClient.getPlayerList(uniInfo.name, null).thenAccept(players -> {
                    EmbedBuilder eb = EmbedTemplates.createBaseBuilder(isAdmin);

                    // Author
                    if (uniInfo.iconUrl != null && !uniInfo.iconUrl.isEmpty()) {
                        eb.setAuthor(uniInfo.name, null, uniInfo.iconUrl);
                    } else {
                        eb.setAuthor(uniInfo.name);
                    }

                    // Description: 1st line role mention, then players (if not admin)
                    StringBuilder desc = new StringBuilder();
                    desc.append("<@&").append(roleId).append(">\n\n");

                    if (players.isEmpty()) {
                        desc.append("登録されているプレイヤーはいません。");
                        eb.setDescription(desc.toString().trim());

                        long uniquePlayers = players.stream().map(p -> p.discordId).distinct().count();
                        eb.addField("合計人数", "**" + uniquePlayers + "** 人", true);

                        hook.editOriginalEmbeds(eb.build()).queue();
                    } else {
                        if (isAdmin) {
                            int itemsPerPage = 25;
                            int totalPages = (int) Math.ceil((double) players.size() / itemsPerPage);
                            int page = 1;
                            int startIndex = (page - 1) * itemsPerPage;
                            int endIndex = Math.min(startIndex + itemsPerPage, players.size());

                            for (int i = startIndex; i < endIndex; i++) {
                                PluginApiClient.PlayerAccount acc = players.get(i);
                                boolean isBe = "BEDROCK".equalsIgnoreCase(acc.edition);
                                String key = String.format("%s (%s)", acc.minecraftId, isBe ? "B" : "J");
                                String label = isBe ? "XUID" : "UUID";
                                String value = String.format("<@%s> %s\n%s: `%s`\n最終ログイン: %s",
                                        acc.discordId,
                                        PluginApiClient.getUniversityDisplay(acc.universityName),
                                        label,
                                        acc.minecraftUuid != null ? acc.minecraftUuid : "不明",
                                        acc.lastLoginAt != null ? acc.lastLoginAt : "未ログイン");
                                eb.addField(key, value, true);
                            }

                            eb.setDescription(desc.toString().trim());

                            long uniquePlayers = players.stream().map(p -> p.discordId).distinct().count();
                            eb.addField("合計人数", "**" + uniquePlayers + "** 人", true);

                            eb.setFooter("金策サバイバルシステム（管理者） | " + String.format("ページ %d/%d (合計 %d 件)", page, totalPages, players.size()), EmbedTemplates.KINSAKU_ICON);

                            Button prevBtn = Button.secondary("admin:uni_search:page:" + roleId + ":" + (page - 1), "◀️ 前のページ");
                            Button nextBtn = Button.secondary("admin:uni_search:page:" + roleId + ":" + (page + 1), "▶️ 次のページ");
                            Button updateBtn = Button.primary("admin:uni_search:page:" + roleId + ":" + page, "🔄 更新");

                            if (page <= 1) {
                                prevBtn = prevBtn.asDisabled();
                            }
                            if (page >= totalPages) {
                                nextBtn = nextBtn.asDisabled();
                            }

                            hook.editOriginalEmbeds(eb.build())
                                    .setComponents(ActionRow.of(prevBtn, updateBtn, nextBtn))
                                    .queue();
                        } else {
                            // Unique discord IDs to avoid mentioning same user multiple times if they have
                            // multiple accounts
                            List<String> discordIds = players.stream()
                                    .map(p -> p.discordId)
                                    .filter(id -> id != null && !id.isEmpty())
                                    .distinct()
                                    .collect(Collectors.toList());

                            for (String discordId : discordIds) {
                                desc.append("<@").append(discordId).append(">\n");
                            }

                            eb.setDescription(desc.toString().trim());

                            long uniquePlayers = players.stream().map(p -> p.discordId).distinct().count();
                            eb.addField("合計人数", "**" + uniquePlayers + "** 人", true);

                            hook.editOriginalEmbeds(eb.build()).queue();
                        }
                    }
                }).exceptionally(ex -> {
                    hook.editOriginalEmbeds(EmbedTemplates.createResultEmbed(
                            false,
                            "❌ エラー",
                            "プレイヤー情報の取得中にエラーが発生しました。\n" + ex.getMessage(),
                            isAdmin)).queue();
                    return null;
                });
            }).exceptionally(ex -> {
                hook.editOriginalEmbeds(EmbedTemplates.createResultEmbed(
                        false,
                        "❌ エラー",
                        "大学情報の取得中にエラーが発生しました。\n" + ex.getMessage(),
                        isAdmin)).queue();
                return null;
            });
        });
    }
}
