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
        // the chat listener only honours local chat for players with this permission, so without it
        // the toggle would say "only players nearby" while the messages still went to everyone
        if (!p.hasPermission("quill.chat.local")) {
            plugin.lang().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.lang().send(sender, plugin.state().toggleLocalMode(p.getUniqueId()) ? "localchat-on" : "localchat-off");
            return true;
        }
        String prefix = plugin.settings().localPrefix;
        String said = String.join(" ", args);
        if (!prefix.isEmpty()) {
            // said as if typed with the local prefix
            Bukkit.getScheduler().runTask(plugin, () -> p.chat(prefix + said));
            return true;
        }
        // local.prefix is empty: without a prefix the message would go out to everyone. Player#chat is
        // synchronous on the main thread, so local mode can be switched on for just this one message.
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean wasOn = plugin.state().localMode(p.getUniqueId());
            if (!wasOn) plugin.state().toggleLocalMode(p.getUniqueId());
            try {
                p.chat(said);
            } finally {
                if (!wasOn) plugin.state().toggleLocalMode(p.getUniqueId());
            }
        });
        return true;
    }
}
