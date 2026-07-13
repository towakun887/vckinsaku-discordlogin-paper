package net.niwa.kinsaku.discordsystem.util;

import net.dv8tion.jda.api.interactions.commands.Command;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandMentionHelper {
    private static final Map<String, String> commandMentions = new HashMap<>();

    public static void cacheMentions(List<Command> commands) {
        for (Command cmd : commands) {
            commandMentions.put(cmd.getName(), "</" + cmd.getName() + ":" + cmd.getId() + ">");
        }
    }

    public static String getMention(String commandName) {
        return commandMentions.getOrDefault(commandName, "`/" + commandName + "`");
    }

    public static String getMention(String commandName, String subcommandName) {
        String base = commandMentions.get(commandName);
        if (base != null) {
            int colonIndex = base.indexOf(':');
            if (colonIndex != -1) {
                String id = base.substring(colonIndex + 1, base.length() - 1);
                return "</" + commandName + " " + subcommandName + ":" + id + ">";
            }
        }
        return "`/" + commandName + " " + subcommandName + "`";
    }
}
