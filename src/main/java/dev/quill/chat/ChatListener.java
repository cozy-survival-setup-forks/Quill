package dev.quill.chat;

import dev.quill.QuillFilterEvent;
import dev.quill.QuillPlugin;
import dev.quill.Settings;
import dev.quill.Text;
import dev.quill.filter.FilterEngine;
import dev.quill.filter.Spam;
import dev.quill.filter.Verdict;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two steps for every message, on Paper's async chat event. First the filter, early, so a stopped message is cancelled
 * before any other plugin (Spectrum colours it later and skips cancelled ones) has touched it. Then, late, the local
 * range, the format and the extras, which work on the message as the other plugins left it.
 */
public final class ChatListener implements Listener {

    private static final Pattern LINK = Pattern.compile("(?i)\\b(?:https?://|www\\.)[^\\s<>\"']+");

    private final QuillPlugin plugin;
    // the @mention pattern lists every online name, so it only needs rebuilding when someone joins or quits
    private volatile Pattern mentionPattern;
    private volatile boolean mentionsDirty = true;

    public ChatListener(QuillPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- step 1: stop what must not be said

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFilter(AsyncChatEvent event) {
        Player p = event.getPlayer();
        Settings s = plugin.settings();
        String text = Text.plain(event.message());

        if (plugin.state().muted && !p.hasPermission("quill.bypass.chatmuted")) {
            event.setCancelled(true);
            plugin.lang().send(p, "chat-muted");
            return;
        }
        // staff chat is for staff only, so it is not filtered
        if (!s.staffPrefix.isEmpty() && text.startsWith(s.staffPrefix) && p.hasPermission("quill.staffchat")) return;
        if (plugin.state().staffMode(p.getUniqueId()) && p.hasPermission("quill.staffchat")) return;

        FilterEngine engine = plugin.engine();
        if (s.filterEnabled && !p.hasPermission("quill.bypass.filter")) {
            Set<String> names = new HashSet<>();
            for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName().toLowerCase(Locale.ROOT));
            Set<String> skip = new HashSet<>();
            for (String category : engine.terms().categories().keySet()) {
                if (p.hasPermission("quill.bypass.filter." + category)) skip.add(category);
            }
            if (p.hasPermission("quill.bypass.filter.advertising")) skip.add("advertising");
            Verdict verdict = engine.check(text, names, skip, p.hasPermission("quill.links"));
            if (verdict != null) {
                stop(event, p, text, verdict);
                return;
            }
        }

        if (!p.hasPermission("quill.bypass.spam")) {
            Spam.Kind kind = plugin.spam().check(p.getUniqueId(), text, System.currentTimeMillis());
            if (kind != null) {
                event.setCancelled(true);
                plugin.lang().send(p, "spam-" + kind.name().toLowerCase(Locale.ROOT));
            }
        }
    }

    private void stop(AsyncChatEvent event, Player p, String text, Verdict verdict) {
        Settings s = plugin.settings();
        event.setCancelled(true);
        int count = plugin.warnings().add(p.getUniqueId(), System.currentTimeMillis());
        String label = plugin.engine().label(verdict.category());

        TagResolver[] who = {Placeholder.unparsed("category", label), Placeholder.unparsed("count", String.valueOf(Math.min(count, s.maxWarnings))),
                Placeholder.unparsed("max", String.valueOf(s.maxWarnings)), Placeholder.unparsed("player", p.getName())};
        plugin.lang().send(p, "filter-warning", who);
        if (plugin.lang().has("filter-actionbar")) p.sendActionBar(plugin.lang().bare("filter-actionbar", who));

        if (s.alertStaff) {
            Component hover = Component.text("Message: ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .append(Component.text(text, net.kyori.adventure.text.format.NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("Matched: " + verdict.matched() + " (" + verdict.rule() + ")", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text("Click to see their warnings", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
            Component alert = plugin.lang().get("filter-alert", who).hoverEvent(HoverEvent.showText(hover))
                    .clickEvent(ClickEvent.runCommand("/quill warnings " + p.getName()));
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("quill.filter.alerts")) staff.sendMessage(alert);
            }
        }
        if (s.logFile) log(p, verdict, text, count);

        List<String> commands = new ArrayList<>();
        for (Settings.Escalation e : s.escalation) {
            if (count == e.at() || (e.repeat() && count >= e.at())) commands.addAll(e.commands());
        }
        if (!commands.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (String c : commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", p.getName()).replace("%category%", verdict.category()));
                }
            });
        }
        Bukkit.getPluginManager().callEvent(new QuillFilterEvent(p, text, verdict, count));
    }

    private void log(Player p, Verdict v, String text, int count) {
        String line = LocalDateTime.now().toString() + " " + p.getName() + " (" + p.getUniqueId() + ") warning " + count + " [" + v.category()
                + ", " + v.rule() + ": " + v.matched() + "] " + text.replace('\n', ' ') + System.lineSeparator();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Files.writeString(plugin.getDataFolder().toPath().resolve("filter.log"), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not write filter.log: " + e.getMessage());
            }
        });
    }

    // ---------------------------------------------------------------- step 2: range, format, extras

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFormat(AsyncChatEvent event) {
        // another plugin already took over how this message looks (dungeon chat, team chat): leave it alone
        if (event.renderer().getClass() != ChatRenderer.defaultRenderer().getClass()) return;

        Player p = event.getPlayer();
        Settings s = plugin.settings();
        ChatState state = plugin.state();

        List<Rich.Ch> all = Rich.flatten(event.message());
        if (all == null) {
            all = new ArrayList<>();
            for (char c : Text.plain(event.message()).toCharArray()) all.add(new Rich.Ch(c, Style.empty()));
        }
        String full = Rich.text(all);

        boolean localActive = state.localOn && !s.ignoreWorlds.contains(p.getWorld().getName().toLowerCase(Locale.ROOT));
        boolean staff = false, global = true;
        int cut = 0;
        if (!s.staffPrefix.isEmpty() && full.startsWith(s.staffPrefix) && p.hasPermission("quill.staffchat")) {
            staff = true;
            cut = s.staffPrefix.length();
        } else if (state.staffMode(p.getUniqueId()) && p.hasPermission("quill.staffchat")) {
            staff = true;
        } else if (!s.localPrefix.isEmpty() && full.startsWith(s.localPrefix) && p.hasPermission("quill.chat.local")) {
            // /localchat <message>: only nearby players hear it
            global = !localActive;
            cut = s.localPrefix.length();
        } else if (localActive && state.localMode(p.getUniqueId()) && p.hasPermission("quill.chat.local")) {
            global = false;
        }
        while (cut < full.length() && full.charAt(cut) == ' ') cut++;
        if (cut >= full.length()) {
            event.setCancelled(true);
            return;
        }
        List<Rich.Ch> body = all.subList(cut, all.size());
        String text = Rich.text(body);

        // the extras: items, inventories, links, @mentions
        List<Rich.Span> spans = new ArrayList<>();
        Set<Player> mentioned = new HashSet<>();
        if (s.items) itemSpans(p, s, text, body, spans);
        if (s.clickableLinks) linkSpans(text, body, spans);
        if (s.mentions && text.indexOf('@') >= 0 && p.hasPermission("quill.chat.mention")) mentionSpans(p, s, text, spans, mentioned);
        Component message = Rich.replace(body, disjoint(spans));
        event.message(message);

        // who hears it
        Set<Audience> viewers = event.viewers();
        Set<UUID> spySet = new HashSet<>();
        int heard = 0;
        if (staff) {
            viewers.removeIf(a -> a instanceof Player v && !v.hasPermission("quill.staffchat"));
        } else {
            boolean local = !global;
            double r2 = s.radius * s.radius;
            for (Audience a : List.copyOf(viewers)) {
                if (!(a instanceof Player v) || v.equals(p)) continue;
                UUID id = v.getUniqueId();
                boolean remove = false;
                boolean near = !local || inRange(p, v, r2);
                if (state.hidden(id) && !v.hasPermission("quill.bypass.chattoggle")) remove = true;
                else if (!p.hasPermission("quill.bypass.ignore") && plugin.hooks().ignores(v, p)) remove = true;
                else if (!near) {
                    if (state.spying(id)) spySet.add(id);
                    else remove = true;
                }
                if (remove) viewers.remove(v);
                else if (near) heard++;
            }
            if (local && heard == 0 && s.hintWhenAlone && plugin.lang().has("nobody-heard")) {
                p.sendActionBar(plugin.lang().bare("nobody-heard"));
            }
        }

        // how it looks
        Formats.Format format = plugin.formats().pick(p);
        String template = staff ? s.staffFormat : global ? format.global() : format.local();
        Component name = nameOf(p, s, event.getPlayer().displayName());
        Component main = compose(template, p, name, message, s);
        Component spy = spySet.isEmpty() ? main : compose(s.spyFormat, p, name, message, s);
        event.renderer((source, displayName, msg, viewer) -> viewer instanceof Player v && spySet.contains(v.getUniqueId()) ? spy : main);

        if (!mentioned.isEmpty()) {
            Set<Player> audience = mentioned;
            Bukkit.getScheduler().runTask(plugin, () -> notifyMentions(p, s, audience));
        }
    }

    private static boolean inRange(Player a, Player b, double r2) {
        if (!a.getWorld().equals(b.getWorld())) return false;
        Location x = a.getLocation(), y = b.getLocation();
        return x.distanceSquared(y) <= r2;
    }

    private Component nameOf(Player p, Settings s, Component fallback) {
        String resolved = plugin.hooks().apply(p, s.name).trim();
        Component name = resolved.isEmpty() ? fallback : Text.legacy(resolved);
        if (!s.hover.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String line : s.hover) lines.add(Text.parse(plugin.hooks().apply(p, line.replace("{player}", p.getName()))));
            name = name.hoverEvent(HoverEvent.showText(Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines)));
        }
        if (!s.hoverClick.isEmpty()) name = name.clickEvent(ClickEvent.suggestCommand(s.hoverClick.replace("{player}", p.getName())));
        return name;
    }

    private Component compose(String template, Player p, Component name, Component message, Settings s) {
        String filled = plugin.hooks().apply(p, template);
        String pre = plugin.hooks().apply(p, s.prefix).trim();
        String suf = plugin.hooks().apply(p, s.suffix).trim();
        return Text.parse(filled,
                Placeholder.component("name", name),
                Placeholder.component("prefix", pre.isEmpty() ? Component.empty() : Text.rich(pre, true)),
                Placeholder.component("suffix", suf.isEmpty() ? Component.empty() : Text.rich(suf, true)),
                Placeholder.component("message", message),
                Placeholder.component("displayname", p.displayName()),
                Placeholder.unparsed("player", p.getName()),
                Placeholder.unparsed("world", p.getWorld().getName()));
    }

    // ---------------------------------------------------------------- the extras

    private void itemSpans(Player p, Settings s, String text, List<Rich.Ch> body, List<Rich.Span> spans) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : s.itemTokens) {
            if (!p.hasPermission("quill.chat.item")) break;
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) break;
            int at = lower.indexOf(token);
            while (at >= 0) {
                Component c = hand.clone().displayName();
                if (hand.getAmount() > 1) c = c.append(Component.text(" x" + hand.getAmount(), net.kyori.adventure.text.format.NamedTextColor.GRAY));
                spans.add(new Rich.Span(at, at + token.length(), c));
                at = lower.indexOf(token, at + token.length());
            }
        }
        snapshotSpans(p, lower, s.invTokens, false, s.invFormat, "quill.chat.inventory", spans);
        snapshotSpans(p, lower, s.enderTokens, true, s.enderFormat, "quill.chat.ender", spans);
    }

    private void snapshotSpans(Player p, String lower, List<String> tokens, boolean ender, String format, String permission, List<Rich.Span> spans) {
        if (!p.hasPermission(permission)) return;
        String id = null;
        for (String token : tokens) {
            int at = lower.indexOf(token);
            while (at >= 0) {
                if (id == null) id = plugin.snapshots().take(p, ender);
                Component c = Text.parse(format, Placeholder.unparsed("player", p.getName()))
                        .clickEvent(ClickEvent.runCommand("/quill view " + id))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to look inside", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
                spans.add(new Rich.Span(at, at + token.length(), c));
                at = lower.indexOf(token, at + token.length());
            }
        }
    }

    private void linkSpans(String text, List<Rich.Ch> body, List<Rich.Span> spans) {
        Matcher m = LINK.matcher(text);
        while (m.find()) {
            String url = m.group();
            while (url.endsWith(".") || url.endsWith(",") || url.endsWith("!") || url.endsWith(")")) url = url.substring(0, url.length() - 1);
            if (url.length() < 4) continue;
            int end = m.start() + url.length();
            String target = url.toLowerCase(Locale.ROOT).startsWith("www.") ? "https://" + url : url;
            Component c = Rich.replace(body.subList(m.start(), end), List.of())
                    .decorate(TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(target));
            spans.add(new Rich.Span(m.start(), end, c));
        }
    }

    private void mentionSpans(Player p, Settings s, String text, List<Rich.Span> spans, Set<Player> mentioned) {
        Pattern pattern = mentionPattern();
        if (pattern == null) return;
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            Player target = Bukkit.getPlayerExact(m.group(1));
            if (target == null) continue;
            spans.add(new Rich.Span(m.start(), m.end(), Text.parse(s.mentionFormat, Placeholder.unparsed("player", target.getName()))));
            if (!target.equals(p)) mentioned.add(target);
        }
    }

    /** Names change only on join or quit, so the pattern is rebuilt then instead of once per message. */
    private Pattern mentionPattern() {
        if (!mentionsDirty) return mentionPattern;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparingInt((Player x) -> x.getName().length()).reversed());
        StringBuilder names = new StringBuilder();
        for (Player o : online) {
            if (names.length() > 0) names.append('|');
            names.append(Pattern.quote(o.getName()));
        }
        mentionPattern = names.length() == 0 ? null : Pattern.compile("(?i)@(" + names + ")(?![A-Za-z0-9_])");
        mentionsDirty = false;
        return mentionPattern;
    }

    private void notifyMentions(Player from, Settings s, Set<Player> targets) {
        for (Player t : targets) {
            if (!t.isOnline() || t.hasPermission("quill.ignore.mentions")) continue;
            if (!s.mentionSound.isEmpty()) t.playSound(t.getLocation(), s.mentionSound, 1f, 1.2f);
            if (!s.mentionActionbar.isEmpty()) t.sendActionBar(Text.parse(s.mentionActionbar, Placeholder.unparsed("player", from.getName())));
        }
    }

    /** Sorted by position, with anything that overlaps an earlier piece dropped. */
    private static List<Rich.Span> disjoint(List<Rich.Span> spans) {
        List<Rich.Span> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparingInt(Rich.Span::start));
        List<Rich.Span> out = new ArrayList<>();
        int end = 0;
        for (Rich.Span sp : sorted) {
            if (sp.start() < end) continue;
            out.add(sp);
            end = sp.end();
        }
        return out;
    }

    // ---------------------------------------------------------------- upkeep

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.state().join(event.getPlayer());
        mentionsDirty = true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.state().quit(event.getPlayer());
        plugin.spam().forget(event.getPlayer().getUniqueId());
        mentionsDirty = true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Snapshots.Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Snapshots.Holder) event.setCancelled(true);
    }
}
