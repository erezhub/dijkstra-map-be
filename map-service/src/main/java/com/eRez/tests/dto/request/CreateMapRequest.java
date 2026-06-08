package com.eRez.tests.dto.request;

import jakarta.validation.Valid;
import lombok.Getter;

import java.util.List;

@Getter
public class CreateMapRequest {
    @Valid
    private List<NodeRequest> nodes;
}
