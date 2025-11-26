package de.nurrobin.smpstats.health;

/**
 * Configurable thresholds for health metrics to determine color coding in charts.
 * Each threshold defines boundaries for: good, acceptable, warning, critical
 */
public class HealthThresholds {
    
    /**
     * Thresholds for a single metric.
     * For "higher is better" metrics (like TPS), values above good are green, below critical are red.
     * For "lower is better" metrics (like entities), values below good are green, above critical are red.
     */
    public record MetricThreshold(double good, double acceptable, double warning, double critical, boolean higherIsBetter) {
        
        /**
         * Returns a quality level (0.0 = worst, 1.0 = best) based on the value.
         */
        public double getQuality(double value) {
            if (higherIsBetter) {
                // Higher values are better (e.g., TPS)
                if (value >= good) return 1.0;
                if (value >= acceptable) return 0.75;
                if (value >= warning) return 0.5;
                if (value >= critical) return 0.25;
                return 0.0;
            } else {
                // Lower values are better (e.g., entity count)
                if (value <= good) return 1.0;
                if (value <= acceptable) return 0.75;
                if (value <= warning) return 0.5;
                if (value <= critical) return 0.25;
                return 0.0;
            }
        }
        
        /**
         * Returns a descriptive status string.
         */
        public String getStatus(double value) {
            double quality = getQuality(value);
            if (quality >= 1.0) return "Good";
            if (quality >= 0.75) return "Acceptable";
            if (quality >= 0.5) return "Warning";
            if (quality >= 0.25) return "Bad";
            return "Critical";
        }
    }
    
    private final MetricThreshold tps;
    private final MetricThreshold memory;
    private final MetricThreshold chunks;
    private final MetricThreshold entities;
    private final MetricThreshold hoppers;
    private final MetricThreshold redstone;
    private final MetricThreshold costIndex;
    
    public HealthThresholds(MetricThreshold tps, MetricThreshold memory, MetricThreshold chunks,
                            MetricThreshold entities, MetricThreshold hoppers, MetricThreshold redstone,
                            MetricThreshold costIndex) {
        this.tps = tps;
        this.memory = memory;
        this.chunks = chunks;
        this.entities = entities;
        this.hoppers = hoppers;
        this.redstone = redstone;
        this.costIndex = costIndex;
    }
    
    public MetricThreshold getTps() { return tps; }
    public MetricThreshold getMemory() { return memory; }
    public MetricThreshold getChunks() { return chunks; }
    public MetricThreshold getEntities() { return entities; }
    public MetricThreshold getHoppers() { return hoppers; }
    public MetricThreshold getRedstone() { return redstone; }
    public MetricThreshold getCostIndex() { return costIndex; }
    
    /**
     * Returns default thresholds suitable for most servers.
     */
    public static HealthThresholds defaults() {
        return new HealthThresholds(
                // TPS: higher is better
                new MetricThreshold(19.0, 18.0, 15.0, 10.0, true),
                // Memory %: lower is better
                new MetricThreshold(50, 70, 85, 95, false),
                // Chunks: lower is better
                new MetricThreshold(500, 1000, 2000, 4000, false),
                // Entities: lower is better
                new MetricThreshold(500, 1500, 3000, 5000, false),
                // Hoppers: lower is better
                new MetricThreshold(200, 500, 1000, 2000, false),
                // Redstone: lower is better
                new MetricThreshold(500, 1000, 2000, 5000, false),
                // Cost Index: lower is better (0-100)
                new MetricThreshold(25, 50, 75, 90, false)
        );
    }
}
