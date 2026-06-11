package com.eRez.map.dto.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RouteRecalculatedEvent {
    private String nodeA;
    private String nodeB;
    private int distance;
    private List<String> recipients;
}
