package com.eRez.map.dto.request;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateMapRequest {
    @Valid
    private List<NodeRequest> nodes;
}
