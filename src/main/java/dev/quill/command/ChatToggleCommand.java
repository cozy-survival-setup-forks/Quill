package dev.quill.command;

import dev.quill.QuillPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /chattoggle: hide public chat for yourself, and bring it back. */
public final class ChatToggleCommand implements CommandExecutor {

    private final QuillPlugin plugin;

    public ChatToggleCommand(QuillPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.lang().send(sender, "players-only");
            return true;
        }
        plugin.lang().send(sender, plugin.state().toggleHidden(p) ? "chat-hidden" : "chat-shown");
        return true;
    }
}
