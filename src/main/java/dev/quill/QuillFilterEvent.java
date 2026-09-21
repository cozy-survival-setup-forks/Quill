package dev.quill;

import dev.quill.filter.Verdict;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired (from the chat thread) when a message was stopped by the filter. For plugins that want to log it or react to it. */
public final class QuillFilterEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String message;
    private final Verdict verdict;
    private final int warnings;

    public QuillFilterEvent(Player player, String message, Verdict verdict, int warnings) {
        super(true);
        this.player = player;
        this.message = message;
        this.verdict = verdict;
        this.warnings = warnings;
    }

    public Player player() {
        return player;
    }

    /** The message that was stopped. */
    public String message() {
        return message;
    }

    public Verdict verdict() {
        return verdict;
    }

    /** The player's warnings, this one included. */
    public int warnings() {
        return warnings;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
