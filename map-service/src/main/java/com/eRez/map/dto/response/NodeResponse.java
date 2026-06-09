package com.eRez.map.dto.response;

import com.eRez.map.dto.Position;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class NodeResponse {
    private String name;
    private Position position;
    // key: target node name, value: edge weight
    private Map<String, Integer> connections;
}
