package com.eRez.map.database.document;

import com.eRez.common.data.Auditable;
import com.eRez.map.dto.Position;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Getter
@Setter
@Document(collection = "nodes")
public class NodeDocument implements Auditable {

    @Id
    private String id;

    private String name;

    private Position position;

    // key: target node id, value: edge weight
    private Map<String, Integer> connections;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Override
    public void onBeforeSave() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
}
