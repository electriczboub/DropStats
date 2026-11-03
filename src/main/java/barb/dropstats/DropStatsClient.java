package barb.dropstats;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class DropStatsClient implements ClientModInitializer {
    private static double chancePercent;
    private static long attempts;
    private static long drops;
    private static DisplayFormat defaultFormat = DisplayFormat.PERCENT;

    @Override
    public void onInitializeClient() {
        DropStatsConfig.load();
        defaultFormat = DropStatsConfig.getFormat();
        attempts = DropStatsConfig.getAttempts();
        drops = DropStatsConfig.getDrops();
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            var processed = TooltipProcessor.process(lines);
            lines.clear();
            lines.addAll(processed);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("dropstats")
                    .executes(ctx -> {
                        reply(ctx.getSource(), status());
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(ClientCommands.literal("help").executes(ctx -> {
                        showHelp(ctx.getSource());
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(ClientCommands.literal("chance")
                            .then(ClientCommands.argument("percent", DoubleArgumentType.doubleArg(0.00000001, 100.0))
                                    .executes(ctx -> {
                                        chancePercent = DoubleArgumentType.getDouble(ctx, "percent");
                                        reply(ctx.getSource(), "Chance set to " + formatPercent(chancePercent)
                                                + " (" + odds(chancePercent) + "). Hover the item to see its stats.");
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .then(ClientCommands.literal("attempts")
                            .then(ClientCommands.argument("count", LongArgumentType.longArg(0))
                                    .executes(ctx -> {
                                        attempts = LongArgumentType.getLong(ctx, "count");
                                        DropStatsConfig.setCounts(attempts, drops);
                                        reply(ctx.getSource(), status());
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .then(ClientCommands.literal("drops")
                            .then(ClientCommands.argument("count", LongArgumentType.longArg(0))
                                    .executes(ctx -> {
                                        drops = LongArgumentType.getLong(ctx, "count");
                                        DropStatsConfig.setCounts(attempts, drops);
                                        reply(ctx.getSource(), status());
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .then(ClientCommands.literal("reset").executes(ctx -> {
                        attempts = 0;
                        drops = 0;
                        DropStatsConfig.setCounts(attempts, drops);
                        reply(ctx.getSource(), "Attempt and drop counts reset.");
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(formatCommand("format")));
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource>
    formatCommand(String command) {
        return ClientCommands.literal(command)
                .then(ClientCommands.literal("percent").executes(ctx -> {
                    setFormat(ctx.getSource(), DisplayFormat.PERCENT);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("fraction").executes(ctx -> {
                    setFormat(ctx.getSource(), DisplayFormat.FRACTION);
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static void setFormat(FabricClientCommandSource source, DisplayFormat format) {
        defaultFormat = format;
        DropStatsConfig.setFormat(format);
        reply(source, "Default chance display set to " + format.commandName + ". Hold Shift to switch it.");
    }

    private static void showHelp(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("DropStats — command guide")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        helpLine(source, "/dropstats", "Show the current chance, attempts, and probability summary.");
        helpLine(source, "/dropstats chance <percent>", "Set a chance manually when no tooltip chance is available.");
        helpLine(source, "/dropstats attempts <count>", "Set how many tries you made.");
        helpLine(source, "/dropstats drops <count>", "Set how many times the item dropped.");
        helpLine(source, "/dropstats format percent|fraction", "Choose the saved tooltip display format.");
        helpLine(source, "/dropstats reset", "Set attempts and drops back to zero.");
        source.sendFeedback(Component.literal("Hold Shift over an item to temporarily switch the chance format.")
                .withStyle(ChatFormatting.GRAY));
        source.sendFeedback(Component.literal("Example: 400 tries at 0.08% gives about 27.4% chance of at least one drop.")
                .withStyle(ChatFormatting.DARK_GRAY));
        source.sendFeedback(Component.literal("The result line reports the chance of getting your drop count or more.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void helpLine(FabricClientCommandSource source, String command, String description) {
        source.sendFeedback(Component.literal("  " + command).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(" — " + description).withStyle(ChatFormatting.GRAY)));
    }

    static void recordTooltipChance(double percent) {
        chancePercent = percent;
    }

    public static DisplayFormat activeFormat() {
        boolean shift = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
        return shift ? defaultFormat.other() : defaultFormat;
    }

    static long attempts() { return attempts; }
    static long drops() { return drops; }

    private static String status() {
        if (chancePercent <= 0) return "Set a chance with /dropstats chance <percent>, then set attempts and drops.";
        return "Chance " + formatPercent(chancePercent) + " (" + odds(chancePercent) + "), " + attempts
                + " attempts, " + drops + " drops. Expected drops: "
                + String.format(java.util.Locale.ROOT, "%.2f", attempts * chancePercent / 100.0)
                + ". Chance of at least one: "
                + formatPercent(ProbabilityMath.atLeastOne(chancePercent / 100.0, attempts) * 100) + ".";
    }

    private static String odds(double percent) {
        double reciprocal = 100.0 / percent;
        return "1 in " + (reciprocal >= 1000 ? String.format(java.util.Locale.ROOT, "%.0f", reciprocal)
                : String.format(java.util.Locale.ROOT, "%.2f", reciprocal));
    }

    private static String formatPercent(double percent) {
        if (percent >= 10) return String.format(java.util.Locale.ROOT, "%.1f%%", percent);
        if (percent >= 0.1) return String.format(java.util.Locale.ROOT, "%.3f%%", percent);
        return String.format(java.util.Locale.ROOT, "%.5f%%", percent);
    }

    private static void reply(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal("[DropStats] ")
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)
                .append(Component.literal(message).withStyle(ChatFormatting.GRAY)));
    }

    public enum DisplayFormat {
        PERCENT("percent") {
            @Override String format(double percent) { return formatPercent(percent); }
        },
        FRACTION("fraction") {
            @Override String format(double percent) { return odds(percent); }
        };

        private final String commandName;
        DisplayFormat(String commandName) { this.commandName = commandName; }
        String commandName() { return commandName; }
        String format(double percent) { return this == PERCENT ? formatPercent(percent) : odds(percent); }
        DisplayFormat other() { return this == PERCENT ? FRACTION : PERCENT; }
    }
}
