package com.eRez.map.services;

import com.eRez.map.data.CacheData;
import com.eRez.map.database.document.NodeDocument;
import com.eRez.map.dto.MapNode;
import com.eRez.map.dto.response.PathResponse;
import com.eRez.map.dto.response.PathSegment;
import com.eRez.map.exception.MapException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PathService {

    private final CacheData cacheData;

    public PathResponse getPath(String fromId, String toId) {
        List<NodeDocument> allDocs = cacheData.getNodes();

        NodeDocument fromDoc = allDocs.stream()
                .filter(d -> fromId.equals(d.getId()))
                .findFirst()
                .orElseThrow(() -> new MapException("Node not found: " + fromId));
        NodeDocument toDoc = allDocs.stream()
                .filter(d -> toId.equals(d.getId()))
                .findFirst()
                .orElseThrow(() -> new MapException("Node not found: " + toId));

        log.info("Finding path from '{}' to '{}'", fromDoc.getName(), toDoc.getName());

        Map<String, MapNode> graph = buildGraph(allDocs);

        Map<String, Integer> distances = new HashMap<>();
        Set<MapNode> visited = new HashSet<>();
        distances.put(fromDoc.getId(), 0);

        PriorityQueue<MapNode> pq = new PriorityQueue<>(Comparator.comparingInt(n -> distances.get(n.getId())));
        pq.offer(graph.get(fromDoc.getId()));

        MapNode toNode = runDijkstra(graph.get(fromDoc.getId()), toDoc.getId(), distances, visited, pq);
        PathResponse response = buildPathResponse(toNode, distances);
        log.info("Path found from '{}' to '{}': distance={}, hops={}", fromDoc.getName(), toDoc.getName(), response.getDistance(), response.getPath().size());
        return response;
    }

    private Map<String, MapNode> buildGraph(List<NodeDocument> documents) {
        Map<String, MapNode> graph = new HashMap<>();

        for (NodeDocument doc : documents) {
            graph.put(doc.getId(), new MapNode(doc.getId()));
        }

        for (NodeDocument doc : documents) {
            MapNode node = graph.get(doc.getId());
            Map<MapNode, Integer> connections = new HashMap<>();
            doc.getConnections().forEach((nodeId, weight) -> {
                MapNode target = graph.get(nodeId);
                if (target != null) {
                    connections.put(target, weight);
                }
            });
            node.setConnections(connections);
        }

        return graph;
    }

    private MapNode runDijkstra(MapNode from, String toId,
                                Map<String, Integer> distances,
                                Set<MapNode> visited,
                                PriorityQueue<MapNode> pq) {
        if (from.getId().equals(toId)) {
            return from;
        }

        visited.add(from);
        String fromId = from.getId();
        from.getConnections().forEach((mapNode, weight) -> {
            String id = mapNode.getId();
            int newDist = distances.get(fromId) + weight;
            if (!distances.containsKey(id)) {
                distances.put(id, newDist);
                mapNode.setShortestPath(from);
                pq.offer(mapNode);
            } else if (distances.get(id) > newDist) {
                distances.put(id, newDist);
                mapNode.setShortestPath(from);
                pq.offer(mapNode);
            }
        });

        MapNode nextNode = findNextNode(visited, pq);
        if (nextNode == null) {
            Map<String, String> idToName = cacheData.getIdToName();
            throw new MapException(String.format("No path exists from '%s' to '%s'",
                    idToName.getOrDefault(fromId, fromId), idToName.getOrDefault(toId, toId)));
        }
        return runDijkstra(nextNode, toId, distances, visited, pq);
    }

    private PathResponse buildPathResponse(MapNode toNode, Map<String, Integer> distances) {
        Map<String, String> idToName = cacheData.getIdToName();
        List<PathSegment> segments = new ArrayList<>();
        MapNode current = toNode;
        while (current.getShortestPath() != null) {
            MapNode prev = current.getShortestPath();
            int segmentDist = distances.get(current.getId()) - distances.get(prev.getId());
            segments.add(0, new PathSegment(
                    prev.getId(), idToName.get(prev.getId()),
                    current.getId(), idToName.get(current.getId()),
                    segmentDist));
            current = prev;
        }
        return new PathResponse(distances.get(toNode.getId()), segments);
    }

    private MapNode findNextNode(Set<MapNode> visited, PriorityQueue<MapNode> pq) {
        while (!pq.isEmpty()) {
            MapNode candidate = pq.poll();
            if (!visited.contains(candidate)) return candidate;
        }
        return null;
    }
}