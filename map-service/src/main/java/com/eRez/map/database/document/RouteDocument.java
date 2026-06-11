package com.eRez.map.database.document;

import com.eRez.common.data.Auditable;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Getter
@Setter
@Document(collection = "routes")
public class RouteDocument implements Auditable {

    @Id
    private String id;

    private String nodeA;
    private String nodeB;
    private List<String> path;              // ordered node names, nodeA → nodeB inclusive
    private List<Integer> segmentDistances; // per-hop distances matching path gaps
    private int distance;
    private boolean stale;
    private List<String> createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public void onBeforeSave() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
}
