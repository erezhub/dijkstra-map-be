package com.eRez.tests.services;

import com.eRez.tests.data.CacheData;
import com.eRez.tests.database.document.NodeDocument;
import com.eRez.tests.database.repository.NodeRepository;
import com.eRez.tests.dto.request.CreateMapRequest;
import com.eRez.tests.dto.request.NodeRequest;
import com.eRez.tests.dto.request.UpdateNodeRequest;
import com.eRez.tests.dto.response.MapResponse;
import com.eRez.tests.dto.response.NodeResponse;
import com.eRez.tests.exception.MapException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeService {

    private final NodeRepository nodeRepository;
    private final CacheData cacheData;

    public MapResponse getMap() {
        List<NodeDocument> allDocs = cacheData.getNodes();
        Map<String, String> idToName = allDocs.stream()
                .collect(Collectors.toMap(NodeDocument::getId, NodeDocument::getName));

        List<NodeResponse> nodes = allDocs.stream()
                .map(doc -> {
                    Map<String, Integer> connections = new HashMap<>();
                    doc.getConnections().forEach((targetId, weight) -> {
                        String targetName = idToName.get(targetId);
                        if (targetName != null) {
                            connections.put(targetName, weight);
                        }
                    });
                    return new NodeResponse(doc.getName(), connections);
                })
                .collect(Collectors.toList());

        return new MapResponse(nodes);
    }

    public void createMap(CreateMapRequest request) {
        validateCreateMapRequest(request);
        log.info("Creating map with {} nodes", request.getNodes().size());
        Map<String, String> nameToId = new HashMap<>();
        for (NodeRequest node : request.getNodes()) {
            nameToId.put(node.getName(), UUID.randomUUID().toString());
        }

        List<NodeDocument> documents = new ArrayList<>();
        for (NodeRequest node : request.getNodes()) {
            NodeDocument doc = new NodeDocument();
            doc.setId(nameToId.get(node.getName()));
            doc.setName(node.getName());
            Map<String, Integer> connections = new HashMap<>();
            node.getConnections().forEach((targetName, weight) ->
                connections.put(nameToId.get(targetName), weight)
            );
            doc.setConnections(connections);
            documents.add(doc);
        }

        nodeRepository.deleteAll();
        nodeRepository.saveAll(documents);
        cacheData.refresh();
        log.info("Map created successfully with {} nodes", documents.size());
    }

    private void validateCreateMapRequest(CreateMapRequest request) {
        Map<String, Map<String, Integer>> nodeMap = request.getNodes().stream()
                .collect(Collectors.toMap(NodeRequest::getName, NodeRequest::getConnections));

        for (NodeRequest node : request.getNodes()) {
            for (Map.Entry<String, Integer> entry : node.getConnections().entrySet()) {
                String targetName = entry.getKey();
                int weight = entry.getValue();

                Map<String, Integer> targetConnections = nodeMap.get(targetName);
                if (targetConnections == null) {
                    throw new MapException(
                            String.format("Node '%s' connects to unknown node '%s'", node.getName(), targetName));
                }

                Integer reverseWeight = targetConnections.get(node.getName());
                if (reverseWeight == null) {
                    throw new MapException(
                            String.format("Connection '%s' → '%s' has no reverse", node.getName(), targetName));
                }

                if (!reverseWeight.equals(weight)) {
                    throw new MapException(
                            String.format("Weight mismatch: '%s' → '%s' is %d but '%s' → '%s' is %d",
                                    node.getName(), targetName, weight, targetName, node.getName(), reverseWeight));
                }
            }
        }
    }

    public void addNode(NodeRequest request) {
        log.info("Adding node '{}'", request.getName());
        boolean exists = cacheData.getNodes().stream()
                .anyMatch(d -> request.getName().equals(d.getName()));
        if (exists) {
            throw new MapException("Node already exists: " + request.getName());
        }

        List<NodeDocument> allDocs = cacheData.getNodes();
        Map<String, NodeDocument> nameToDoc = allDocs.stream()
                .collect(Collectors.toMap(NodeDocument::getName, Function.identity()));

        String newId = UUID.randomUUID().toString();
        NodeDocument newNode = new NodeDocument();
        newNode.setId(newId);
        newNode.setName(request.getName());

        Map<String, NodeDocument> toUpdate = new HashMap<>();
        Map<String, Integer> newConnections = new HashMap<>();

        request.getConnections().forEach((targetName, weight) -> {
            NodeDocument targetDoc = nameToDoc.get(targetName);
            if (targetDoc != null) {
                newConnections.put(targetDoc.getId(), weight);
                targetDoc.getConnections().put(newId, weight);
                toUpdate.put(targetDoc.getId(), targetDoc);
            } else {
                log.warn("Skipping unknown connection target '{}' for node '{}'", targetName, request.getName());
            }
        });

        newNode.setConnections(newConnections);
        toUpdate.put(newId, newNode);
        nodeRepository.saveAll(toUpdate.values());
        cacheData.refresh();
        log.info("Node '{}' added with {} connection(s)", request.getName(), newConnections.size());
    }

    public void updateNode(String nodeName, UpdateNodeRequest request) {
        log.info("Updating node '{}'", nodeName);
        List<NodeDocument> allDocs = cacheData.getNodes();
        Map<String, NodeDocument> nameToDoc = allDocs.stream()
                .collect(Collectors.toMap(NodeDocument::getName, Function.identity()));
        Map<String, NodeDocument> idToDoc = allDocs.stream()
                .collect(Collectors.toMap(NodeDocument::getId, Function.identity()));

        NodeDocument nodeDoc = nameToDoc.get(nodeName);
        if (nodeDoc == null) {
            throw new MapException("Node not found: " + nodeName);
        }

        Map<String, Integer> newConnections = new HashMap<>();
        request.getConnections().forEach((targetName, weight) -> {
            NodeDocument targetDoc = nameToDoc.get(targetName);
            if (targetDoc != null) {
                newConnections.put(targetDoc.getId(), weight);
            } else {
                log.warn("Skipping unknown connection target '{}' for node '{}'", targetName, nodeName);
            }
        });

        Map<String, NodeDocument> toUpdate = new HashMap<>();

        nodeDoc.getConnections().forEach((targetId, weight) -> {
            if (!newConnections.containsKey(targetId)) {
                NodeDocument targetDoc = idToDoc.get(targetId);
                if (targetDoc != null) {
                    log.debug("Removing connection from '{}' to '{}'", nodeName, targetDoc.getName());
                    targetDoc.getConnections().remove(nodeDoc.getId());
                    toUpdate.put(targetId, targetDoc);
                }
            }
        });

        newConnections.forEach((targetId, weight) -> {
            NodeDocument targetDoc = idToDoc.get(targetId);
            if (targetDoc != null) {
                log.debug("Adding/updating connection from '{}' to '{}' (weight: {})", nodeName, targetDoc.getName(), weight);
                targetDoc.getConnections().put(nodeDoc.getId(), weight);
                toUpdate.put(targetId, targetDoc);
            }
        });

        nodeDoc.setConnections(newConnections);
        toUpdate.put(nodeDoc.getId(), nodeDoc);
        nodeRepository.saveAll(toUpdate.values());
        cacheData.refresh();
        log.info("Node '{}' updated, {} document(s) affected", nodeName, toUpdate.size());
    }

    public void deleteNode(String nodeName) {
        log.info("Deleting node '{}'", nodeName);
        List<NodeDocument> allDocs = cacheData.getNodes();

        NodeDocument nodeDoc = allDocs.stream()
                .filter(d -> nodeName.equals(d.getName()))
                .findFirst()
                .orElseThrow(() -> new MapException("Node not found: " + nodeName));

        List<NodeDocument> toUpdate = allDocs.stream()
                .filter(d -> !d.getId().equals(nodeDoc.getId()) && d.getConnections().containsKey(nodeDoc.getId()))
                .peek(d -> d.getConnections().remove(nodeDoc.getId()))
                .collect(Collectors.toList());

        nodeRepository.saveAll(toUpdate);
        nodeRepository.delete(nodeDoc);
        cacheData.refresh();
        log.info("Node '{}' deleted, {} neighbour(s) updated", nodeName, toUpdate.size());
    }
}
