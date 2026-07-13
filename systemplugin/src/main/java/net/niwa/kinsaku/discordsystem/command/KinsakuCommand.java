package net.niwa.kinsaku.discordsystem.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.niwa.kinsaku.discordsystem.KinsakuDiscordSystem;
import net.niwa.kinsaku.discordsystem.database.UniversityCache;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.List;

public class KinsakuCommand implements CommandExecutor, TabCompleter {

    private final KinsakuDiscordSystem plugin;
    private final UniversityCache uniCache;

    public KinsakuCommand(KinsakuDiscordSystem plugin, UniversityCache uniCache) {
        this.plugin = plugin;
        this.uniCache = uniCache;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("kinsaku.admin")) {
            sender.sendMessage(Component.text("このコマンドを実行する権限がありません。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        if ("reload".equals(subCommand)) {
            plugin.reloadConfig();
            uniCache.reload();
            sender.sendMessage(Component.text("[KinsakuDiscordSystem] 設定ファイルと大学リストのキャッシュをリロードしました。", NamedTextColor.GREEN));
            return true;
        } else if ("status".equals(subCommand)) {
            sender.sendMessage(Component.text("=== KinsakuDiscordSystem 稼働状況 ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("・Folia互換: ", NamedTextColor.YELLOW)
                    .append(Component.text(plugin.isFolia() ? "はい (Folia)" : "いいえ (Paper/Spigot)", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("・APIポート: ", NamedTextColor.YELLOW)
                    .append(Component.text(plugin.getApiPort(), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("・Bedrock接頭辞: ", NamedTextColor.YELLOW)
                    .append(Component.text("\"" + plugin.getBedrockPrefix() + "\"", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("・Floodgateコマンド使用: ", NamedTextColor.YELLOW)
                    .append(Component.text(plugin.isUseFloodgate() ? "はい" : "いいえ", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("API サーバーとデータベースは稼働中です。", NamedTextColor.GREEN));
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== KinsakuDiscordSystem コマンドヘルプ ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/kinsaku-usersystem reload ", NamedTextColor.YELLOW)
                .append(Component.text("- 設定ファイルとキャッシュのリロード", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/kinsaku-usersystem status ", NamedTextColor.YELLOW)
                .append(Component.text("- 稼働状況の確認", NamedTextColor.WHITE)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (!sender.hasPermission("kinsaku.admin")) {
            return list;
        }
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("reload".startsWith(input)) {
                list.add("reload");
            }
            if ("status".startsWith(input)) {
                list.add("status");
            }
        }
        return list;
    }
}
