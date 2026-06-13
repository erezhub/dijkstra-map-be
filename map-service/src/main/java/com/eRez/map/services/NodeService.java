package com.eRez.map.services;

import com.eRez.map.data.CacheData;
import com.eRez.map.database.document.NodeDocument;
import com.eRez.map.database.repository.NodeRepository;
import com.eRez.map.dto.event.NodeChangedEvent;
import com.eRez.map.dto.request.AddNodeRequest;
import com.eRez.map.dto.request.CreateMapRequest;
import com.eRez.map.dto.request.NodeRequest;
import com.eRez.map.dto.request.UpdateNodeRequest;
import com.eRez.map.dto.response.MapResponse;
import com.eRez.map.dto.response.NodeResponse;
import com.eRez.map.exception.MapException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
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
    private final RouteService routeService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    public MapResponse getMap() {
        List<NodeDocument> allDocs = cacheData.getNodes();

        List<NodeResponse> nodes = allDocs.stream()
                .map(doc -> new NodeResponse(doc.getId(), doc.getName(), doc.getPosition(),
                        new HashMap<>(doc.getConnections())))
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
            doc.setPosition(node.getPosition());
            Map<String, Integer> connections = new HashMap<>();
            node.getConnections().forEach((targetName, weight) ->
                connections.put(nameToId.get(targetName), weight)
            );
            doc.setConnections(connections);
            documents.add(doc);
        }

        routeService.deleteAll();
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

    public void addNode(AddNodeRequest request) {
        log.info("Adding node '{}'", request.getName());
        boolean exists = cacheData.getNodes().stream()
                .anyMatch(d -> request.getName().equals(d.getName()));
        if (exists) {
            throw new MapException("Node already exists: " + request.getName());
        }

        Map<String, NodeDocument> idToDoc = cacheData.getIdToDoc();

        String newId = UUID.randomUUID().toString();
        NodeDocument newNode = new NodeDocument();
        newNode.setId(newId);
        newNode.setName(request.getName());
        newNode.setPosition(request.getPosition());

        Map<String, NodeDocument> toUpdate = new HashMap<>();
        Map<String, Integer> newConnections = new HashMap<>();

        request.getConnections().forEach((targetId, weight) -> {
            NodeDocument targetDoc = idToDoc.get(targetId);
            if (targetDoc != null) {
                newConnections.put(targetId, weight);
                targetDoc.getConnections().put(newId, weight);
                toUpdate.put(targetId, targetDoc);
            } else {
                log.warn("Skipping unknown connection target '{}' for node '{}'", targetId, request.getName());
            }
        });

        newNode.setConnections(newConnections);
        toUpdate.put(newId, newNode);
        nodeRepository.saveAll(toUpdate.values());
        cacheData.refresh();

        routeService.markAllStale();
        rabbitTemplate.convertAndSend(exchange, "map.node.added", new NodeChangedEvent("NODE_ADDED", null));
        log.info("Node '{}' added with {} connection(s)", request.getName(), newConnections.size());
    }

    public void updateNode(String nodeId, UpdateNodeRequest request) {
        log.info("Updating node with id '{}'", nodeId);
        List<NodeDocument> allDocs = cacheData.getNodes();
        Map<String, NodeDocument> nameToDoc = allDocs.stream()
                .collect(Collectors.toMap(NodeDocument::getName, Function.identity()));
        Map<String, NodeDocument> idToDoc = cacheData.getIdToDoc();

        NodeDocument nodeDoc = idToDoc.get(nodeId);
        if (nodeDoc == null) {
            throw new MapException("Node not found: " + nodeId);
        }

        if (request.getNewName() != null && !request.getNewName().equals(nodeDoc.getName())) {
            if (nameToDoc.containsKey(request.getNewName()))
                throw new MapException("Node already exists: " + request.getNewName());
            nodeDoc.setName(request.getNewName());
        }

        Map<String, NodeDocument> toUpdate = new HashMap<>();
        boolean connectionsChanged = request.getConnections() != null;

        if (connectionsChanged) {
            Map<String, Integer> newConnections = new HashMap<>();
            request.getConnections().forEach((targetId, weight) -> {
                if (idToDoc.containsKey(targetId)) {
                    newConnections.put(targetId, weight);
                } else {
                    log.warn("Skipping unknown connection target '{}' for node '{}'", targetId, nodeDoc.getName());
                }
            });

            nodeDoc.getConnections().forEach((targetId, weight) -> {
                if (!newConnections.containsKey(targetId)) {
                    NodeDocument targetDoc = idToDoc.get(targetId);
                    if (targetDoc != null) {
                        log.debug("Removing connection from '{}' to '{}'", nodeDoc.getName(), targetDoc.getName());
                        targetDoc.getConnections().remove(nodeDoc.getId());
                        toUpdate.put(targetId, targetDoc);
                    }
                }
            });

            newConnections.forEach((targetId, weight) -> {
                NodeDocument targetDoc = idToDoc.get(targetId);
                if (targetDoc != null) {
                    log.debug("Adding/updating connection from '{}' to '{}' (weight: {})", nodeDoc.getName(), targetDoc.getName(), weight);
                    targetDoc.getConnections().put(nodeDoc.getId(), weight);
                    toUpdate.put(targetId, targetDoc);
                }
            });

            nodeDoc.setConnections(newConnections);
        }

        if (request.getPosition() != null) {
            nodeDoc.setPosition(request.getPosition());
        }

        toUpdate.put(nodeDoc.getId(), nodeDoc);
        nodeRepository.saveAll(toUpdate.values());
        cacheData.refresh();

        if (connectionsChanged) {
            routeService.markAllStale();
            rabbitTemplate.convertAndSend(exchange, "map.node.updated", new NodeChangedEvent("NODE_UPDATED", nodeDoc.getName()));
        }
        log.info("Node '{}' updated, {} document(s) affected", nodeDoc.getName(), toUpdate.size());
    }

    public void deleteNode(String nodeId) {
        log.info("Deleting node with id '{}'", nodeId);
        NodeDocument nodeDoc = cacheData.getIdToDoc().get(nodeId);
        if (nodeDoc == null) throw new MapException("Node not found: " + nodeId);

        List<NodeDocument> toUpdate = cacheData.getNodes().stream()
                .filter(d -> !d.getId().equals(nodeDoc.getId()) && d.getConnections().containsKey(nodeDoc.getId()))
                .peek(d -> d.getConnections().remove(nodeDoc.getId()))
                .collect(Collectors.toList());

        nodeRepository.saveAll(toUpdate);
        nodeRepository.delete(nodeDoc);
        cacheData.refresh();

        routeService.deleteByEndpoint(nodeId);
        routeService.markStaleByPath(nodeId);
        rabbitTemplate.convertAndSend(exchange, "map.node.deleted", new NodeChangedEvent("NODE_DELETED_INTERMEDIATE", nodeDoc.getName()));
        log.info("Node '{}' deleted, {} neighbour(s) updated", nodeDoc.getName(), toUpdate.size());
    }
}