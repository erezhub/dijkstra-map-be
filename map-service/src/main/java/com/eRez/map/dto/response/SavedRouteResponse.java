package com.eRez.map.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SavedRouteResponse {
    private final String nodeA;
    private final String nodeB;
    private final int distance;
    private final List<PathSegment> path;
    private final List<String> createdBy;
}
