package de.nurrobin.smpstats.database;

public record HeatmapEntry(String type, String world, double x, double y, double z, double value, long timestamp) {}
