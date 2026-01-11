package com.stufy.fragmc.icedspear.models;

import java.util.UUID;

public class LeaderboardEntry {
    private final UUID playerId;
    private final String playerName;
    private final long time; // Time in milliseconds
    private final long timestamp; // When this time was achieved

    public LeaderboardEntry(UUID playerId, String playerName, long time, long timestamp) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.time = time;
        this.timestamp = timestamp;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getTime() {
        return time;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Format the time in a readable way
     */
    public String getFormattedTime() {
        long totalSeconds = time / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = (time % 1000) / 10;

        if (minutes > 0) {
            return String.format("%d:%02d.%02d", minutes, seconds, millis);
        } else {
            return String.format("%d.%02d", seconds, millis);
        }
    }
}