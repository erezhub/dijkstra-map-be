package com.eRez.map.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PathResponse {
    private int distance;
    private List<PathSegment> path;
}
