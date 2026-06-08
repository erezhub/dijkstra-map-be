package com.eRez.tests.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class NodeResponse {
    private String name;
    // key: target node name, value: edge weight
    private Map<String, Integer> connections;
}
