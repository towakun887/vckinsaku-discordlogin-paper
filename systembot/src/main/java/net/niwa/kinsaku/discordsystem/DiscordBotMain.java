package net.niwa.kinsaku.discordsystem;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.niwa.kinsaku.discordsystem.api.PluginApiClient;
import net.niwa.kinsaku.discordsystem.command.*;
import net.niwa.kinsaku.discordsystem.config.BotConfig;
import net.niwa.kinsaku.discordsystem.handler.AdminFlowHandler;
import net.niwa.kinsaku.discordsystem.handler.SupportFlowHandler;
import net.niwa.kinsaku.discordsystem.util.CommandMentionHelper;
import java.util.List;

public class DiscordBotMain {
    private static final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        System.out.println("DiscordBotSystem を起動しています...");

        BotConfig config = BotConfig.getInstance();
        String token = config.getBotToken();

        if (token == null || token.isEmpty()) {
            System.err.println("エラー: 設定 'DISCORD_BOT_TOKEN' が指定されていません。");
            return;
        }

        try {
            PluginApiClient apiClient = new PluginApiClient();

            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGE_REACTIONS,
                            GatewayIntent.GUILD_MEMBERS)
                    .setActivity(Activity.playing("全国VTuberサークル金策サバイバル"))
                    .addEventListeners(
                            new ServerSupportCommand(),
                            new SupportFlowHandler(apiClient),
                            new WhitelistAddCommand(apiClient),
                            new WhitelistDeleteCommand(apiClient),
                            new ServerAdminCommand(),
                            new ServerUniversityCommand(apiClient),
                            new AdminDeleteCommand(apiClient),
                            new AdminFlowHandler(apiClient),
                            new UniversitySearchCommand(apiClient),
                            new PlayerSearchCommand(apiClient),
                            new ServerListCommand(apiClient),
                            new net.dv8tion.jda.api.hooks.ListenerAdapter() {
                                @Override
                                public void onGuildMemberUpdateNickname(
                                        net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent event) {
                                    String whitelistedRoleId = BotConfig.getInstance().getWhitelistedRoleId();
                                    Member member = event.getMember();
                                    boolean hasRole = member.getRoles().stream()
                                            .anyMatch(role -> role.getId().equals(whitelistedRoleId));
                                    if (hasRole) {
                                        java.util.Map<String, String> update = new java.util.HashMap<>();
                                        update.put("discord_id", member.getId());
                                        update.put("discord_username", member.getEffectiveName());
                                        apiClient.updateDiscordNames(List.of(update)).thenAccept(success -> {
                                            if (success) {
                                                System.out.println("メンバーのニックネーム変更を同期しました: " + member.getUser().getName()
                                                        + " -> " + member.getEffectiveName());
                                            }
                                        });
                                    }
                                }
                            },
                            new net.dv8tion.jda.api.hooks.ListenerAdapter() {
                                @Override
                                public void onCommandAutoCompleteInteraction(
                                        net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent event) {
                                    if (event.getFocusedOption().getName().equals("university")) {
                                        String value = event.getFocusedOption().getValue().toLowerCase();
                                        boolean isRemOrEdit = event.getName().equals("server-university")
                                                && ("rem".equals(event.getSubcommandName()) || "edit".equals(event.getSubcommandName()));

                                        java.util.List<net.dv8tion.jda.api.interactions.commands.Command.Choice> choices = apiClient
                                                .getCachedUniversities().stream()
                                                .filter(uni -> uni.name.toLowerCase().contains(value))
                                                .map(uni -> {
                                                    String choiceValue = isRemOrEdit ? String.valueOf(uni.id) : uni.name;
                                                    return new net.dv8tion.jda.api.interactions.commands.Command.Choice(
                                                            uni.name, choiceValue);
                                                })
                                                .limit(25)
                                                .collect(java.util.stream.Collectors.toList());

                                        event.replyChoices(choices).queue();
                                    }
                                }
                            })
                    .build();

            // ReadyEvent でコマンドを動的に登録するリスナー
            jda.addEventListener(new ListenerAdapter() {
                @Override
                public void onReady(ReadyEvent event) {
                    System.out.println("Discordボットの初期化が完了しました。コマンドを登録しています...");
                    registerCommands(jda, apiClient, config.getGuildId());
                    loadUniversityCacheWithRetry(apiClient);
                    syncDisplayNames(jda, apiClient, config.getGuildId());
                }
            });

            System.out.println("Botの起動シーケンスを開始しました。");
        } catch (Exception e) {
            System.err.println("Botの起動中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void loadUniversityCacheWithRetry(PluginApiClient apiClient) {
        apiClient.getUniversities().thenAccept(unis -> {
            System.out.println("初期大学キャッシュをロードしました。件数: " + unis.size());
        }).exceptionally(ex -> {
            System.err.println("初期大学キャッシュのロードに失敗しました。APIサーバーに接続できないため30秒後に再試行します: " + ex.getMessage());
            scheduler.schedule(() -> loadUniversityCacheWithRetry(apiClient), 30, java.util.concurrent.TimeUnit.SECONDS);
            return null;
        });
    }

    public static void registerCommands(JDA jda, PluginApiClient apiClient, String guildId) {
        // 大学選択オプションの作成 (オートコンプリートを有効化)
        OptionData uniOption = new OptionData(OptionType.STRING, "university", "所属大学を選択してください", true)
                .setAutoComplete(true);

        // エディション選択オプション
        OptionData editionOption = new OptionData(OptionType.STRING, "edition", "接続するMinecraftのエディション", true)
                .addChoice("Java Edition", "JAVA")
                .addChoice("Bedrock Edition (統合版)", "BEDROCK");

        // Whitelist IDオプション
        OptionData mcIdOption = new OptionData(OptionType.STRING, "minecraft_id", "Minecraft of IDを入力してください", true);

        // 一般ユーザー用コマンドデータ定義
        SlashCommandData supportCmd = Commands.slash("server-support", "サーバー案内Embedを表示します");

        SlashCommandData addCmd = Commands.slash("server-whitelist-add", "ホワイトリストへ新規追加します")
                .addOptions(uniOption, editionOption, mcIdOption);

        SlashCommandData deleteCmd = Commands.slash("server-whitelist-delete", "登録している自分のホワイトリストを解除します");

        // 管理者用コマンドデータ定義
        SlashCommandData adminCmd = Commands.slash("server-admin", "管理者用管理メニューを開きます")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));

        OptionData adminMcIdOpt = new OptionData(OptionType.STRING, "minecraft_id", "削除するMinecraft ID", false);
        OptionData adminUserOpt = new OptionData(OptionType.USER, "discord_user", "削除するDiscordユーザー", false);
        SlashCommandData adminDeleteCmd = Commands.slash("server-admin-delete", "指定したアカウントのホワイトリスト登録を解除します")
                .addOptions(adminMcIdOpt, adminUserOpt)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));

        // 大学管理用オプションの作成 (オートコンプリートを有効化)
        OptionData remUniOption = new OptionData(OptionType.STRING, "university", "削除する大学を選択してください", true)
                .setAutoComplete(true);

        // 大学管理用コマンド定義
        SlashCommandData universityCmd = Commands.slash("server-university", "大学情報を管理します（追加・削除・編集）")
                .addSubcommands(
                        new SubcommandData("add", "新規大学を追加します")
                                .addOption(OptionType.STRING, "name", "大学名", true)
                                .addOption(OptionType.ROLE, "role", "大学のDiscordロール", true)
                                .addOption(OptionType.STRING, "icon_url", "大学のアイコンURL (オプション)", false),
                        new SubcommandData("rem", "登録されている大学を削除します")
                                .addOptions(remUniOption),
                        new SubcommandData("edit", "登録されている大学の情報を編集します")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "university", "編集する大学を選択してください", true).setAutoComplete(true),
                                        new OptionData(OptionType.STRING, "field", "編集する項目", true)
                                                .addChoice("名前", "name")
                                                .addChoice("ロール", "role")
                                                .addChoice("アイコンURL", "icon_url"),
                                        new OptionData(OptionType.STRING, "new_value", "新しい値 (ロールの場合はメンションまたはID)", true)
                                ))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));

        // 大学検索コマンド
        SlashCommandData universitySearchCmd = Commands.slash("university-search", "指定した大学の情報を検索します")
                .addOption(OptionType.ROLE, "role", "検索する大学のロール", true);

        // プレイヤー検索コマンド
        SlashCommandData playerSearchByMinecraftCmd = Commands
                .slash("player-search-by-minecraft", "Minecraft IDからプレイヤーの登録状況を検索します")
                .addOption(OptionType.STRING, "id", "MinecraftのID", true);

        SlashCommandData playerSearchByDiscordCmd = Commands
                .slash("player-search-by-discord", "Discordユーザーからプレイヤーの登録状況を検索します")
                .addOption(OptionType.USER, "user", "Discordユーザー", true);

        SlashCommandData serverListCmd = Commands.slash("server-list", "現在のオンラインプレイヤー一覧を表示します");

        List<SlashCommandData> commandDataList = List.of(supportCmd, addCmd, deleteCmd, adminCmd, adminDeleteCmd,
                universityCmd, universitySearchCmd, playerSearchByMinecraftCmd, playerSearchByDiscordCmd, serverListCmd);

        if (guildId != null && !guildId.isEmpty()) {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                guild.updateCommands().addCommands(commandDataList).queue(
                        success -> {
                            System.out.println("ギルドコマンドを登録しました: " + guild.getName());
                            CommandMentionHelper.cacheMentions(success);
                        },
                        error -> System.err.println("ギルドコマンドの登録に失敗しました: " + error.getMessage()));
            } else {
                System.err.println("エラー: 指定されたギルドIDが見つかりません: " + guildId);
            }
        } else {
            System.out.println("警告: ギルドID未指定のため、グローバルコマンドとして登録します（反映に時間がかかる場合があります）。");
            jda.updateCommands().addCommands(commandDataList).queue(
                    success -> {
                        System.out.println("グローバルコマンドを登録しました。");
                        CommandMentionHelper.cacheMentions(success);
                    },
                    error -> System.err.println("グローバルコマンドの登録に失敗しました: " + error.getMessage()));
        }
    }

    private static void syncDisplayNames(JDA jda, PluginApiClient apiClient, String guildId) {
        if (guildId == null || guildId.isEmpty())
            return;
        Guild guild = jda.getGuildById(guildId);
        if (guild == null)
            return;

        System.out.println("データベースから登録メンバーを取得して、DisplayName同期を開始します...");
        apiClient.getPlayerList(null, null).thenAccept(players -> {
            java.util.Set<String> discordIds = new java.util.HashSet<>();
            for (PluginApiClient.PlayerAccount player : players) {
                if (player.discordId != null && !player.discordId.isEmpty()) {
                    discordIds.add(player.discordId);
                }
            }

            if (discordIds.isEmpty()) {
                System.out.println("同期対象のプレイヤーがデータベースに存在しません。");
                return;
            }

            System.out.println("同期対象のDiscord ID数: " + discordIds.size() + " 件。Discordから最新情報を取得中...");

            guild.retrieveMembersByIds(discordIds.toArray(new String[0])).onSuccess(members -> {
                java.util.List<java.util.Map<String, String>> nameUpdates = new java.util.ArrayList<>();
                for (Member member : members) {
                    java.util.Map<String, String> update = new java.util.HashMap<>();
                    update.put("discord_id", member.getId());
                    update.put("discord_username", member.getEffectiveName());
                    nameUpdates.add(update);
                }
                if (!nameUpdates.isEmpty()) {
                    apiClient.updateDiscordNames(nameUpdates).thenAccept(success -> {
                        System.out.println("DisplayNameの一括同期が完了しました。件数: " + nameUpdates.size() + ", 結果: " + success);
                    }).exceptionally(ex -> {
                        System.err.println("DisplayNameの一括同期API呼び出しでエラーが発生しました。30秒後に再試行します: " + ex.getMessage());
                        scheduler.schedule(() -> syncDisplayNames(jda, apiClient, guildId), 30, java.util.concurrent.TimeUnit.SECONDS);
                        return null;
                    });
                } else {
                    System.out.println("Discordから有効なメンバー情報を取得できませんでした（ギルドにいない可能性があります）。");
                }
            }).onError(error -> {
                System.err.println("Discordからのメンバー情報取得に失敗しました。30秒後に再試行します: " + error.getMessage());
                scheduler.schedule(() -> syncDisplayNames(jda, apiClient, guildId), 30, java.util.concurrent.TimeUnit.SECONDS);
            });
        }).exceptionally(ex -> {
            System.err.println("同期用のプレイヤー一覧取得に失敗しました。30秒後に再試行します: " + ex.getMessage());
            scheduler.schedule(() -> syncDisplayNames(jda, apiClient, guildId), 30, java.util.concurrent.TimeUnit.SECONDS);
            return null;
        });
    }
}
