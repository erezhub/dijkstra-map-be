package com.eRez.tests.dto.request;

import lombok.Getter;

import java.util.Map;

@Getter
public class UpdateNodeRequest {
    // key: target node name, value: edge weight
    private Map<String, Integer> connections;
}
