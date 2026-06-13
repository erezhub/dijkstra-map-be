package com.eRez.map.data;

import com.eRez.map.database.document.NodeDocument;
import com.eRez.map.database.repository.NodeRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheData {

    private final NodeRepository nodeRepository;
    @Getter
    private volatile List<NodeDocument> nodes = Collections.emptyList();
    @Getter
    private volatile Map<String, String> idToName = Collections.emptyMap();
    @Getter
    private volatile Map<String, NodeDocument> idToDoc = Collections.emptyMap();

    @PostConstruct
    private void initCache() {
        List<NodeDocument> fresh = Collections.unmodifiableList(nodeRepository.findAll());
        nodes = fresh;
        idToName = Collections.unmodifiableMap(
                fresh.stream().collect(Collectors.toMap(NodeDocument::getId, NodeDocument::getName)));
        idToDoc = Collections.unmodifiableMap(
                fresh.stream().collect(Collectors.toMap(NodeDocument::getId, Function.identity())));
        log.info("Cache initialized with {} node(s)", nodes.size());
    }

    public void refresh() {
        List<NodeDocument> fresh = Collections.unmodifiableList(nodeRepository.findAll());
        nodes = fresh;
        idToName = Collections.unmodifiableMap(
                fresh.stream().collect(Collectors.toMap(NodeDocument::getId, NodeDocument::getName)));
        idToDoc = Collections.unmodifiableMap(
                fresh.stream().collect(Collectors.toMap(NodeDocument::getId, Function.identity())));
        log.debug("Cache refreshed with {} node(s)", nodes.size());
    }
}
