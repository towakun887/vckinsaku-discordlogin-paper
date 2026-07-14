package net.niwa.kinsaku.discordsystem.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient.PlayerAccount;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient.UniversityInfo;
import net.niwa.kinsaku.discordsystem.util.EmbedTemplates;

import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.interactions.InteractionHook;

public class PlayerSearchCommand extends ListenerAdapter {

    private final PluginApiClient apiClient;

    public PlayerSearchCommand(PluginApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String cmdName = event.getName();
        if (!cmdName.equals("player-search-by-discord") && !cmdName.equals("player-search-by-minecraft") &&
            !cmdName.equals("admin-player-search-by-discord") && !cmdName.equals("admin-player-search-by-minecraft")) {
            return;
        }

        boolean isAdminSearch = cmdName.startsWith("admin-");

        event.deferReply(true).queue(hook -> {
            if (cmdName.endsWith("player-search-by-discord")) {
                User user = event.getOption("user").getAsUser();
                
                apiClient.getPlayerInfo(user.getId()).thenAccept(accounts -> {
                    sendPlayerInfoEmbed(hook, user, accounts, isAdminSearch);
                }).exceptionally(ex -> {
                    hook.editOriginalEmbeds(EmbedTemplates.createResultEmbed(
                            false, "❌ エラー", "情報の取得中にエラーが発生しました。\n" + ex.getMessage(), false
                    )).queue();
                    return null;
                });
                
            } else if (cmdName.endsWith("player-search-by-minecraft")) {
                String mcId = event.getOption("id").getAsString().trim();

                apiClient.searchPlayer(mcId).thenAccept(accounts -> {
                    // 入力値に完全一致するアカウントがあるか確認
                    boolean hasExactMatch = accounts != null && accounts.stream()
                            .anyMatch(a -> a.minecraftId != null && a.minecraftId.equalsIgnoreCase(mcId));

                    if (hasExactMatch) {
                        sendMinecraftPlayerInfoEmbed(hook, mcId, accounts, isAdminSearch);
                        return;
                    }

                    // 補正検索ロジック: Bedrock版でのスペースとドットの補正
                    String adjustedMcId = mcId;
                    boolean needsAdjustment = false;

                    if (adjustedMcId.contains(" ")) {
                        adjustedMcId = adjustedMcId.replace(" ", "_");
                        needsAdjustment = true;
                    }
                    if (!adjustedMcId.startsWith(".")) {
                        adjustedMcId = "." + adjustedMcId;
                        needsAdjustment = true;
                    }

                    if (needsAdjustment) {
                        final String finalAdjustedMcId = adjustedMcId;
                        apiClient.searchPlayer(finalAdjustedMcId).thenAccept(adjustedAccounts -> {
                            // 補正後のIDに完全一致するアカウントがあるか確認
                            boolean hasAdjustedExactMatch = adjustedAccounts != null && adjustedAccounts.stream()
                                    .anyMatch(a -> a.minecraftId != null && a.minecraftId.equalsIgnoreCase(finalAdjustedMcId));

                            if (hasAdjustedExactMatch) {
                                sendMinecraftPlayerInfoEmbed(hook, finalAdjustedMcId, adjustedAccounts, isAdminSearch);
                            } else {
                                // 補正後も完全一致しなければ見つからなかった扱いにする
                                sendMinecraftPlayerInfoEmbed(hook, finalAdjustedMcId, List.of(), isAdminSearch);
                            }
                        }).exceptionally(ex -> {
                            hook.editOriginalEmbeds(EmbedTemplates.createResultEmbed(
                                    false, "❌ エラー", "情報の取得中にエラーが発生しました。\n" + ex.getMessage(), false
                            )).queue();
                            return null;
                        });
                    } else {
                        sendMinecraftPlayerInfoEmbed(hook, mcId, List.of(), isAdminSearch);
                    }
                }).exceptionally(ex -> {
                    hook.editOriginalEmbeds(EmbedTemplates.createResultEmbed(
                            false, "❌ エラー", "情報の取得中にエラーが発生しました。\n" + ex.getMessage(), false
                    )).queue();
                    return null;
                });
            }
        });
    }

    private void sendPlayerInfoEmbed(InteractionHook hook, User user, List<PlayerAccount> accounts, boolean isAdminSearch) {
        EmbedBuilder eb = EmbedTemplates.createBaseBuilder(false);
        eb.setAuthor(user.getName(), null, user.getEffectiveAvatarUrl());

        if (accounts == null || accounts.isEmpty()) {
            eb.setDescription("指定されたユーザーはシステムに登録されていません。");
            hook.editOriginalEmbeds(eb.build()).queue();
            return;
        }

        // 全てのアカウントで大学ロールは同じはずなので、1つ目から取得
        String universityName = accounts.get(0).universityName;
        
        hook.getJDA().retrieveUserById(user.getId()).queue(u -> {}, e -> {}); // Dummy to check connection if needed
        apiClient.getUniversities().thenAccept(unis -> {
            String roleId = null;
            for (UniversityInfo uni : unis) {
                if (uni.name.equals(universityName)) {
                    roleId = uni.discordRoleId;
                    break;
                }
            }

            if (roleId != null) {
                eb.setDescription("所属: <@&" + roleId + ">");
            } else {
                eb.setDescription("所属: **" + universityName + "**");
            }

            for (PlayerAccount acc : accounts) {
                String status = acc.isActive ? "✅ 有効" : "❌ 無効";
                String fieldValue = "ID: `" + acc.minecraftId + "`\n状態: " + status;
                if (isAdminSearch) {
                    String label = "BEDROCK".equalsIgnoreCase(acc.edition) ? "XUID" : "UUID";
                    fieldValue += "\n" + label + ": `" + (acc.minecraftUuid != null ? acc.minecraftUuid : "不明") + "`";
                }
                eb.addField(
                        acc.edition.equals("JAVA") ? "☕ Java Edition" : "📱 Bedrock Edition",
                        fieldValue,
                        true
                );
            }

            hook.editOriginalEmbeds(eb.build()).queue();
        });
    }

    private void sendMinecraftPlayerInfoEmbed(InteractionHook hook, String queryId, List<PlayerAccount> foundAccounts, boolean isAdminSearch) {
        if (foundAccounts == null || foundAccounts.isEmpty()) {
            hook.editOriginalEmbeds(EmbedTemplates.createResultEmbed(
                    false,
                    "検索結果: " + queryId,
                    "指定されたMinecraft IDに一致するプレイヤーは見つかりませんでした。",
                    false
            )).queue();
            return;
        }

        // 検索キーワードに完全一致（大文字小文字無視）するアカウントのみを抽出
        List<PlayerAccount> exactMatches = foundAccounts.stream()
                .filter(a -> a.minecraftId != null && a.minecraftId.equalsIgnoreCase(queryId))
                .collect(Collectors.toList());

        if (exactMatches.isEmpty()) {
            hook.editOriginalEmbeds(EmbedTemplates.createResultEmbed(
                    false,
                    "検索結果: " + queryId,
                    "指定されたMinecraft IDに完全一致するプレイヤーは見つかりませんでした。",
                    false
            )).queue();
            return;
        }

        // ヒットしたアカウントに紐づく一意の discordId リストを抽出
        List<String> discordIds = exactMatches.stream()
                .map(a -> a.discordId)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (discordIds.isEmpty()) {
            EmbedBuilder eb = EmbedTemplates.createBaseBuilder(false);
            eb.setTitle("検索結果: " + queryId);
            for (PlayerAccount acc : exactMatches) {
                String status = acc.isActive ? "✅ 有効" : "❌ 無効";
                String fieldValue = "ID: `" + acc.minecraftId + "`\n状態: " + status;
                if (isAdminSearch) {
                    String label = "BEDROCK".equalsIgnoreCase(acc.edition) ? "XUID" : "UUID";
                    fieldValue += "\n" + label + ": `" + (acc.minecraftUuid != null ? acc.minecraftUuid : "不明") + "`";
                }
                eb.addField(
                        acc.edition.equals("JAVA") ? "☕ Java Edition" : "📱 Bedrock Edition",
                        fieldValue,
                        true
                );
            }
            hook.editOriginalEmbeds(eb.build()).queue();
            return;
        }

        // 各 discordId に紐づく全アカウント情報を取得
        List<CompletableFuture<List<PlayerAccount>>> futures = discordIds.stream()
                .map(apiClient::getPlayerInfo)
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenAccept(v -> {
            List<PlayerAccount> allAccounts = new java.util.ArrayList<>();
            java.util.Map<Integer, PlayerAccount> accountMap = new java.util.HashMap<>();
            for (CompletableFuture<List<PlayerAccount>> future : futures) {
                List<PlayerAccount> accs = future.join();
                for (PlayerAccount acc : accs) {
                    accountMap.put(acc.id, acc);
                }
            }
            allAccounts.addAll(accountMap.values());

            String representativeDiscordId = discordIds.get(0);
            EmbedBuilder eb = EmbedTemplates.createBaseBuilder(false);

            hook.getJDA().retrieveUserById(representativeDiscordId).queue(
                user -> {
                    eb.setAuthor(user.getName(), null, user.getEffectiveAvatarUrl());
                    buildMinecraftPlayerBody(hook, eb, allAccounts, isAdminSearch);
                },
                error -> {
                    eb.setAuthor("不明なユーザー (ID: " + representativeDiscordId + ")");
                    buildMinecraftPlayerBody(hook, eb, allAccounts, isAdminSearch);
                }
            );
        }).exceptionally(ex -> {
            hook.editOriginalEmbeds(EmbedTemplates.createResultEmbed(
                    false, "❌ エラー", "関連情報の取得中にエラーが発生しました。\n" + ex.getMessage(), false
            )).queue();
            return null;
        });
    }

    private void buildMinecraftPlayerBody(InteractionHook hook, EmbedBuilder eb, List<PlayerAccount> accounts, boolean isAdminSearch) {
        // 重複を除去しながら所属を表示
        List<String> uniNames = accounts.stream()
                .map(a -> a.universityName)
                .distinct()
                .collect(Collectors.toList());
                
        apiClient.getUniversities().thenAccept(unis -> {
            StringBuilder desc = new StringBuilder("所属: ");
            for (String uniName : uniNames) {
                String roleId = null;
                for (UniversityInfo uni : unis) {
                    if (uni.name.equals(uniName)) {
                        roleId = uni.discordRoleId;
                        break;
                    }
                }
                if (roleId != null) {
                    desc.append("<@&").append(roleId).append("> ");
                } else {
                    desc.append("**").append(uniName).append("** ");
                }
            }
            eb.setDescription(desc.toString().trim());

            for (PlayerAccount acc : accounts) {
                String status = acc.isActive ? "✅ 有効" : "❌ 無効";
                String fieldValue = "ID: `" + acc.minecraftId + "`\n状態: " + status;
                if (isAdminSearch) {
                    String label = "BEDROCK".equalsIgnoreCase(acc.edition) ? "XUID" : "UUID";
                    fieldValue += "\n" + label + ": `" + (acc.minecraftUuid != null ? acc.minecraftUuid : "不明") + "`";
                }
                eb.addField(
                        acc.edition.equals("JAVA") ? "☕ Java Edition" : "📱 Bedrock Edition",
                        fieldValue,
                        true
                );
            }
            hook.editOriginalEmbeds(eb.build()).queue();
        });
    }
}