package com.eRez.tests.services;

import com.eRez.tests.data.CacheData;
import com.eRez.tests.database.document.NodeDocument;
import com.eRez.tests.database.repository.NodeRepository;
import com.eRez.tests.dto.request.CreateMapRequest;
import com.eRez.tests.dto.request.NodeRequest;
import com.eRez.tests.dto.request.UpdateNodeRequest;
import com.eRez.tests.dto.response.MapResponse;
import com.eRez.tests.exception.MapException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private CacheData cacheData;
    @InjectMocks private NodeService nodeService;

    private static final String ID_A = "id-amsterdam";
    private static final String ID_B = "id-berlin";
    private static final String ID_C = "id-paris";

    private NodeDocument amsterdam;
    private NodeDocument berlin;
    private NodeDocument paris;

    @BeforeEach
    void setUp() {
        amsterdam = doc(ID_A, "Amsterdam", new HashMap<>(Map.of(ID_B, 7)));
        berlin    = doc(ID_B, "Berlin",    new HashMap<>(Map.of(ID_A, 7)));
        paris     = doc(ID_C, "Paris",     new HashMap<>());
    }

    private NodeDocument doc(String id, String name, Map<String, Integer> connections) {
        NodeDocument d = new NodeDocument();
        d.setId(id);
        d.setName(name);
        d.setConnections(connections);
        return d;
    }

    private NodeRequest nodeRequest(String name, Map<String, Integer> connections) {
        NodeRequest r = new NodeRequest();
        r.setName(name);
        r.setConnections(connections);
        return r;
    }

    // ── createMap ─────────────────────────────────────────────────────────────

    @Test
    void createMap_validRequest_deletesAndSavesAll() {
        CreateMapRequest request = new CreateMapRequest();
        request.setNodes(List.of(
                nodeRequest("Amsterdam", Map.of("Berlin", 7)),
                nodeRequest("Berlin",    Map.of("Amsterdam", 7))
        ));

        nodeService.createMap(request);

        verify(nodeRepository).deleteAll();
        verify(nodeRepository).saveAll(anyList());
        verify(cacheData).refresh();
    }

    @Test
    void createMap_unknownConnectionTarget_throwsAndDoesNotWrite() {
        CreateMapRequest request = new CreateMapRequest();
        request.setNodes(List.of(
                nodeRequest("Amsterdam", Map.of("Atlantis", 5))
        ));

        assertThatThrownBy(() -> nodeService.createMap(request))
                .isInstanceOf(MapException.class)
                .hasMessageContaining("connects to unknown node");

        verifyNoInteractions(nodeRepository);
    }

    @Test
    void createMap_missingReverseConnection_throwsAndDoesNotWrite() {
        CreateMapRequest request = new CreateMapRequest();
        request.setNodes(List.of(
                nodeRequest("Amsterdam", Map.of("Berlin", 7)),
                nodeRequest("Berlin",    Map.of())
        ));

        assertThatThrownBy(() -> nodeService.createMap(request))
                .isInstanceOf(MapException.class)
                .hasMessageContaining("has no reverse");

        verifyNoInteractions(nodeRepository);
    }

    @Test
    void createMap_weightMismatch_throwsAndDoesNotWrite() {
        CreateMapRequest request = new CreateMapRequest();
        request.setNodes(List.of(
                nodeRequest("Amsterdam", Map.of("Berlin", 7)),
                nodeRequest("Berlin",    Map.of("Amsterdam", 99))
        ));

        assertThatThrownBy(() -> nodeService.createMap(request))
                .isInstanceOf(MapException.class)
                .hasMessageContaining("Weight mismatch");

        verifyNoInteractions(nodeRepository);
    }

    // ── addNode ───────────────────────────────────────────────────────────────

    @Test
    void addNode_newNode_savedWithBidirectionalConnection() {
        when(cacheData.getNodes()).thenReturn(new ArrayList<>(List.of(amsterdam, berlin)));

        nodeService.addNode(nodeRequest("Prague", Map.of("Berlin", 5)));

        verify(nodeRepository).saveAll(anyCollection());
        verify(cacheData).refresh();
        assertThat(berlin.getConnections()).hasSize(2); // original + Prague
    }

    @Test
    void addNode_nodeAlreadyExists_throws() {
        when(cacheData.getNodes()).thenReturn(new ArrayList<>(List.of(amsterdam)));

        assertThatThrownBy(() -> nodeService.addNode(nodeRequest("Amsterdam", Map.of())))
                .isInstanceOf(MapException.class)
                .hasMessageContaining("Node already exists");

        verifyNoInteractions(nodeRepository);
    }

    // ── updateNode ────────────────────────────────────────────────────────────

    @Test
    void updateNode_addConnection_neighbourLinked() {
        when(cacheData.getNodes()).thenReturn(new ArrayList<>(List.of(amsterdam, berlin, paris)));

        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setConnections(Map.of("Berlin", 7, "Paris", 3));

        nodeService.updateNode("Amsterdam", request);

        verify(nodeRepository).saveAll(anyCollection());
        assertThat(paris.getConnections()).containsKey(ID_A);
    }

    @Test
    void updateNode_removeConnection_neighbourUnlinked() {
        when(cacheData.getNodes()).thenReturn(new ArrayList<>(List.of(amsterdam, berlin)));

        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setConnections(Map.of()); // drop all connections

        nodeService.updateNode("Amsterdam", request);

        assertThat(berlin.getConnections()).doesNotContainKey(ID_A);
    }

    @Test
    void updateNode_nodeNotFound_throws() {
        when(cacheData.getNodes()).thenReturn(new ArrayList<>(List.of(amsterdam)));

        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setConnections(Map.of());

        assertThatThrownBy(() -> nodeService.updateNode("Unknown", request))
                .isInstanceOf(MapException.class)
                .hasMessageContaining("Node not found");
    }

    // ── deleteNode ────────────────────────────────────────────────────────────

    @Test
    void deleteNode_removesNodeAndUnlinksNeighbours() {
        when(cacheData.getNodes()).thenReturn(new ArrayList<>(List.of(amsterdam, berlin)));

        nodeService.deleteNode("Amsterdam");

        verify(nodeRepository).saveAll(anyList());
        verify(nodeRepository).delete(amsterdam);
        assertThat(berlin.getConnections()).doesNotContainKey(ID_A);
    }

    @Test
    void deleteNode_nodeNotFound_throws() {
        when(cacheData.getNodes()).thenReturn(new ArrayList<>(List.of(amsterdam)));

        assertThatThrownBy(() -> nodeService.deleteNode("Unknown"))
                .isInstanceOf(MapException.class)
                .hasMessageContaining("Node not found");
    }

    // ── getMap ────────────────────────────────────────────────────────────────

    @Test
    void getMap_returnsNodesWithNamesInsteadOfIds() {
        when(cacheData.getNodes()).thenReturn(List.of(amsterdam, berlin));

        MapResponse response = nodeService.getMap();

        assertThat(response.getNodes()).hasSize(2);
        assertThat(response.getNodes())
                .extracting(n -> n.getName())
                .containsExactlyInAnyOrder("Amsterdam", "Berlin");
        response.getNodes().stream()
                .filter(n -> "Amsterdam".equals(n.getName()))
                .findFirst()
                .ifPresent(n -> assertThat(n.getConnections()).containsKey("Berlin"));
    }
}
