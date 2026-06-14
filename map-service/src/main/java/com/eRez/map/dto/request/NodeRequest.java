package com.eRez.map.dto.request;

import com.eRez.map.dto.Position;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class NodeRequest {
    @NotNull(message = "Node name cannot be null")
    @NotEmpty(message = "Node name cannot be empty")
    @NotBlank(message = "Node name cannot be blank")
    @Size(max = 50, message = "Node name must be 50 characters or fewer")
    private String name;

    private Position position;

    // key: target node name, value: edge weight
    @NotNull(message = "Connections cannot be null")
    private Map<String, Integer> connections;
}
