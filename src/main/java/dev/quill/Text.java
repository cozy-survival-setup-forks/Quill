package dev.quill;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MiniMessage with the old & codes still working, as in the other cozy plugins. */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    // For values a player can influence (a nickname): colours and gradients only, no click, hover or the like
    private static final MiniMessage COLOURS_ONLY = MiniMessage.builder().tags(TagResolver.builder().resolvers(
            StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(), StandardTags.rainbow(),
            StandardTags.reset(), StandardTags.transition(), StandardTags.shadowColor()).build()).build();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.AMPERSAND_CHAR).hexColors().build();

    // §x§r§r§g§g§b§b, the way most plugins hand out hex colours in placeholders
    private static final Pattern X_HEX = Pattern.compile("(?i)&x((?:&[0-9a-f]){6})");
    private static final Pattern CODE = Pattern.compile("&(#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR])");
    private static final Pattern MINI_TAG = Pattern.compile("<[/!?#a-zA-Z]");
    private static final String[] TAGS = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

    private Text() {
    }

    /** One colour code style: § becomes &amp;, and &amp;x&amp;r&amp;r&amp;g&amp;g&amp;b&amp;b becomes &amp;#rrggbb. */
    public static String normalize(String text) {
        String result = text.replace('§', '&');
        if (result.indexOf('&') < 0) return result;
        return X_HEX.matcher(result).replaceAll(match -> "&#" + match.group(1).replace("&", ""));
    }

    /** Turns &amp; codes into MiniMessage tags. A colour after a decoration clears the decoration, as in old chat. */
    public static String toMini(String text) {
        String source = normalize(text);
        Matcher m = CODE.matcher(source);
        StringBuilder out = new StringBuilder();
        boolean decorated = false;
        int last = 0;
        while (m.find()) {
            out.append(source, last, m.start());
            last = m.end();
            String code = m.group(1);
            char c = Character.toLowerCase(code.charAt(0));
            boolean colour = code.length() > 1 || "0123456789abcdef".indexOf(c) >= 0;
            if (colour) {
                if (decorated) out.append("<reset>");
                decorated = false;
                out.append(code.length() > 1 ? "<" + code + ">" : "<" + TAGS["0123456789abcdef".indexOf(c)] + ">");
            } else if (c == 'r') {
                decorated = false;
                out.append("<reset>");
            } else {
                decorated = true;
                out.append(switch (c) {
                    case 'k' -> "<obfuscated>";
                    case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>";
                    case 'n' -> "<underlined>";
                    default -> "<italic>";
                });
            }
        }
        return out.append(source, last, source.length()).toString();
    }

    public static Component parse(String template, TagResolver... resolvers) {
        return MINI.deserialize(toMini(template), resolvers);
    }

    /**
     * What a placeholder gave (%luckperms_prefix%, %eternaltags_tag%, %spectrum_name%): old codes in any form,
     * MiniMessage, or both. With {@code trusted} false only colours, decorations and gradients are read, for
     * values a player can set themselves.
     */
    public static Component rich(String value, boolean trusted) {
        String text = normalize(value);
        if (!MINI_TAG.matcher(text).find()) return LEGACY.deserialize(text);
        return (trusted ? MINI : COLOURS_ONLY).deserialize(toMini(text));
    }

    /** Text with legacy codes (a placeholder's output, like %spectrum_name%) as a component. */
    public static Component legacy(String text) {
        return rich(text, false);
    }

    /**
     * The colour and decorations still open at the end of a prefix, as they are in old-style chat where a code at
     * the end of the prefix (&#RRGGBB after the last space) colours the name that follows.
     */
    public static Style trailingStyle(String value) {
        return tail(rich(value + "X", true), Style.empty());
    }

    private static Style tail(Component c, Style inherited) {
        Style own = c.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        List<Component> kids = c.children();
        return kids.isEmpty() ? own : tail(kids.get(kids.size() - 1), own);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
