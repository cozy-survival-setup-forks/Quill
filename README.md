# Quill

Chat for a survival server: local chat, formats, the usual extras, and a filter for advertising and hate that is built
to stop the real thing and nothing else. Private messages, ignoring and muting are left to Essentials.

## What it does

- **Local chat.** Messages go to everyone. `/localchat <message>` is heard only by players within a radius (64 blocks by
  default) in the same world, and `/localchat` alone switches your own chat between local and everyone (`quill.chat.local`,
  everyone by default). Staff switch on `/quill spy` to hear local messages from anywhere in a spy format.
- **Formats.** MiniMessage, one per permission and weight, separate local and global formats. Name, prefix and suffix
  come from placeholders, so LuckPerms prefixes and Spectrum name gradients just work. Hover and click on the name.
- **Extras.** `[item]` shows the item in hand with its real hover, `[inv]` and `[ender]` open a read-only copy,
  `@name` highlights and pings a player, links that passed the filter are clickable. The inventory copy has the
  armor and off hand on the top row, the bag in the three rows below, then a divider row and the hotbar on the
  last row; unused slots are nameless, tooltip-less panes, and nothing in the window can be taken or moved.
- **Staff chat.** `/staffchat <message>`, `/staffchat` to stay in it, or start a message with `#`.
- **Controls.** `/chattoggle` hides public chat for yourself, `/quill mutechat` and `/quill clearchat` for staff.
- **Announcements.** A timed list, off by default.
- **Spam.** Cooldown, repeated message and shouting. They stop the message with a short note and never count as warnings.

## The filter

A stopped message is cancelled before anyone sees it and the player gets a warning. Staff see who was stopped and can
hover for the message and the exact rule. Everything is written to `filter.log`.

It is built around not stopping innocent messages:

- Words match as **whole words**, so "class", "assassin", "Scunthorpe" and "Niger" are never a hit. Plurals and
  forms are listed, not guessed.
- It sees through the usual disguises: capitals, accents, full-width and look-alike letters from other alphabets,
  invisible characters, l33t (`n1gger`), stretched letters (`niiigger`), letters typed one at a time (`n i g g e r`,
  `n.i.g.g.e.r`), a word broken by a space or a symbol (`nig ger`, `nig'ger`) and masked letters (`f*ck`). It never
  makes a shorter word longer, so "niger" stays a country.
- The name, lore and contents of an item shown with `[item]` are checked like any other text.
- A few long, unmistakable words are also matched inside a longer one, minus a safe list ("snigger").
- Words with an innocent meaning have safe phrases ("chink in the armor", "Maine Coon", "spick and span").
- Online player names are never a hit.
- **Advertising**: links, domains, server addresses and phrases like "join my server". A bare `name.ending` counts only
  for endings that ads really use and names of three letters or more, so "ok.so" and "gg.wp" are fine. IP-like
  version numbers and `127.0.0.1` are ignored. Links to YouTube, Imgur, the Minecraft wiki and the like are allowed
  (`allowed-domains`), and `quill.links` allows any link.
- Categories: racism, homophobia, ableism, hate speech, encouraging self-harm, advertising. Sexual content and
  profanity are in the lists but off. Turn each on or off in `terms.yml`.
- The tests run the shipped lists against hundreds of normal sentences and disguised slurs. `/quill filter test
  <text>` shows what the filter does with any text and why, which is the way to tune it.
- Warnings fade after an hour. `filter.yml` can run commands at a number of warnings (mute, tempban), nothing by default.

## Files

| File | What it holds |
| --- | --- |
| `config.yml` | Local chat, formats, name and prefix placeholders, extras, announcements |
| `filter.yml` | Advertising, spam, warnings and what happens at a number of warnings |
| `terms.yml` | The word lists, phrases, patterns and safe lists |
| `messages.yml` | Every message |

## Works with

- **Spectrum.** The filter runs early and cancels, Spectrum skips cancelled messages. The format runs late, so a chat
  colour or gradient applied by Spectrum is kept even letter by letter, and `%spectrum_name%` is the default name.
- **Bastion, BetterTeams and other plugins that own the look of a message.** If another plugin has already set
  the renderer (dungeon chat, team chat), Quill leaves its format and range alone. The filter still applies.
- **Essentials.** Its `/ignore` is respected. Do not run EssentialsXChat next to Quill.
- **PlaceholderAPI** (optional), **LuckPerms** (through PlaceholderAPI). `%quill_warnings%`, `%quill_spy%`,
  `%quill_local%`, `%quill_muted%`, `%quill_chat_hidden%`.
- Other plugins can listen for `QuillFilterEvent`.

## Permissions

`quill.admin`, `quill.staffchat`, `quill.chat.local` (everyone), `quill.chat.item` `.inventory` `.ender` `.mention` (everyone),
`quill.links` (op), `quill.filter.alerts`, `quill.bypass.filter` and `quill.bypass.filter.<category>`,
`quill.bypass.spam`, `quill.bypass.chatmuted`, `quill.bypass.chatclear`, `quill.bypass.chattoggle`, `quill.bypass.ignore`,
`quill.ignore.mentions`, `quill.announcements.bypass`.

## Telemetry

On startup Quill sends a small anonymous beacon (plugin name/version, server software/version,
online/max player counts, and a random ID with no player data) so we know which versions are in
use. Turn it off with `metrics.enabled: false` in `config.yml`.

## Building

```
./gradlew build
```

The jar is in `build/libs`. See `LICENSE`: free to run on your own servers, not for redistribution or resale.
