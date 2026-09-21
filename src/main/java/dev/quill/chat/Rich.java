package dev.quill.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * A message taken apart into characters with their styles, so a piece of it can be replaced (an [item], an @name)
 * without losing the colours of the rest. Other plugins colour a message letter by letter, and this keeps that.
 */
final class Rich {

    record Ch(char c, Style style) {
    }

    record Span(int start, int end, Component replacement) {
    }

    private Rich() {
    }

    /** The characters of a message, or null if it holds something other than text. */
    static List<Ch> flatten(Component component) {
        List<Ch> out = new ArrayList<>();
        return walk(component, Style.empty(), out) ? out : null;
    }

    private static boolean walk(Component c, Style parent, List<Ch> out) {
        if (!(c instanceof TextComponent t)) return false;
        Style style = parent.merge(t.style());
        String content = t.content();
        for (int i = 0; i < content.length(); i++) out.add(new Ch(content.charAt(i), style));
        for (Component child : t.children()) if (!walk(child, style, out)) return false;
        return true;
    }

    static String text(List<Ch> chars) {
        StringBuilder sb = new StringBuilder(chars.size());
        for (Ch c : chars) sb.append(c.c);
        return sb.toString();
    }

    /** The characters from `from` on, as a message again. */
    static Component from(List<Ch> chars, int from) {
        return replace(chars.subList(Math.min(from, chars.size()), chars.size()), List.of());
    }

    /** The message with the spans replaced. Spans must be sorted and must not overlap. */
    static Component replace(List<Ch> chars, List<Span> spans) {
        List<Component> parts = new ArrayList<>();
        int i = 0;
        for (Span s : spans) {
            run(chars, i, s.start, parts);
            parts.add(s.replacement);
            i = s.end;
        }
        run(chars, i, chars.size(), parts);
        return Component.empty().children(parts);
    }

    private static void run(List<Ch> chars, int from, int to, List<Component> parts) {
        int i = from;
        while (i < to) {
            Style style = chars.get(i).style;
            StringBuilder sb = new StringBuilder();
            while (i < to && chars.get(i).style.equals(style)) sb.append(chars.get(i++).c);
            parts.add(Component.text(sb.toString(), style));
        }
    }
}
