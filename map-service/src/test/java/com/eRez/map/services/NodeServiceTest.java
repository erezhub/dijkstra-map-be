package com.eRez.map.services;

import com.eRez.map.data.CacheData;
import com.eRez.map.database.document.NodeDocument;
import com.eRez.map.database.repository.NodeRepository;
import com.eRez.map.dto.Position;
import com.eRez.map.dto.event.NodeChangedEvent;
import com.eRez.map.dto.request.CreateMapRequest;
import com.eRez.map.dto.request.NodeRequest;
import com.eRez.map.dto.request.UpdateNodeRequest;
import com.eRez.map.dto.response.MapResponse;
import com.eRez.map.dto.response.NodeResponse;
import com.eRez.map.exception.MapException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private CacheData cacheData;
    @Mock private RouteService routeService;
    @Mock private RabbitTemplate rabbitTemplate;
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
        amsterdam.setPosition(position(1.0, 2.0));
        berlin    = doc(ID_B, "Berlin",    new HashMap<>(Map.of(ID_A, 7)));
        paris     = doc(ID_C, "Paris",     new HashMap<>());
        ReflectionTestUtils.setField(nodeService, "exchange", "dijkstra.events");
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

    private Position position(double x, double y) {
        Position p = new Position();
        p.setX(x);
        p.setY(y);
        return p;
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

        verify(routeService).deleteAll();
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
        verify(routeService).markAllStale();
        verify(rabbitTemplate).convertAndSend(eq("dijkstra.events"), eq("map.node.added"), any(NodeChangedEvent.class));
    }

    @Test
    void addNode_nodeAlreadyExists_throws() {
        when(cacheData.getNodes()).thenReturn(new ArrayList<>(List.of(amsterdam)));

        assertThatThrownBy(() -> nodeService.addNode(nodeRequest("Amsterdam", Map.of())))
                .isInstanceOf(MapException.class)
                .hasMessageContaining("Node already exists");

        verifyNoInteractions(nodeRepository);
    }

    @Test
    void addNode_persistsPosition() {
        when(cacheData.getNodes()).thenReturn(new ArrayList<>(List.of(amsterdam)));

        NodeRequest request = nodeRequest("Prague", Map.of());
        request.setPosition(position(5.0, 10.0));

        nodeService.addNode(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<NodeDocument>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(nodeRepository).saveAll(captor.capture());

        NodeDocument saved = captor.getValue().stream()
                .filter(d -> "Prague".equals(d.getName()))
                .findFirst().orElseThrow();
        assertThat(saved.getPosition().getX()).isEqualTo(5.0);
        assertThat(saved.getPosition().getY()).isEqualTo(10.0);
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
        verify(routeService).markAllStale();
        verify(rabbitTemplate).convertAndSend(eq("dijkstra.events"), eq("map.node.updated"), any(NodeChangedEvent.class));
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

    @Test
    void updateNode_updatesPosition() {
        when(cacheData.getNodes()).thenReturn(new ArrayList<>(List.of(amsterdam, berlin)));

        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setConnections(Map.of("Berlin", 7));
        request.setPosition(position(3.0, 4.0));

        nodeService.updateNode("Amsterdam", request);

        assertThat(amsterdam.getPosition().getX()).isEqualTo(3.0);
        assertThat(amsterdam.getPosition().getY()).isEqualTo(4.0);
    }

    // ── deleteNode ────────────────────────────────────────────────────────────

    @Test
    void deleteNode_removesNodeAndUnlinksNeighbours() {
        when(cacheData.getNodes()).thenReturn(new ArrayList<>(List.of(amsterdam, berlin)));

        nodeService.deleteNode("Amsterdam");

        verify(nodeRepository).saveAll(anyList());
        verify(nodeRepository).delete(amsterdam);
        assertThat(berlin.getConnections()).doesNotContainKey(ID_A);
        verify(routeService).deleteByEndpoint("Amsterdam");
        verify(routeService).markStaleByPath("Amsterdam");
        verify(rabbitTemplate).convertAndSend(eq("dijkstra.events"), eq("map.node.deleted"), any(NodeChangedEvent.class));
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
                .extracting(NodeResponse::getName)
                .containsExactlyInAnyOrder("Amsterdam", "Berlin");
        response.getNodes().stream()
                .filter(n -> "Amsterdam".equals(n.getName()))
                .findFirst()
                .ifPresent(n -> assertThat(n.getConnections()).containsKey("Berlin"));
    }

    @Test
    void getMap_includesPositionInResponse() {
        when(cacheData.getNodes()).thenReturn(List.of(amsterdam)); // amsterdam has position (1,2) from setUp

        MapResponse response = nodeService.getMap();

        assertThat(response.getNodes().get(0).getPosition()).isNotNull();
        assertThat(response.getNodes().get(0).getPosition().getX()).isEqualTo(1.0);
        assertThat(response.getNodes().get(0).getPosition().getY()).isEqualTo(2.0);
    }
}
