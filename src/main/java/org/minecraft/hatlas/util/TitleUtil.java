package org.minecraft.hatlas.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Unified utility for showing title/subtitle overlays to players.
 *
 * <p>Multi-line text is split using {@link #MAX_LINE}-char word-wrap.
 * When wrapping produces two lines the first goes to the main title slot
 * and the remainder to the subtitle slot — no {@code Component.newline()}
 * is ever used, which avoids the "LF" artefact in Minecraft's title packet.
 */
public final class TitleUtil {

    /** Maximum characters per line before a line break is inserted. */
    public static final int MAX_LINE = 16;

    // -------------------------------------------------------------------------
    // Timing presets
    // -------------------------------------------------------------------------

    /** Standard notification: short fade-in, medium stay, short fade-out. */
    public static final Title.Times NOTIFY_TIMES = Title.Times.times(
            Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(300));

    /** Hit / damage feedback: instant appear, very short stay, slow fade-out. */
    public static final Title.Times HIT_TIMES = Title.Times.times(
            Duration.ZERO, Duration.ofMillis(400), Duration.ofMillis(700));

    /** Alert / warning: short fade-in, long stay, short fade-out. */
    public static final Title.Times ALERT_TIMES = Title.Times.times(
            Duration.ofMillis(200), Duration.ofMillis(3000), Duration.ofMillis(400));

    private TitleUtil() {}

    // -------------------------------------------------------------------------
    // Single-player send methods
    // -------------------------------------------------------------------------

    /** Notification (plain text, {@link #NOTIFY_TIMES}). */
    public static void notify(Player player, String text, NamedTextColor color) {
        send(player, text, color, false, NOTIFY_TIMES);
    }

    /** Notification (bold, {@link #NOTIFY_TIMES}). */
    public static void notifyBold(Player player, String text, NamedTextColor color) {
        send(player, text, color, true, NOTIFY_TIMES);
    }

    /**
     * Hit / damage feedback (bold, {@link #HIT_TIMES}).
     * Text is always shown in the subtitle slot only — no word-wrap is applied
     * because damage strings are inherently short.
     */
    public static void hitFeedback(Player player, String text, NamedTextColor color) {
        player.showTitle(Title.title(
                Component.empty(),
                Component.text(text, color).decorate(TextDecoration.BOLD),
                HIT_TIMES));
    }

    /** Alert (plain text, {@link #ALERT_TIMES}). */
    public static void alert(Player player, String text, NamedTextColor color) {
        send(player, text, color, false, ALERT_TIMES);
    }

    /** Alert (bold, {@link #ALERT_TIMES}). */
    public static void alertBold(Player player, String text, NamedTextColor color) {
        send(player, text, color, true, ALERT_TIMES);
    }

    // -------------------------------------------------------------------------
    // Broadcast helpers
    // -------------------------------------------------------------------------

    /** Sends a plain notification to every player in {@code players}. */
    public static void broadcast(Collection<? extends Player> players, String text, NamedTextColor color) {
        for (Player p : players) notify(p, text, color);
    }

    /** Sends a bold notification to every player in {@code players}. */
    public static void broadcastBold(Collection<? extends Player> players, String text, NamedTextColor color) {
        for (Player p : players) notifyBold(p, text, color);
    }

    /** Sends a bold alert to every player in {@code players}. */
    public static void broadcastAlertBold(Collection<? extends Player> players, String text, NamedTextColor color) {
        for (Player p : players) alertBold(p, text, color);
    }

    /**
     * Always shows {@code text} in the <em>subtitle</em> slot only — never in the title slot —
     * regardless of text length. Uses {@link #NOTIFY_TIMES}.
     */
    public static void subtitle(Player player, String text, NamedTextColor color) {
        player.showTitle(Title.title(
                Component.empty(),
                Component.text(text, color),
                NOTIFY_TIMES));
    }

    /** Sends a subtitle-only notification to every player in {@code players}. */
    public static void broadcastSubtitle(Collection<? extends Player> players, String text, NamedTextColor color) {
        for (Player p : players) subtitle(p, text, color);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Splits {@code text} via word-wrap, then shows it using Minecraft's native
     * title/subtitle slots so no {@code \n} is ever embedded in a Component:
     * <ul>
     *   <li>Single line  → empty title slot, text in subtitle slot.</li>
     *   <li>Two+ lines   → first line in title slot, rest joined in subtitle slot.</li>
     * </ul>
     */
    private static void send(Player player, String text, NamedTextColor color, boolean bold, Title.Times times) {
        Component titleComp;
        Component subtitleComp;

        int nl = text.indexOf('\n');
        if (nl >= 0) {
            // Explicit split: everything before \n → title slot, everything after → subtitle slot
            titleComp    = styled(text.substring(0, nl), color, bold);
            subtitleComp = styled(text.substring(nl + 1), color, bold);
        } else {
            List<String> lines = wordWrap(text);
            if (lines.size() == 1) {
                titleComp    = Component.empty();
                subtitleComp = styled(lines.getFirst(), color, bold);
            } else {
                titleComp    = styled(lines.getFirst(), color, bold);
                subtitleComp = styled(String.join(" ", lines.subList(1, lines.size())), color, bold);
            }
        }

        player.showTitle(Title.title(titleComp, subtitleComp, times));
    }

    private static Component styled(String text, NamedTextColor color, boolean bold) {
        Component c = Component.text(text, color);

        return bold ? c.decorate(TextDecoration.BOLD) : c;
    }

    /**
     * Splits {@code text} on spaces so that no line exceeds {@link #MAX_LINE} chars.
     * A single word longer than the limit is placed on its own line unchanged.
     */
    static List<String> wordWrap(String text) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ", -1);
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.isEmpty()) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= MAX_LINE) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }

        if (!current.isEmpty()) lines.add(current.toString());
        if (lines.isEmpty()) lines.add("");

        return lines;
    }
}
