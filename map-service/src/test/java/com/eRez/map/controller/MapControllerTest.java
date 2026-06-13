package com.eRez.map.controller;

import com.eRez.map.dto.Position;
import com.eRez.map.dto.response.MapResponse;
import com.eRez.map.dto.response.NodeResponse;
import com.eRez.map.dto.response.PathResponse;
import com.eRez.map.dto.response.PathSegment;
import com.eRez.map.dto.response.SavedRouteResponse;
import com.eRez.common.exception.GlobalExceptionHandler;
import com.eRez.map.exception.MapException;
import com.eRez.map.services.NodeService;
import com.eRez.map.services.PathService;
import com.eRez.map.services.RouteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
    @Mock private RouteService routeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MapController controller = new MapController(nodeService, pathService, routeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        setAuth("admin", "ADMIN");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuth(String username, String role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var userDetails = User.withUsername(username).password("").authorities(authorities).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities));
    }

    private Position position(double x, double y) {
        Position p = new Position();
        p.setX(x);
        p.setY(y);
        return p;
    }

    // ── GET /map ──────────────────────────────────────────────────────────────

    @Test
    void getMap_returns200WithNodes() throws Exception {
        when(nodeService.getMap()).thenReturn(new MapResponse(List.of(
                new NodeResponse("id-amsterdam", "Amsterdam", position(1.0, 2.0), Map.of("id-berlin", 7)),
                new NodeResponse("id-berlin",    "Berlin",    null,               Map.of("id-amsterdam", 7))
        )));

        mockMvc.perform(get("/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.nodes[0].id").value("id-amsterdam"))
                .andExpect(jsonPath("$.nodes[0].name").value("Amsterdam"))
                .andExpect(jsonPath("$.nodes[0].position.x").value(1.0))
                .andExpect(jsonPath("$.nodes[0].position.y").value(2.0))
                .andExpect(jsonPath("$.nodes[1].position").doesNotExist());
    }

    // ── POST /map ─────────────────────────────────────────────────────────────

    @Test
    void createMap_validRequest_returns201() throws Exception {
        String body = """
                {
                  "nodes": [
                    {"name": "Amsterdam", "position": {"x": 1.0, "y": 2.0}, "connections": {"Berlin": 7}},
                    {"name": "Berlin",    "position": {"x": 3.0, "y": 4.0}, "connections": {"Amsterdam": 7}}
                  ]
                }
                """;

        mockMvc.perform(post("/map").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createMap_regularUser_returns409() throws Exception {
        setAuth("user@x.com", "REGULAR");
        String body = """
                {"nodes": [{"name": "Amsterdam", "connections": {}}]}
                """;

        mockMvc.perform(post("/map").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void createMap_managerUser_returns201() throws Exception {
        setAuth("mgr@x.com", "MANAGER");
        String body = """
                {
                  "nodes": [
                    {"name": "Amsterdam", "connections": {}},
                    {"name": "Berlin",    "connections": {}}
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
        // connections keys are now node IDs
        String body = """
                {"name": "Prague", "position": {"x": 5.0, "y": 6.0}, "connections": {"id-berlin": 5}}
                """;

        mockMvc.perform(post("/map/node").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void addNode_regularUser_returns409() throws Exception {
        setAuth("user@x.com", "REGULAR");
        String body = """
                {"name": "Prague", "connections": {}}
                """;

        mockMvc.perform(post("/map/node").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Access denied"));
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

    // ── PUT /map/node/{id} ────────────────────────────────────────────────────

    @Test
    void updateNode_validRequest_returns200() throws Exception {
        String body = """
                {"position": {"x": 7.0, "y": 8.0}, "connections": {"id-berlin": 5}}
                """;

        mockMvc.perform(put("/map/node/id-amsterdam").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void updateNode_regularUser_returns409() throws Exception {
        setAuth("user@x.com", "REGULAR");
        String body = """
                {"connections": {}}
                """;

        mockMvc.perform(put("/map/node/id-amsterdam").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void updateNode_serviceThrowsMapException_returns409() throws Exception {
        doThrow(new MapException("Node not found: id-unknown"))
                .when(nodeService).updateNode(eq("id-unknown"), any());

        String body = """
                {"connections": {}}
                """;

        mockMvc.perform(put("/map/node/id-unknown").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Node not found: id-unknown"));
    }

    // ── DELETE /map/node/{id} ─────────────────────────────────────────────────

    @Test
    void deleteNode_returns204() throws Exception {
        mockMvc.perform(delete("/map/node/id-amsterdam"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteNode_regularUser_returns409() throws Exception {
        setAuth("user@x.com", "REGULAR");

        mockMvc.perform(delete("/map/node/id-amsterdam"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void deleteNode_serviceThrowsMapException_returns409() throws Exception {
        doThrow(new MapException("Node not found: id-ghost"))
                .when(nodeService).deleteNode("id-ghost");

        mockMvc.perform(delete("/map/node/id-ghost"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Node not found: id-ghost"));
    }

    // ── GET /map/path ─────────────────────────────────────────────────────────

    @Test
    void getPath_noSavedRoute_runsDijkstra() throws Exception {
        when(routeService.findCachedRoute("id-amsterdam", "id-prague")).thenReturn(Optional.empty());
        when(pathService.getPath("id-amsterdam", "id-prague")).thenReturn(new PathResponse(12, List.of(
                new PathSegment("id-amsterdam", "Amsterdam", "id-berlin", "Berlin", 7),
                new PathSegment("id-berlin",    "Berlin",    "id-prague", "Prague", 5)
        )));

        mockMvc.perform(get("/map/path").param("from", "id-amsterdam").param("to", "id-prague"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distance").value(12))
                .andExpect(jsonPath("$.path.length()").value(2))
                .andExpect(jsonPath("$.path[0].from").value("Amsterdam"))
                .andExpect(jsonPath("$.path[1].to").value("Prague"));

        verify(pathService).getPath("id-amsterdam", "id-prague");
    }

    @Test
    void getPath_cachedRouteExists_returnsCachedWithoutDijkstra() throws Exception {
        SavedRouteResponse cached = new SavedRouteResponse(
                "id-amsterdam", "Amsterdam", "id-prague", "Prague", 12, List.of(
                new PathSegment("id-amsterdam", "Amsterdam", "id-berlin", "Berlin", 7),
                new PathSegment("id-berlin",    "Berlin",    "id-prague", "Prague", 5)
        ), List.of("admin"));
        when(routeService.findCachedRoute("id-amsterdam", "id-prague")).thenReturn(Optional.of(cached));

        mockMvc.perform(get("/map/path").param("from", "id-amsterdam").param("to", "id-prague"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distance").value(12))
                .andExpect(jsonPath("$.path.length()").value(2));

        verify(pathService, org.mockito.Mockito.never()).getPath(anyString(), anyString());
    }

    @Test
    void getPath_serviceThrowsMapException_returns409() throws Exception {
        when(routeService.findCachedRoute("id-atlantis", "id-amsterdam")).thenReturn(Optional.empty());
        doThrow(new MapException("Node not found: id-atlantis"))
                .when(pathService).getPath("id-atlantis", "id-amsterdam");

        mockMvc.perform(get("/map/path").param("from", "id-atlantis").param("to", "id-amsterdam"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Node not found: id-atlantis"));
    }
}