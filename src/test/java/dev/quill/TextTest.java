package dev.quill;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextTest {

    private static boolean hasClick(Component c) {
        if (c.clickEvent() != null) return true;
        for (Component child : c.children()) if (hasClick(child)) return true;
        return false;
    }

    private static boolean has(Component c, TextColor colour) {
        if (colour.equals(c.style().color())) return true;
        for (Component child : c.children()) if (has(child, colour)) return true;
        return false;
    }

    @Test
    void sectionHexFromPlaceholderApiBecomesAColour() {
        // what %eternaltags_tag% gives for &f&l<&#FFD700^-^&f&l>
        Component c = Text.rich("§f§l<§x§F§F§D§7§0§0^-^§f§l>", false);
        assertEquals("<^-^>", Text.plain(c));
        assertTrue(has(c, TextColor.color(0xFFD700)));
    }

    @Test
    void ampersandRepeatedHexIsNotLeftAsText() {
        assertEquals("R", Text.plain(Text.rich("&x&f&f&0&0&0&0R", false)));
        assertEquals("<#ff0000>", Text.toMini("&x&f&f&0&0&0&0").replace("<reset>", ""));
    }

    @Test
    void miniMessageInAPrefixIsParsed() {
        Component c = Text.rich("<gradient:#FF5073:#FFB46C><b>RAINBOW</b></gradient><reset>", true);
        assertEquals("RAINBOW", Text.plain(c));
    }

    @Test
    void aNicknameCannotAddClicks() {
        Component c = Text.rich("<click:run_command:/op me><red>hi</click>", false);
        assertFalse(hasClick(c));
    }

    @Test
    void literalAngleBracketsStayText() {
        assertEquals("<tag>", Text.plain(Text.rich("<tag>", true)));
    }

    @Test
    void aColourEndsBoldLikeOldChat() {
        String mini = Text.toMini("&l<&#FFD700^-^");
        assertTrue(mini.indexOf("<reset>") > mini.indexOf("<bold>"));
    }

    @Test
    void aColourAtTheEndOfThePrefixReachesTheName() {
        var style = Text.trailingStyle("&#8BF0A6&lSPROUT&r &#8BF0A6");
        assertEquals("#8bf0a6", style.color().asHexString().toLowerCase());
        assertFalse(style.hasDecoration(net.kyori.adventure.text.format.TextDecoration.BOLD));
    }

    @Test
    void aPrefixWithNoOpenColourLeavesTheNameAlone() {
        assertNull(Text.trailingStyle("&#8BF0A6&lSPROUT&r ").color());
    }
}
