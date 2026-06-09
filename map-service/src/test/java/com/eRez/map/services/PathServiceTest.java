package com.eRez.map.services;

import com.eRez.map.data.CacheData;
import com.eRez.map.database.document.NodeDocument;
import com.eRez.map.dto.response.PathResponse;
import com.eRez.map.exception.MapException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PathServiceTest {

    @Mock private CacheData cacheData;
    @InjectMocks private PathService pathService;

    // Graph:
    //   Amsterdam -7- Berlin -5- Prague
    //   Amsterdam -3- Paris
    //   Isolated  (no connections)
    private static final String ID_A = "id-a";
    private static final String ID_B = "id-b";
    private static final String ID_C = "id-c";
    private static final String ID_D = "id-d";
    private static final String ID_X = "id-x";

    private NodeDocument amsterdam;
    private NodeDocument berlin;
    private NodeDocument paris;
    private NodeDocument prague;
    private NodeDocument isolated;

    @BeforeEach
    void setUp() {
        amsterdam = doc(ID_A, "Amsterdam", new HashMap<>(Map.of(ID_B, 7, ID_C, 3)));
        berlin    = doc(ID_B, "Berlin",    new HashMap<>(Map.of(ID_A, 7, ID_D, 5)));
        paris     = doc(ID_C, "Paris",     new HashMap<>(Map.of(ID_A, 3)));
        prague    = doc(ID_D, "Prague",    new HashMap<>(Map.of(ID_B, 5)));
        isolated  = doc(ID_X, "Isolated",  new HashMap<>());
    }

    private NodeDocument doc(String id, String name, Map<String, Integer> connections) {
        NodeDocument d = new NodeDocument();
        d.setId(id);
        d.setName(name);
        d.setConnections(connections);
        return d;
    }

    @Test
    void getPath_directConnection_returnsCorrectDistanceAndSegment() {
        when(cacheData.getNodes()).thenReturn(List.of(amsterdam, berlin, paris, prague));

        PathResponse response = pathService.getPath("Amsterdam", "Paris");

        assertThat(response.getDistance()).isEqualTo(3);
        assertThat(response.getPath()).hasSize(1);
        assertThat(response.getPath().get(0).getFrom()).isEqualTo("Amsterdam");
        assertThat(response.getPath().get(0).getTo()).isEqualTo("Paris");
        assertThat(response.getPath().get(0).getDistance()).isEqualTo(3);
    }

    @Test
    void getPath_multiHop_returnsShortestPath() {
        when(cacheData.getNodes()).thenReturn(List.of(amsterdam, berlin, paris, prague));

        PathResponse response = pathService.getPath("Amsterdam", "Prague");

        assertThat(response.getDistance()).isEqualTo(12);
        assertThat(response.getPath()).hasSize(2);
        assertThat(response.getPath().get(0).getFrom()).isEqualTo("Amsterdam");
        assertThat(response.getPath().get(0).getTo()).isEqualTo("Berlin");
        assertThat(response.getPath().get(1).getFrom()).isEqualTo("Berlin");
        assertThat(response.getPath().get(1).getTo()).isEqualTo("Prague");
    }

    @Test
    void getPath_sameNode_returnsZeroDistanceWithEmptyPath() {
        when(cacheData.getNodes()).thenReturn(List.of(amsterdam, berlin));

        PathResponse response = pathService.getPath("Amsterdam", "Amsterdam");

        assertThat(response.getDistance()).isEqualTo(0);
        assertThat(response.getPath()).isEmpty();
    }

    @Test
    void getPath_fromNodeNotFound_throwsMapException() {
        when(cacheData.getNodes()).thenReturn(List.of(amsterdam));

        assertThatThrownBy(() -> pathService.getPath("Atlantis", "Amsterdam"))
                .isInstanceOf(MapException.class)
                .hasMessageContaining("Node not found: Atlantis");
    }

    @Test
    void getPath_toNodeNotFound_throwsMapException() {
        when(cacheData.getNodes()).thenReturn(List.of(amsterdam));

        assertThatThrownBy(() -> pathService.getPath("Amsterdam", "Atlantis"))
                .isInstanceOf(MapException.class)
                .hasMessageContaining("Node not found: Atlantis");
    }

    @Test
    void getPath_noPathExists_throwsMapException() {
        when(cacheData.getNodes()).thenReturn(List.of(amsterdam, isolated));

        assertThatThrownBy(() -> pathService.getPath("Amsterdam", "Isolated"))
                .isInstanceOf(MapException.class)
                .hasMessageContaining("No path exists");
    }
}
