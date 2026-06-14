package com.eRez.map.dto.request;

import com.eRez.map.dto.Position;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class UpdateNodeRequest {
    @Size(max = 50, message = "Node name must be 50 characters or fewer")
    private String newName;
    private Position position;

    // key: target node ID, value: edge weight
    private Map<String, Integer> connections;
}
