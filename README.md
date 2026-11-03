# DropStats

Standalone client-side Fabric mod for adding probability summaries to SkyBlock item tooltips.

## Current first build

The tooltip processor recognizes percentages on lines labelled `Odds:` or `Drop chance:` at the final tooltip-render stage, so it can also see chance lines appended by recipe viewers. Fraction mode replaces every percentage on an `Odds:` line, including struck-through base chances, while preserving the original text formatting. Hold Shift to temporarily switch between percentage and fraction display. Once counts are entered, it also adds the chance of at least one drop and the chance of seeing the recorded number of drops or more.

In-game guide: `/dropstats help`.

Commands:

- `/dropstats` — print the current summary
- `/dropstats chance <percent>` — set a manual chance for command calculations
- `/dropstats attempts <count>` — set the attempt count
- `/dropstats drops <count>` — set the observed drop count
- `/dropstats format percent|fraction` — choose the persistent default display
- `/dropstats reset` — clear attempts and drops

Example: `/dropstats chance 0.08`, `/dropstats attempts 400`, and `/dropstats drops 1` summarize a 400-attempt run at 0.08%. The tooltip reports the chance of at least one drop and the chance of one or more drops in that many attempts. `Shift` temporarily switches the tooltip chance format; the selected default is saved.

Settings and counts are saved in `config/dropstats.properties`. Per-attempt probability is treated as constant in the calculation; this does not model a changing RNG meter yet.

## Build

From this folder, run `./gradlew build`. The standalone jar is written under `build/libs/`.
