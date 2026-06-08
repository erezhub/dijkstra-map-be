package com.eRez.tests.data;

import com.eRez.tests.database.document.NodeDocument;
import com.eRez.tests.database.repository.NodeRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheData {

    private final NodeRepository nodeRepository;
    @Getter
    private volatile List<NodeDocument> nodes = Collections.emptyList();

    @PostConstruct
    private void initCache() {
        nodes = Collections.unmodifiableList(nodeRepository.findAll());
        log.info("Cache initialized with {} node(s)", nodes.size());
    }

    public void refresh() {
        nodes = Collections.unmodifiableList(nodeRepository.findAll());
        log.debug("Cache refreshed with {} node(s)", nodes.size());
    }
}
