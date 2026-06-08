package com.eRez.tests.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NonNull;

import java.util.Map;

@Getter
public class NodeRequest {
    @NotNull(message = "Node name cannot be null")
    @NotEmpty(message = "Node name cannot be empty")
    @NotBlank(message = "Node name cannot be blank")
    private String name;

    // key: target node name, value: edge weight
    @NotNull(message = "Connections cannot be null")
    private Map<String, Integer> connections;
}
