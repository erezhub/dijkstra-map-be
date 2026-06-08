package com.eRez.tests.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class MapNode {
    String id;
    @Setter Map<MapNode, Integer> connections;
    @Setter MapNode shortestPath;
    @Setter Position position;

    public MapNode(String id) {
        this.id = id;
        connections = new HashMap<>();
    }
}
