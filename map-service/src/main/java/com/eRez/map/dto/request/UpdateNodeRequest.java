package com.eRez.map.dto.request;

import com.eRez.map.dto.Position;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class UpdateNodeRequest {
    private String newName;
    private Position position;

    // key: target node ID, value: edge weight
    private Map<String, Integer> connections;
}
