package com.stufy.fragmc.icedspear.models;

import java.util.*;

public class Party {
    private final String code;
    private final UUID leader;
    private final Set<UUID> members;
    private String currentMap;

    public Party(String code, UUID leader) {
        this.code = code;
        this.leader = leader;
        this.members = new HashSet<>();
        this.members.add(leader);
    }

    public String getCode() {
        return code;
    }

    public UUID getLeader() {
        return leader;
    }

    public Set<UUID> getMembers() {
        return new HashSet<>(members);
    }

    public void addMember(UUID playerId) {
        members.add(playerId);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    public String getCurrentMap() {
        return currentMap;
    }

    public void setCurrentMap(String currentMap) {
        this.currentMap = currentMap;
    }
}