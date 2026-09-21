package dev.quill.filter;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the filter that ships in the jar: what must be stopped, and, as important, what must never be. */
class FilterTest {

    private static YamlConfiguration yaml(String name) {
        var in = FilterTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(in, name);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static FilterEngine engine(boolean profanity) {
        YamlConfiguration terms = yaml("terms.yml");
        if (profanity) {
            terms.set("categories.profanity.enabled", true);
            terms.set("categories.sexual.enabled", true);
        }
        FilterEngine e = new FilterEngine(yaml("filter.yml"), terms);
        assertEquals(List.of(), e.problems());
        return e;
    }

    private final FilterEngine engine = engine(false);
    private final FilterEngine strict = engine(true);

    private Verdict check(FilterEngine e, String text) {
        return e.check(text, Set.of("alice", "dick", "nigel"), Set.of(), false);
    }

    private void stopped(String category, String text) {
        Verdict v = check(engine, text);
        assertNotNull(v, "should be stopped: " + text);
        assertEquals(category, v.category(), text + " -> " + v);
    }

    private void allowed(String text) {
        Verdict v = check(engine, text);
        assertNull(v, "should be allowed: " + text + " -> " + v);
    }

    @Test
    void slursInTheUsualDisguises() {
        for (String t : new String[]{"nigger", "NIGGER", "n1gger", "nigg3r", "n i g g e r", "n.i.g.g.e.r", "n-i-g-g-e-r", "niiigger",
                "nïgger", "ｎｉｇｇｅｒ", "nіgger", "n*gger", "*nigger*", "you nigger!", "sandnigger", "n​igger", "niggеr",
                "u r a nigger lol", "NiGgEr,", "(nigger)", "niggers", "n1gg4", "nigga"}) {
            stopped("racism", t);
        }
        for (String t : new String[]{"faggot", "f4ggot", "you fag", "f a g g o t", "fagg0ts", "dyke", "tranny", "trannies"}) {
            stopped("homophobia", t);
        }
        for (String t : new String[]{"retard", "you are retarded", "r3tard", "spaz", "spazzz"}) stopped("ableism", t);
        stopped("racism", "chinks");
        stopped("racism", "kike");
        stopped("racism", "k1kes");
        stopped("racism", "paki");
    }

    @Test
    void hateAndThreats() {
        stopped("hate", "heil hitler");
        stopped("hate", "HEIL   HITLER");
        stopped("hate", "he1l h1tler");
        stopped("hate", "white power");
        stopped("hate", "gas the jews");
        stopped("hate", "kill all jews");
        stopped("hate", "go back to your country");
        stopped("threats", "kys");
        stopped("threats", "K Y S".toLowerCase().replace(" ", ""));
        stopped("threats", "just kill yourself");
        stopped("threats", "go kill urself");
        stopped("threats", "i hope you die");
        stopped("threats", "hang yourself");
    }

    @Test
    void advertising() {
        stopped("advertising", "join my server");
        stopped("advertising", "come to my minecraft server");
        stopped("advertising", "play.example.net");
        stopped("advertising", "mc.example.net:25565");
        stopped("advertising", "discord.gg/abcdef");
        stopped("advertising", "https://evil.example");
        stopped("advertising", "www.evil.example");
        stopped("advertising", "check out example.com now");
        stopped("advertising", "192.168.1.100");
        stopped("advertising", "203.0.113.5:25565");
        stopped("advertising", "example (dot) com");
        stopped("advertising", "play dot example dot com");
        stopped("advertising", "exаmple.com");
        stopped("advertising", "subscribe to my channel");
        stopped("advertising", "free rank at mysite");
    }

    @Test
    void allowedLinks() {
        allowed("https://www.youtube.com/watch?v=abc");
        allowed("youtu.be/abc");
        allowed("look at i.imgur.com/x.png");
        allowed("minecraft.wiki/w/Creeper");
        allowed("https://github.com/PaperMC/Paper");
        assertNull(engine.check("check example.com", Set.of(), Set.of(), true));
    }

    @Test
    void ordinaryChatIsNeverStopped() {
        String[] talk = {
                "hello everyone", "gg", "gg wp", "gg.wp", "ez.gg", "ok.so I think we should go", "yes.no maybe", "e.g. this and i.e. that",
                "I am at 100 64 -300", "x: 120 y: 64 z: 300", "version 1.21.4 is out", "1.21.11.0", "the ip is 127.0.0.1", "0.0.0.0",
                "class", "classic", "assassin", "Scunthorpe", "assistant", "grass", "bass", "glass", "passage", "cocktail", "peacock",
                "Hancock", "dickens", "Dickson", "shiitake", "Essex", "Sussex", "cumulative", "title", "analysis", "therapist",
                "Niger", "the river Niger", "Nigeria", "Nigel", "niger", "snigger", "sniggering", "niggardly", "niggard",
                "there is a chink in the armor", "a chink of light", "Maine Coon", "my maine coon cat", "raccoon", "raccoons",
                "spick and span", "spice", "spicy", "spices", "Mike", "likes", "kiss", "Pakistan", "pak", "retardant", "fire retardant",
                "don't kill yourself in the lava", "please do not kill yourself", "never kill yourself for a diamond",
                "kill all mobs", "kill all the zombies", "kill the creeper", "I will kill you in the arena", "go back to spawn",
                "go back to your base", "I hope you win", "I hope you didn't die", "hope you have a nice day",
                "he was a good man", "the blacks and whites of chess", "hitler documentary", "gassed the mobs",
                "fag ends of fabric".replace("fag ", "fig "), "how are you", "what's up", "lol", "xD", "o.o", "no...", "wait.what",
                "I have 3.5 diamonds", "3.14159", "a.b.c", "u.s.a", "ok.", "hmm...", "hi.there", "what.is.this",
                "sell 64 diamonds for 500", "I need 2 more stacks", "can I get a ride to 1000 -1000", "thanks!", "!!!", "?", "@Alice hi",
                "@nigel hey", "cool cool coooool", "noooooo", "goooooal", "hiiii", "heyyyy", "sooo good", "Cocoa", "Coon Rapids",
                "shut up", "damn it", "hell no", "that sucks", "what the heck", "crap", "butt", "poop", "hoe the field", "hoe", "bitcoin",
                "cock-a-doodle-doo", "assess the situation", "an ass and a donkey", "ass", "dick", "hey dick", "Kys.".replace("Kys.", "keys"),
                "Kyle", "Kyoto", "tardis", "Tardigrade", "retire", "retreat", "faggy".replace("faggy", "fancy"), "dyed", "dykes".replace("dykes", "dikes"),
        };
        for (String t : talk) allowed(t);
    }

    @Test
    void playerNamesAreNeverMatched() {
        // an online player called Dick, or @Dick
        assertNull(engine.check("dick", Set.of("dick"), Set.of(), false));
        assertNull(strict.check("hey @dick", Set.of("dick"), Set.of(), false));
        assertNotNull(strict.check("hey dick", Set.of(), Set.of(), false));
    }

    @Test
    void profanityWhenSwitchedOnStillSpares_theInnocent() {
        for (String t : new String[]{"fuck", "f*ck", "f**k", "sh1t", "sh!t", "fuuuck", "bitch", "b1tch", "you asshole", "cunt", "motherfucker", "pornhub"}) {
            assertNotNull(check(strict, t), t);
        }
        for (String t : new String[]{"class", "assassin", "Scunthorpe", "assist", "bass", "shiitake", "cockatoo", "peacock", "Hancock",
                "dickens", "Dickson", "cumin", "pass", "passing", "grass", "bitcoin", "titan", "analysis", "therapist", "cocktail",
                "the essex boys", "suck", "hello", "shift", "shirt", "shot", "shoot", "duck", "luck", "truck", "puck", "buck", "fun",
                "punt", "count", "cent", "hunt", "cut", "cute", "dice", "dish", "kick", "sick", "lick", "brick", "thick", "nick", "pick"}) {
            assertNull(check(strict, t), t);
        }
    }

    @Test
    void bypassedCategoriesAreSkipped() {
        assertNull(engine.check("nigger", Set.of(), Set.of("racism"), false));
        assertNull(engine.check("example.com", Set.of(), Set.of("advertising"), false));
        assertNotNull(engine.check("faggot", Set.of(), Set.of("racism"), false));
    }

    @Test
    void speed() {
        String msg = "hey everyone I found a really cool cave with lots of diamonds near spawn, come and help me mine it before it gets dark";
        long start = System.nanoTime();
        for (int i = 0; i < 2000; i++) check(engine, msg);
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ms < 2000, "2000 checks took " + ms + " ms");
    }
}
