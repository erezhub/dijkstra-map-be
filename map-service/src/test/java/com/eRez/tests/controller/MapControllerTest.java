package com.eRez.tests.controller;

import com.eRez.tests.dto.response.MapResponse;
import com.eRez.tests.dto.response.NodeResponse;
import com.eRez.tests.dto.response.PathResponse;
import com.eRez.tests.dto.response.PathSegment;
import com.eRez.tests.exception.GlobalExceptionHandler;
import com.eRez.tests.exception.MapException;
import com.eRez.tests.services.NodeService;
import com.eRez.tests.services.PathService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MapControllerTest {

    @Mock private NodeService nodeService;
    @Mock private PathService pathService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MapController controller = new MapController(nodeService, pathService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── GET /map ──────────────────────────────────────────────────────────────

    @Test
    void getMap_returns200WithNodes() throws Exception {
        when(nodeService.getMap()).thenReturn(new MapResponse(List.of(
                new NodeResponse("Amsterdam", null, Map.of("Berlin", 7)),
                new NodeResponse("Berlin",    null, Map.of("Amsterdam", 7))
        )));

        mockMvc.perform(get("/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.nodes[0].name").value("Amsterdam"));
    }

    // ── POST /map ─────────────────────────────────────────────────────────────

    @Test
    void createMap_validRequest_returns201() throws Exception {
        String body = """
                {
                  "nodes": [
                    {"name": "Amsterdam", "connections": {"Berlin": 7}},
                    {"name": "Berlin",    "connections": {"Amsterdam": 7}}
                  ]
                }
                """;

        mockMvc.perform(post("/map").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createMap_blankNodeName_returns409() throws Exception {
        String body = """
                {"nodes": [{"name": "", "connections": {}}]}
                """;

        mockMvc.perform(post("/map").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void createMap_serviceThrowsMapException_returns409WithMessage() throws Exception {
        doThrow(new MapException("Node 'Amsterdam' connects to unknown node 'Ghost'"))
                .when(nodeService).createMap(any());

        String body = """
                {
                  "nodes": [
                    {"name": "Amsterdam", "connections": {"Ghost": 5}},
                    {"name": "Ghost",     "connections": {"Amsterdam": 5}}
                  ]
                }
                """;

        mockMvc.perform(post("/map").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Node 'Amsterdam' connects to unknown node 'Ghost'"));
    }

    // ── POST /map/node ────────────────────────────────────────────────────────

    @Test
    void addNode_validRequest_returns201() throws Exception {
        String body = """
                {"name": "Prague", "connections": {"Berlin": 5}}
                """;

        mockMvc.perform(post("/map/node").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void addNode_nullName_returns409() throws Exception {
        String body = """
                {"name": null, "connections": {}}
                """;

        mockMvc.perform(post("/map/node").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isString());
    }

    // ── PUT /map/node/{name} ──────────────────────────────────────────────────

    @Test
    void updateNode_validRequest_returns200() throws Exception {
        String body = """
                {"connections": {"Berlin": 5}}
                """;

        mockMvc.perform(put("/map/node/Amsterdam").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void updateNode_serviceThrowsMapException_returns409() throws Exception {
        doThrow(new MapException("Node not found: Unknown"))
                .when(nodeService).updateNode(eq("Unknown"), any());

        String body = """
                {"connections": {}}
                """;

        mockMvc.perform(put("/map/node/Unknown").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Node not found: Unknown"));
    }

    // ── DELETE /map/node/{name} ───────────────────────────────────────────────

    @Test
    void deleteNode_returns204() throws Exception {
        mockMvc.perform(delete("/map/node/Amsterdam"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteNode_serviceThrowsMapException_returns409() throws Exception {
        doThrow(new MapException("Node not found: Ghost"))
                .when(nodeService).deleteNode("Ghost");

        mockMvc.perform(delete("/map/node/Ghost"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Node not found: Ghost"));
    }

    // ── GET /map/path ─────────────────────────────────────────────────────────

    @Test
    void getPath_returns200WithPathResponse() throws Exception {
        when(pathService.getPath("Amsterdam", "Prague")).thenReturn(new PathResponse(12, List.of(
                new PathSegment("Amsterdam", "Berlin", 7),
                new PathSegment("Berlin",    "Prague", 5)
        )));

        mockMvc.perform(get("/map/path").param("from", "Amsterdam").param("to", "Prague"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distance").value(12))
                .andExpect(jsonPath("$.path.length()").value(2))
                .andExpect(jsonPath("$.path[0].from").value("Amsterdam"))
                .andExpect(jsonPath("$.path[1].to").value("Prague"));
    }

    @Test
    void getPath_serviceThrowsMapException_returns409() throws Exception {
        doThrow(new MapException("Node not found: Atlantis"))
                .when(pathService).getPath("Atlantis", "Amsterdam");

        mockMvc.perform(get("/map/path").param("from", "Atlantis").param("to", "Amsterdam"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Node not found: Atlantis"));
    }
}
