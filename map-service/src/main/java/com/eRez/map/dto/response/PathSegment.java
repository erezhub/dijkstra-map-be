package com.eRez.map.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PathSegment {
    private String fromId;
    private String from;
    private String toId;
    private String to;
    private int distance;
}
