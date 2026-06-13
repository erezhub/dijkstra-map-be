package com.eRez.map.dto.request;

import com.eRez.map.dto.Position;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class AddNodeRequest {
    @NotNull(message = "Node name cannot be null")
    @NotBlank(message = "Node name cannot be blank")
    private String name;

    private Position position;

    // key: existing node ID, value: edge weight
    @NotNull(message = "Connections cannot be null")
    private Map<String, Integer> connections;
}