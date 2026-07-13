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
                        plugin.getLogger().warning("最終ログイン更新中にエラーが発生しました: " + e.getMessage());
                    }
                });
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("ログインイベント処理中にDBエラーが発生しました: " + e.getMessage());
        }

        net.kyori.adventure.text.Component customName = getCustomName(player);
        if (customName != null) {
            player.displayName(customName);
            player.playerListName(customName);

            net.kyori.adventure.text.Component joinMsg = customName
                    .append(net.kyori.adventure.text.Component.text(" がサーバーに参加しました。"));
            event.joinMessage(joinMsg);
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        net.kyori.adventure.text.Component customName = getCustomName(player);
        if (customName != null) {
            net.kyori.adventure.text.Component quitMsg = customName
                    .append(net.kyori.adventure.text.Component.text(" がサーバーを退出しました。"));
            event.quitMessage(quitMsg);
        }
    }

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        org.bukkit.entity.Player player = event.getEntity();
        net.kyori.adventure.text.Component customName = getCustomName(player);
        net.kyori.adventure.text.Component deathMsg = event.deathMessage();
        if (deathMsg != null) {
            net.kyori.adventure.text.Component updatedMsg = deathMsg;
            if (customName != null) {
                updatedMsg = replacePlayerNameSafely(updatedMsg, player, customName);
            }
            org.bukkit.entity.Player killer = player.getKiller();
            if (killer != null) {
                net.kyori.adventure.text.Component killerCustomName = getCustomName(killer);
                if (killerCustomName != null) {
                    updatedMsg = replacePlayerNameSafely(updatedMsg, killer, killerCustomName);
                }
            }
            event.deathMessage(updatedMsg);
        }
    }

    @EventHandler
    public void onPlayerAdvancementDone(org.bukkit.event.player.PlayerAdvancementDoneEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        net.kyori.adventure.text.Component customName = getCustomName(player);
        net.kyori.adventure.text.Component msg = event.message();
        if (msg != null && customName != null) {
            event.message(replacePlayerNameSafely(msg, player, customName));
        }
    }

    private net.kyori.adventure.text.Component replacePlayerNameSafely(net.kyori.adventure.text.Component comp,
            org.bukkit.entity.Player targetPlayer, net.kyori.adventure.text.Component replacement) {
        if (comp == null || replacement == null) return comp;

        // 1. 確実な判定: HoverEvent が ShowEntity であり、対象プレイヤーのUUIDと一致する場合は丸ごと置換
        net.kyori.adventure.text.event.HoverEvent<?> hover = comp.hoverEvent();
        if (hover != null && hover.action() == net.kyori.adventure.text.event.HoverEvent.Action.SHOW_ENTITY) {
            Object value = hover.value();
            if (value instanceof net.kyori.adventure.text.event.HoverEvent.ShowEntity showEntity) {
                if (showEntity.id().equals(targetPlayer.getUniqueId())) {
                    return replacement;
                }
            }
        }

        // 2. フォールバック判定: 単一の TextComponent の内容がプレイヤーの名前と完全一致する場合
        // （他のプラグインが (JE) 等を付けている場合は一致しないが、それらはHoverEvent判定でカバーされることが多い）
        if (comp instanceof net.kyori.adventure.text.TextComponent textComp) {
            if (textComp.content().equals(targetPlayer.getName())) {
                return replacement;
            }
        }

        // 3. 子要素や引数を再帰的に探索して置換する
        net.kyori.adventure.text.Component result = comp;
        
        if (result instanceof net.kyori.adventure.text.TranslatableComponent tc) {
            java.util.List<net.kyori.adventure.text.TranslationArgument> newArgs = new java.util.ArrayList<>();
            boolean changed = false;
            for (net.kyori.adventure.text.TranslationArgument arg : tc.arguments()) {
                if (arg.value() instanceof net.kyori.adventure.text.Component argComp) {
                    net.kyori.adventure.text.Component newArgComp = replacePlayerNameSafely(argComp, targetPlayer, replacement);
                    newArgs.add(net.kyori.adventure.text.TranslationArgument.component(newArgComp));
                    if (newArgComp != argComp) changed = true;
                } else {
                    newArgs.add(arg);
                }
            }
            if (changed) {
                result = tc.arguments(newArgs);
            }
        }

        if (!result.children().isEmpty()) {
            java.util.List<net.kyori.adventure.text.Component> newChildren = new java.util.ArrayList<>();
            boolean changed = false;
            for (net.kyori.adventure.text.Component child : result.children()) {
                net.kyori.adventure.text.Component newChild = replacePlayerNameSafely(child, targetPlayer, replacement);
                newChildren.add(newChild);
                if (newChild != child) changed = true;
            }
            if (changed) {
                result = result.children(newChildren);
            }
        }

        return result;
    }

    private net.kyori.adventure.text.Component getCustomName(org.bukkit.entity.Player player) {
        String playerName = player.getName();
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

                    return net.kyori.adventure.text.Component
                            .text(user.discordUsername)
                            .append(net.kyori.adventure.text.Component.text(" (")
                                    .color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                            .append(net.kyori.adventure.text.Component.text(displayPlayerName)
                                    .color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                            .append(net.kyori.adventure.text.Component.text(")")
                                    .color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                            .append(editionBadge);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("カスタム名取得中にDBエラーが発生しました: " + e.getMessage());
        }
        return null;
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
