package com.eRez.tests.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class UpdateNodeRequest {
    // key: target node name, value: edge weight
    private Map<String, Integer> connections;
}
