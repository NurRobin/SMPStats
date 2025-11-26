# Heatmap Analytics Overhaul

## Overview
The heatmap system has been completely rewritten to support dynamic trend analysis, custom time ranges, and flexible decay calculations. Instead of pre-aggregating data into static bins, the system now stores raw events, allowing for powerful on-the-fly analysis.

## Key Features

### Dynamic Aggregation
- **Raw Event Storage**: Heatmap events (block breaks, deaths, movement) are now stored as individual records with timestamps in the `heatmap_events` table.
- **Flexible Querying**: The API now supports querying any time range (e.g., "last 10 minutes", "last 3 weeks") without loss of granularity.
- **Dynamic Decay**: Decay (half-life) is applied at query time, allowing users to adjust how quickly old data "fades" without permanently altering the stored data.

### Player Position Tracking
- **New Event Type**: `POSITION`
- **Tracking**: Player positions are automatically tracked every 5 seconds.
- **Visualization**: Allows visualizing where players spend the most time ("loitering") and how they distribute across the world.

### API Enhancements
The `GET /heatmap/<type>` endpoint has been updated to support advanced filtering:
- `since` (long): Start timestamp in milliseconds.
- `until` (long): End timestamp in milliseconds (defaults to now).
- `decay` (double): Half-life in hours for decay calculation (defaults to config setting).
- `world` (string): World name to filter by (defaults to "world").

**Example**:
```http
GET /heatmap/POSITION?since=1700000000000&decay=24&world=world
```

## Database Changes
- **Schema Version**: Bumped to `7`.
- **New Table**: `heatmap_events` stores raw event data.
- **Dropped Table**: `heatmap_bins` has been removed. **Note**: Old heatmap data is not migrated and will be lost upon update.
- **Hotspots**: Fixed an issue with the `incrementHotspot` SQL query for better conflict handling.

## Configuration
- `heatmap_flush_minutes`: Controls how often raw events are batched and written to the database.
- `heatmap_decay_half_life_hours`: Default decay value used if not specified in the API request.
