package com.eRez.notification.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class RouteRecalculatedEvent {
    private String nodeA;
    private String nodeB;
    private int distance;
    private List<String> recipients;
}