package net.niwa.kinsaku.discordsystem.command;

import net.dv8tion.jda.api.EmbedBuilder;
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
        if (!event.getName().equals("university-search")) {
            return;
        }

        Role role = event.getOption("role").getAsRole();
        String roleId = role.getId();

        event.deferReply(false).queue(hook -> {
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
                            false)).queue();
                    return;
                }

                final UniversityInfo uniInfo = foundUni;

                apiClient.getPlayerList(uniInfo.name, null).thenAccept(players -> {
                    EmbedBuilder eb = EmbedTemplates.createBaseBuilder(false);

                    // Author
                    if (uniInfo.iconUrl != null && !uniInfo.iconUrl.isEmpty()) {
                        eb.setAuthor(uniInfo.name, null, uniInfo.iconUrl);
                    } else {
                        eb.setAuthor(uniInfo.name);
                    }

                    // Description: 1st line role mention, then players
                    StringBuilder desc = new StringBuilder();
                    desc.append("<@&").append(roleId).append(">\n\n");

                    if (players.isEmpty()) {
                        desc.append("登録されているプレイヤーはいません。");
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
                    }
                    eb.setDescription(desc.toString().trim());

                    // Total players field
                    long uniquePlayers = players.stream().map(p -> p.discordId).distinct().count();
                    eb.addField("合計人数", "**" + uniquePlayers + "** 人", true);

                    hook.editOriginalEmbeds(eb.build()).queue();
                }).exceptionally(ex -> {
                    hook.editOriginalEmbeds(EmbedTemplates.createResultEmbed(
                            false,
                            "❌ エラー",
                            "プレイヤー情報の取得中にエラーが発生しました。\n" + ex.getMessage(),
                            false)).queue();
                    return null;
                });
            }).exceptionally(ex -> {
                hook.editOriginalEmbeds(EmbedTemplates.createResultEmbed(
                        false,
                        "❌ エラー",
                        "大学情報の取得中にエラーが発生しました。\n" + ex.getMessage(),
                        false)).queue();
                return null;
            });
        });
    }
}
