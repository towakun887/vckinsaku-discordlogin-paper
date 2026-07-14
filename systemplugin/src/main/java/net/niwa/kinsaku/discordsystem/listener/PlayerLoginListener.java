package net.niwa.kinsaku.discordsystem.listener;

import net.niwa.kinsaku.discordsystem.KinsakuDiscordSystem;
import net.niwa.kinsaku.discordsystem.database.DatabaseManager;
import net.niwa.kinsaku.discordsystem.database.DatabaseManager.MinecraftAccount;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class PlayerLoginListener implements Listener {

    private final KinsakuDiscordSystem plugin;
    private final DatabaseManager dbManager;

    public PlayerLoginListener(KinsakuDiscordSystem plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
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

        Component customName = getCustomName(player);
        if (customName != null) {
            player.displayName(customName);
            player.playerListName(customName);

            Component joinMsg = customName
                    .append(Component.text(" がサーバーに参加しました。"));
            event.joinMessage(joinMsg);
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Component customName = getCustomName(player);
        if (customName != null) {
            Component quitMsg = customName
                    .append(Component.text(" がサーバーを退出しました。"));
            event.quitMessage(quitMsg);
        }
    }

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        Component customName = getCustomName(player);
        Component deathMsg = event.deathMessage();
        if (deathMsg != null) {
            Component updatedMsg = deathMsg;
            if (customName != null) {
                updatedMsg = replacePlayerNameSafely(updatedMsg, player, customName);
            }
            Player killer = player.getKiller();
            if (killer != null) {
                Component killerCustomName = getCustomName(killer);
                if (killerCustomName != null) {
                    updatedMsg = replacePlayerNameSafely(updatedMsg, killer, killerCustomName);
                }
            }
            event.deathMessage(updatedMsg);
        }
    }

    @EventHandler
    public void onPlayerAdvancementDone(org.bukkit.event.player.PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Component customName = getCustomName(player);
        Component msg = event.message();
        if (msg != null && customName != null) {
            event.message(replacePlayerNameSafely(msg, player, customName));
        }
    }

    private Component replacePlayerNameSafely(Component comp,
            Player targetPlayer, Component replacement) {
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
        Component result = comp;
        
        if (result instanceof net.kyori.adventure.text.TranslatableComponent tc) {
            List<TranslationArgument> newArgs = new ArrayList<>();
            boolean changed = false;
            for (TranslationArgument arg : tc.arguments()) {
                if (arg.value() instanceof Component argComp) {
                    Component newArgComp = replacePlayerNameSafely(argComp, targetPlayer, replacement);
                    newArgs.add(TranslationArgument.component(newArgComp));
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
            List<Component> newChildren = new ArrayList<>();
            boolean changed = false;
            for (Component child : result.children()) {
                Component newChild = replacePlayerNameSafely(child, targetPlayer, replacement);
                newChildren.add(newChild);
                if (newChild != child) changed = true;
            }
            if (changed) {
                result = result.children(newChildren);
            }
        }

        return result;
    }

    private Component getCustomName(Player player) {
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

                    Component editionBadge;
                    if (isBedrock) {
                        editionBadge = Component.text("(BE)")
                                .color(net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN);
                    } else {
                        editionBadge = Component.text("(JE)")
                                .color(net.kyori.adventure.text.format.NamedTextColor.DARK_RED);
                    }

                    return Component
                            .text(user.discordUsername)
                            .append(Component.text(" (")
                                    .color(NamedTextColor.GRAY))
                            .append(Component.text(displayPlayerName)
                                    .color(NamedTextColor.GRAY))
                            .append(Component.text(")")
                                    .color(NamedTextColor.GRAY))
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
                    event.kickMessage(Component.text(msg));
                }
            }
        }
    }
}