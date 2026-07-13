package net.niwa.kinsaku.discordsystem.listener;

import net.niwa.kinsaku.discordsystem.KinsakuDiscordSystem;
import net.niwa.kinsaku.discordsystem.database.DatabaseManager;
import net.niwa.kinsaku.discordsystem.database.DatabaseManager.MinecraftAccount;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public class PlayerLoginListener implements Listener {

    private final KinsakuDiscordSystem plugin;
    private final DatabaseManager dbManager;

    public PlayerLoginListener(KinsakuDiscordSystem plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        String playerName = player.getName();

        try {
            // まずそのままの名前でアクティブな登録があるか確認
            MinecraftAccount account = dbManager.getAccountByMinecraftId(playerName);

            // 見つからない場合、Geyser/Floodgate接頭辞の対応
            if (account == null) {
                String prefix = plugin.getBedrockPrefix();
                if (prefix != null && !prefix.isEmpty() && playerName.startsWith(prefix)) {
                    String rawName = playerName.substring(prefix.length());
                    account = dbManager.getAccountByMinecraftId(rawName);
                }
            }

            if (account != null) {
                // 最終ログイン時間を更新 (非同期)
                final String finalMcId = account.minecraftId;
                CompletableFuture.runAsync(() -> {
                    try {
                        dbManager.updateLastLogin(finalMcId);
                    } catch (SQLException e) {
                        plugin.getLogger().warning("プレイヤーの最終ログイン更新中にエラーが発生しました: " + e.getMessage());
                    }
                });

                // 表示名の変更
                DatabaseManager.DiscordUser user = dbManager.getDiscordUserByDiscordId(account.discordId);
                if (user != null && user.discordUsername != null && !user.discordUsername.isEmpty()) {
                    String discordUsername = user.discordUsername;

                    // チャット名: Discord名 (Minecraft ID: グレー)
                    net.kyori.adventure.text.Component chatName = net.kyori.adventure.text.Component
                            .text(discordUsername)
                            .append(net.kyori.adventure.text.Component.text(" (" + playerName + ")")
                                    .color(net.kyori.adventure.text.format.NamedTextColor.GRAY));
                    player.displayName(chatName);

                    String displayPlayerName = playerName;
                    String prefix = plugin.getBedrockPrefix();
                    boolean isBedrock = false;
                    if (prefix != null && !prefix.isEmpty() && playerName.startsWith(prefix)) {
                        displayPlayerName = playerName.substring(prefix.length());
                        isBedrock = true;
                    } else if ("BEDROCK".equalsIgnoreCase(account.edition)) {
                        isBedrock = true;
                    }

                    net.kyori.adventure.text.Component editionBadge;
                    if (isBedrock) {
                        editionBadge = net.kyori.adventure.text.Component.text("(BE)")
                                .color(net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN);
                    } else {
                        editionBadge = net.kyori.adventure.text.Component.text("(JE)")
                                .color(net.kyori.adventure.text.format.NamedTextColor.DARK_RED);
                    }

                    // タブリスト表示名: Discord名 (Minecraft ID (JE/BE)) (装飾なし)
                    net.kyori.adventure.text.Component tabName = net.kyori.adventure.text.Component
                            .text(discordUsername + " (")
                            .append(net.kyori.adventure.text.Component.text(displayPlayerName))
                            .append(net.kyori.adventure.text.Component.text(" "))
                            .append(editionBadge)
                            .append(net.kyori.adventure.text.Component.text(")"));
                    player.playerListName(tabName);

                    // 参加メッセージ: Discord名 (Minecraft ID: グレー (JE/BE)) がサーバーに参加しました。
                    net.kyori.adventure.text.Component joinMsg = net.kyori.adventure.text.Component
                            .text(discordUsername)
                            .append(net.kyori.adventure.text.Component.text(" (").color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                            .append(net.kyori.adventure.text.Component.text(displayPlayerName).color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                            .append(net.kyori.adventure.text.Component.text(" ").color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                            .append(editionBadge)
                            .append(net.kyori.adventure.text.Component.text(")").color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                            .append(net.kyori.adventure.text.Component.text(" がサーバーに参加しました。"));
                    event.joinMessage(joinMsg);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("ログインイベント処理中にDBエラーが発生しました: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        try {
            MinecraftAccount account = dbManager.getAccountByMinecraftId(playerName);
            if (account == null) {
                String prefix = plugin.getBedrockPrefix();
                if (prefix != null && !prefix.isEmpty() && playerName.startsWith(prefix)) {
                    String rawName = playerName.substring(prefix.length());
                    account = dbManager.getAccountByMinecraftId(rawName);
                }
            }
            if (account != null) {
                DatabaseManager.DiscordUser user = dbManager.getDiscordUserByDiscordId(account.discordId);
                if (user != null && user.discordUsername != null && !user.discordUsername.isEmpty()) {
                    String displayPlayerName = playerName;
                    String prefix = plugin.getBedrockPrefix();
                    boolean isBedrock = false;
                    if (prefix != null && !prefix.isEmpty() && playerName.startsWith(prefix)) {
                        displayPlayerName = playerName.substring(prefix.length());
                        isBedrock = true;
                    } else if ("BEDROCK".equalsIgnoreCase(account.edition)) {
                        isBedrock = true;
                    }

                    net.kyori.adventure.text.Component editionBadge;
                    if (isBedrock) {
                        editionBadge = net.kyori.adventure.text.Component.text("(BE)")
                                .color(net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN);
                    } else {
                        editionBadge = net.kyori.adventure.text.Component.text("(JE)")
                                .color(net.kyori.adventure.text.format.NamedTextColor.DARK_RED);
                    }

                    net.kyori.adventure.text.Component quitMsg = net.kyori.adventure.text.Component
                            .text(user.discordUsername)
                            .append(net.kyori.adventure.text.Component.text(" (").color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                            .append(net.kyori.adventure.text.Component.text(displayPlayerName).color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                            .append(net.kyori.adventure.text.Component.text(" ").color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                            .append(editionBadge)
                            .append(net.kyori.adventure.text.Component.text(")").color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                            .append(net.kyori.adventure.text.Component.text(" がサーバーを退出しました。"));
                    event.quitMessage(quitMsg);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("退出イベント処理中にDBエラーが発生しました: " + e.getMessage());
        }
    }

    @EventHandler
    public void onProfileWhitelistVerify(com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent event) {
        if (event.isWhitelistEnabled()) {
            if (event.isOp()) {
                return;
            }

            String playerName = event.getPlayerProfile().getName();
            boolean isAllowed = false;

            if (playerName != null) {
                try {
                    // DBからJEプレイヤーとして登録されているか確認
                    MinecraftAccount account = dbManager.getAccountByMinecraftId(playerName);
                    if (account != null && "JAVA".equalsIgnoreCase(account.edition)) {
                        isAllowed = true;
                    }
                } catch (SQLException e) {
                    plugin.getLogger().warning("ホワイトリスト判定中にDBエラーが発生しました: " + e.getMessage());
                }
            }

            // 独自の許可判定に合致すれば無条件でホワイトリスト通過とする
            if (isAllowed) {
                event.setWhitelisted(true);
                return;
            }

            // 独自判定で許可されず、かつ既存のwhitelist.jsonにも無い場合（BE等）、または未登録の場合
            if (!event.isWhitelisted()) {
                String msg = plugin.getWhitelistKickMessage();
                if (msg != null && !msg.isEmpty()) {
                    event.kickMessage(net.kyori.adventure.text.Component.text(msg));
                }
            }
        }
    }
}
