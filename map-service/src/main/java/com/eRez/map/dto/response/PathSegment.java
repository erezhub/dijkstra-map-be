package com.eRez.map.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PathSegment {
    private String from;
    private String to;
    private int distance;
}
