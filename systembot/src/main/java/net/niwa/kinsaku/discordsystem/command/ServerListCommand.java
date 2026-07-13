package net.niwa.kinsaku.discordsystem.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;

public class ServerListCommand extends ListenerAdapter {

    private final PluginApiClient apiClient;

    public ServerListCommand(PluginApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("server-list")) {
            return;
        }

        event.deferReply(false).queue(hook -> {
            apiClient.getOnlinePlayersDetail().thenAccept(players -> {
                StringBuilder sb = new StringBuilder();
                if (players.isEmpty()) {
                    sb.append("現在、サーバーにログインしているプレイヤーはいません。");
                } else {
                    for (PluginApiClient.OnlinePlayerDetail player : players) {
                        sb.append("• ");
                        if (player.discordId != null && !player.discordId.isEmpty()) {
                            sb.append("<@").append(player.discordId).append("> ");
                        } else {
                            sb.append("（未連携） ");
                        }

                        if (player.universityRoleId != null && !player.universityRoleId.isEmpty()) {
                            sb.append("(<@&").append(player.universityRoleId).append(">) ");
                        }

                        sb.append("`").append(player.minecraftId).append("` ");
                        if ("BEDROCK".equalsIgnoreCase(player.edition)) {
                            sb.append("(BE)\n");
                        } else {
                            sb.append("(JE)\n");
                        }
                    }
                }

                EmbedBuilder builder = new EmbedBuilder();
                builder.setTitle("🟢 現在のオンラインプレイヤー一覧");
                builder.setDescription(sb.toString().trim());
                builder.setColor(0x00FF00);

                int onlinePlayerCount = players.size();
                long uniqueUserCount = players.stream()
                        .map(p -> p.discordId)
                        .filter(id -> id != null && !id.isEmpty())
                        .distinct()
                        .count();

                builder.addField("オンラインプレイヤー数", "**" + onlinePlayerCount + "** 人", true);
                builder.addField("ユニークユーザー数", "**" + uniqueUserCount + "** 人", true);

                hook.editOriginalEmbeds(builder.build()).queue();
            }).exceptionally(ex -> {
                hook.editOriginal("オンラインプレイヤー一覧の取得に失敗しました: " + ex.getMessage()).queue();
                return null;
            });
        });
    }
}
