package com.eRez.tests.dto.response;

import com.eRez.tests.dto.Position;
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
