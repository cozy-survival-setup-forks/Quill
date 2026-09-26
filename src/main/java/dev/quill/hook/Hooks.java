package dev.quill.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

/** The plugins Quill works with when they are there: PlaceholderAPI, and Essentials for ignoring. Nothing here is required. */
public final class Hooks {

    private static final Pattern UNRESOLVED = Pattern.compile("%[A-Za-z0-9_]+%");

    private boolean papi;
    private Plugin essentials;
    private Method getUser, isIgnored;

    public void refresh() {
        papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        getUser = null;
        isIgnored = null;
        if (essentials != null && essentials.isEnabled()) {
            try {
                getUser = essentials.getClass().getMethod("getUser", Player.class);
                // the User class implements com.earth2me.essentials.IUser, not the net.ess3.api one
                Class<?> iUser = Class.forName("com.earth2me.essentials.IUser", true, essentials.getClass().getClassLoader());
                isIgnored = getUser.getReturnType().getMethod("isIgnoredPlayer", iUser);
            } catch (ReflectiveOperationException | LinkageError e) {
                getUser = null;
                isIgnored = null;
            }
        }
    }

    public boolean papi() {
        return papi;
    }

    /**
     * Fills in placeholders. A placeholder that could not be filled in (its plugin is not installed) is removed,
     * so it never shows up in chat as %luckperms_prefix%.
     */
    public String apply(Player player, String text) {
        if (text == null || text.isEmpty() || text.indexOf('%') < 0) return text == null ? "" : text;
        // PlaceholderAPI has no %player%, so it is the same as %player_name%
        String out = papi ? PlaceholderAPI.setPlaceholders(player, text.replace("%player%", "%player_name%")) : text.replace("%player%", player.getName());
        return UNRESOLVED.matcher(out).replaceAll("");
    }

    /** True when the viewer has the sender on their Essentials /ignore list. */
    public boolean ignores(Player viewer, Player sender) {
        if (isIgnored == null) return false;
        try {
            Object v = getUser.invoke(essentials, viewer);
            Object s = getUser.invoke(essentials, sender);
            return v != null && s != null && (boolean) isIgnored.invoke(v, s);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
