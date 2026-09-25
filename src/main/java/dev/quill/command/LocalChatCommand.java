package dev.quill.command;

import dev.quill.QuillPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /localchat: with a message it says it to the players nearby, without one it switches your own chat between local
 * and everyone. Other messages go to everyone. The message goes through the chat event like any other, so the format
 * and other plugins all apply.
 */
public final class LocalChatCommand implements CommandExecutor {

    private final QuillPlugin plugin;

    public LocalChatCommand(QuillPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.lang().send(sender, "players-only");
            return true;
        }
        if (!plugin.state().localOn) {
            plugin.lang().send(sender, "local-off");
            return true;
        }
        if (args.length == 0) {
            plugin.lang().send(sender, plugin.state().toggleLocalMode(p.getUniqueId()) ? "localchat-on" : "localchat-off");
            return true;
        }
        // said as if typed with the local prefix
        String text = plugin.settings().localPrefix + String.join(" ", args);
        Bukkit.getScheduler().runTask(plugin, () -> p.chat(text));
        return true;
    }
}
