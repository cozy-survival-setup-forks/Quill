package dev.quill.hook;

import dev.quill.QuillPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** %quill_warnings%, %quill_spy%, %quill_local% (on or off) and %quill_chat_hidden%. */
public final class QuillExpansion extends PlaceholderExpansion {

    private final QuillPlugin plugin;

    public QuillExpansion(QuillPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "quill";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Quill";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return switch (params.toLowerCase()) {
            case "local" -> plugin.state().localOn ? "on" : "off";
            case "muted" -> plugin.state().muted ? "yes" : "no";
            case "warnings" -> player == null ? "0" : String.valueOf(plugin.warnings().get(player.getUniqueId(), System.currentTimeMillis()));
            case "spy" -> player != null && plugin.state().spying(player.getUniqueId()) ? "on" : "off";
            case "chat_hidden" -> player != null && plugin.state().hidden(player.getUniqueId()) ? "yes" : "no";
            default -> null;
        };
    }
}
