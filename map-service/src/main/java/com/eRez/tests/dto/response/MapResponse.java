package com.eRez.tests.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MapResponse {
    private List<NodeResponse> nodes;
}
