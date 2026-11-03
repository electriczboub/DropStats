package barb.dropstats;

final class ProbabilityMath {
    private ProbabilityMath() {}

    static double atLeastOne(double probability, long attempts) {
        if (attempts <= 0 || probability <= 0) return 0;
        if (probability >= 1) return 1;
        return -Math.expm1(attempts * Math.log1p(-probability));
    }

    /** Binomial upper-tail probability; exact recurrence for modest n, normal approximation beyond. */
    static double atLeast(long successes, long attempts, double probability) {
        if (successes <= 0) return 1;
        if (successes > attempts || attempts <= 0 || probability <= 0) return 0;
        if (probability >= 1) return 1;
        double mean = attempts * probability;
        double variance = mean * (1.0 - probability);
        if (attempts > 200_000 || variance > 10_000) {
            double z = (successes - 0.5 - mean) / Math.sqrt(variance);
            return clamp(0.5 * erfc(z / Math.sqrt(2.0)));
        }

        double p0 = Math.exp(attempts * Math.log1p(-probability));
        if (p0 == 0.0) {
            double z = (successes - 0.5 - mean) / Math.sqrt(variance);
            return clamp(0.5 * erfc(z / Math.sqrt(2.0)));
        }
        double mass = p0;
        double tail = successes == 0 ? p0 : 0;
        for (long k = 1; k <= attempts; k++) {
            mass *= ((attempts - k + 1.0) / k) * (probability / (1.0 - probability));
            if (k >= successes) tail += mass;
        }
        return clamp(tail);
    }

    private static double clamp(double x) { return Math.max(0, Math.min(1, x)); }

    // Numerical Recipes approximation, sufficient for a descriptive percentile display.
    private static double erfc(double x) {
        double z = Math.abs(x);
        double t = 1.0 / (1.0 + 0.5 * z);
        double ans = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196
                + t * (0.09678418 + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398
                + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? ans : 2.0 - ans;
    }
}
