package barb.dropstats;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs at tooltip submission time so it sees lines added by recipe viewers after Fabric callbacks. */
public final class TooltipProcessor {
    private static final Pattern PERCENT = Pattern.compile("(?<![\\d.])(\\d+(?:\\.\\d+)?)\\s*%");

    private TooltipProcessor() {}

    /** Rewrites raw SkyRecipes chance text before it is attached to an RRV slot tooltip. */
    public static String formatChanceText(String original) {
        if (DropStatsClient.activeFormat() != DropStatsClient.DisplayFormat.FRACTION) return original;
        Matcher matcher = PERCENT.matcher(original);
        StringBuffer formatted = new StringBuffer();
        while (matcher.find()) {
            double value;
            try {
                value = Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            matcher.appendReplacement(formatted, Matcher.quoteReplacement(odds(value)));
        }
        matcher.appendTail(formatted);
        return formatted.toString();
    }

    public static List<Component> process(List<Component> original) {
        List<Component> tooltip = new ArrayList<>(original);
        List<Component> stats = new ArrayList<>();
        for (int i = 0; i < tooltip.size(); i++) {
            Component line = tooltip.get(i);
            String text = line.getString();
            String lower = text.toLowerCase(Locale.ROOT);
            boolean oddsLine = lower.contains("odds:");
            if (!oddsLine && !lower.contains("drop chance")) continue;

            Matcher matcher = PERCENT.matcher(text);
            List<String> matches = new ArrayList<>();
            double currentChance = Double.NaN;
            while (matcher.find()) {
                try {
                    currentChance = Double.parseDouble(matcher.group(1));
                    matches.add(matcher.group());
                } catch (NumberFormatException ignored) {
                    // Ignore malformed percentages while retaining other valid matches.
                }
            }
            if (matches.isEmpty() || !Double.isFinite(currentChance)
                    || currentChance <= 0 || currentChance > 100) continue;

            DropStatsClient.recordTooltipChance(currentChance);
            if (DropStatsClient.activeFormat() == DropStatsClient.DisplayFormat.FRACTION) {
                Component changed = line;
                // Replace all percent values on an Odds line too, including its crossed-out base rate.
                for (int m = matches.size() - 1; m >= 0; m--) {
                    double value = parsePercent(matches.get(m));
                    changed = replaceLast(changed, matches.get(m), odds(value));
                }
                tooltip.set(i, changed);
            }

            long attempts = DropStatsClient.attempts();
            if (attempts > 0) {
                double p = currentChance / 100.0;
                double atLeastOne = ProbabilityMath.atLeastOne(p, attempts);
                stats.add(Component.literal("After " + String.format(Locale.ROOT, "%,d", attempts)
                                + " attempts: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(formatPercent(atLeastOne * 100))
                                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                        .append(Component.literal(" chance of ≥1 drop").withStyle(ChatFormatting.GRAY)));
                long drops = DropStatsClient.drops();
                if (drops > 0) {
                    double tail = ProbabilityMath.atLeast(drops, attempts, p);
                    stats.add(Component.literal("At least " + String.format(Locale.ROOT, "%,d", drops)
                                    + " drops: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(formatPercent(tail * 100))
                                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                            .append(Component.literal(" chance (this many or more)")
                                    .withStyle(ChatFormatting.GRAY)));
                }
            }
            break;
        }
        tooltip.addAll(stats);
        return tooltip;
    }

    private static double parsePercent(String formatted) {
        Matcher matcher = PERCENT.matcher(formatted);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0;
    }

    private static Component replaceLast(Component original, String search, String replacement) {
        List<Component> fragments = original.toFlatList();
        StringBuilder combined = new StringBuilder();
        List<StyledFragment> styled = new ArrayList<>(fragments.size());
        for (Component fragment : fragments) {
            String text = fragment.getString();
            int start = combined.length();
            combined.append(text);
            styled.add(new StyledFragment(text, fragment.getStyle(), start, combined.length()));
        }
        int start = combined.lastIndexOf(search);
        if (start < 0) return original;
        int end = start + search.length();
        MutableComponent result = Component.empty();
        boolean replacementAdded = false;
        for (StyledFragment fragment : styled) {
            if (fragment.end <= start) {
                append(result, fragment.text, fragment.style);
                continue;
            }
            if (fragment.start >= end) {
                if (!replacementAdded) {
                    append(result, replacement, fragment.style);
                    replacementAdded = true;
                }
                append(result, fragment.text, fragment.style);
                continue;
            }
            int localStart = Math.max(0, start - fragment.start);
            int localEnd = Math.min(fragment.text.length(), end - fragment.start);
            append(result, fragment.text.substring(0, localStart), fragment.style);
            if (!replacementAdded) {
                append(result, replacement, fragment.style);
                replacementAdded = true;
            }
            append(result, fragment.text.substring(localEnd), fragment.style);
        }
        return result;
    }

    private static void append(MutableComponent target, String text, Style style) {
        if (!text.isEmpty()) target.append(Component.literal(text).withStyle(style));
    }

    private static String odds(double percent) {
        double reciprocal = 100.0 / percent;
        return "1 in " + (reciprocal >= 1000 ? String.format(Locale.ROOT, "%.0f", reciprocal)
                : String.format(Locale.ROOT, "%.2f", reciprocal));
    }

    private static String formatPercent(double percent) {
        if (percent >= 10) return String.format(Locale.ROOT, "%.1f%%", percent);
        if (percent >= 0.1) return String.format(Locale.ROOT, "%.3f%%", percent);
        return String.format(Locale.ROOT, "%.5f%%", percent);
    }

    private record StyledFragment(String text, Style style, int start, int end) {}
}
