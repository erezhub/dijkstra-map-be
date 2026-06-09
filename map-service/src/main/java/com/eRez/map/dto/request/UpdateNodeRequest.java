package com.eRez.map.dto.request;

import com.eRez.map.dto.Position;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class UpdateNodeRequest {
    private Position position;

    // key: target node name, value: edge weight
    private Map<String, Integer> connections;
}
