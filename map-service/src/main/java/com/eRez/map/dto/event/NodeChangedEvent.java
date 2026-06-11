package com.eRez.map.dto.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodeChangedEvent {
    private String type;      // "NODE_ADDED", "NODE_UPDATED", "NODE_DELETED_INTERMEDIATE"
    private String nodeName;
}
