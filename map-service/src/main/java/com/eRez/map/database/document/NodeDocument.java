package com.eRez.map.database.document;

import com.eRez.map.dto.Position;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Getter
@Setter
@Document(collection = "nodes")
public class NodeDocument {

    @Id
    private String id;

    private String name;

    private Position position;

    // key: target node id, value: edge weight
    private Map<String, Integer> connections;
}
