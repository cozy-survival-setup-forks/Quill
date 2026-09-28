package dev.quill.command;

import dev.quill.QuillPlugin;
import dev.quill.filter.Verdict;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** /quill: the admin tools, and the "view" that the [inv] links in chat run. */
public final class QuillCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("reload", "spy", "local", "mutechat", "clearchat", "filter", "warnings", "clearwarnings");

    private final QuillPlugin plugin;

    public QuillCommand(QuillPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.lang().send(sender, "usage");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("view")) {
            view(sender, args);
            return true;
        }
        if (!sender.hasPermission("quill.admin")) {
            plugin.lang().send(sender, "no-permission");
            return true;
        }
        switch (sub) {
            case "reload" -> plugin.lang().send(sender, plugin.reloadAll() ? "reloaded" : "reload-failed");
            case "spy" -> {
                if (!(sender instanceof Player p)) {
                    plugin.lang().send(sender, "players-only");
                    return true;
                }
                plugin.lang().send(sender, plugin.state().toggleSpy(p.getUniqueId()) ? "spy-on" : "spy-off");
            }
            case "local" -> local(sender, args);
            case "mutechat" -> {
                plugin.state().muted = !plugin.state().muted;
                plugin.lang().broadcast(plugin.state().muted ? "chat-muted-broadcast" : "chat-unmuted-broadcast");
            }
            case "clearchat" -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("quill.bypass.chatclear")) continue;
                    for (int i = 0; i < 100; i++) p.sendMessage(Component.empty());
                }
                plugin.lang().broadcast("chat-cleared", Placeholder.unparsed("player", sender.getName()));
            }
            case "filter" -> filter(sender, args);
            case "warnings", "clearwarnings" -> warnings(sender, args, sub.equals("clearwarnings"));
            default -> plugin.lang().send(sender, "usage");
        }
        return true;
    }

    private void view(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p) || args.length < 2) return;
        if (!plugin.snapshots().open(p, args[1])) plugin.lang().send(sender, "view-expired");
    }

    private void local(CommandSender sender, String[] args) {
        var state = plugin.state();
        boolean on = args.length > 1 ? args[1].equalsIgnoreCase("on") : !state.localOn;
        state.localOn = on;
        plugin.lang().send(sender, on ? "local-on" : "local-off");
    }

    /** /quill filter test <text>: what the filter says about a message, and why. */
    private void filter(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("test")) {
            plugin.lang().send(sender, "usage-filter");
            return;
        }
        String text = String.join(" ", List.of(args).subList(2, args.length));
        Set<String> names = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName().toLowerCase(Locale.ROOT));
        Verdict v = plugin.engine().check(text, names, Set.of(), false);
        if (v == null) {
            plugin.lang().send(sender, "filter-test-clean");
        } else {
            plugin.lang().send(sender, "filter-test-stopped", Placeholder.unparsed("category", plugin.engine().label(v.category())),
                    Placeholder.unparsed("rule", v.rule()), Placeholder.unparsed("matched", v.matched()));
        }
    }

    private void warnings(CommandSender sender, String[] args, boolean clear) {
        if (args.length < 2) {
            plugin.lang().send(sender, "usage");
            return;
        }
        // getOfflinePlayer(String) asks Mojang, on the main thread, for a name that isn't cached
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            plugin.lang().send(sender, "player-not-found", Placeholder.unparsed("player", args[1]));
            return;
        }
        if (clear) {
            plugin.warnings().clear(target.getUniqueId());
            plugin.lang().send(sender, "warnings-cleared", Placeholder.unparsed("player", args[1]));
            return;
        }
        int count = plugin.warnings().get(target.getUniqueId(), System.currentTimeMillis());
        plugin.lang().send(sender, "warnings-of", Placeholder.unparsed("player", args[1]), Placeholder.unparsed("count", String.valueOf(count)),
                Placeholder.unparsed("max", String.valueOf(plugin.settings().maxWarnings)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("quill.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("warnings") || args[0].equalsIgnoreCase("clearwarnings"))) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("filter")) {
            out.add("test");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("local")) {
            out.addAll(List.of("on", "off"));
        }
        return out;
    }
}
