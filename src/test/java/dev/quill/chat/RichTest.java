package dev.quill.chat;

import dev.quill.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RichTest {

    private static final ShadowColor PINK = ShadowColor.shadowColor(net.kyori.adventure.text.format.TextColor.color(0xff7aa8), 255);

    @Test
    void shadowColourSurvivesFlattenAndReplace() {
        Component glitch = Component.text().append(Component.text("hi", NamedTextColor.WHITE).shadowColor(PINK)).build();
        List<Rich.Ch> chars = Rich.flatten(glitch);
        Component back = Rich.replace(chars, List.of());
        assertEquals(PINK, back.children().get(0).style().shadowColor());
    }

    @Test
    void shadowColourSurvivesTheFormat() {
        Component glitch = Component.text("hi", NamedTextColor.WHITE).shadowColor(PINK);
        Component line = Text.parse("<gray>x <white><message>", Placeholder.component("message", glitch));
        ShadowColor found = null;
        for (Component c : line.children()) {
            if (c.style().shadowColor() != null) found = c.style().shadowColor();
            for (Component d : c.children()) if (d.style().shadowColor() != null) found = d.style().shadowColor();
        }
        assertEquals(PINK, found);
    }
}
