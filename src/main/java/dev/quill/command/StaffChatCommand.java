package dev.quill.command;

import dev.quill.QuillPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /staffchat: with a message it says it to staff, without one it switches staff chat mode on or off. The message
 * goes through the chat event like any other, so the format, Spectrum's colours and other plugins all apply.
 */
public final class StaffChatCommand implements CommandExecutor {

    private final QuillPlugin plugin;

    public StaffChatCommand(QuillPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.lang().send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            plugin.lang().send(sender, plugin.state().toggleStaffMode(p.getUniqueId()) ? "staffchat-on" : "staffchat-off");
            return true;
        }
        // said as if typed with the staff prefix
        String prefix = plugin.settings().staffPrefix;
        String text = prefix + String.join(" ", args);
        Bukkit.getScheduler().runTask(plugin, () -> p.chat(text));
        return true;
    }
}
