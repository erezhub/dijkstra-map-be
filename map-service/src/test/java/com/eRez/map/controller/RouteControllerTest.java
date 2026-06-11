package com.eRez.map.controller;

import com.eRez.common.exception.GlobalExceptionHandler;
import com.eRez.map.dto.response.PathSegment;
import com.eRez.map.dto.response.SavedRouteResponse;
import com.eRez.map.exception.MapException;
import com.eRez.map.services.RouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RouteControllerTest {

    @Mock private RouteService routeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RouteController(routeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private SavedRouteResponse sampleResponse() {
        return new SavedRouteResponse("A", "C", 10, List.of(
                new PathSegment("A", "B", 4),
                new PathSegment("B", "C", 6)
        ));
    }

    // ── POST /map/route ───────────────────────────────────────────────────────

    @Test
    void saveRoute_returns201WithBody() throws Exception {
        when(routeService.saveRoute("A", "C")).thenReturn(sampleResponse());

        mockMvc.perform(post("/map/route").param("from", "A").param("to", "C"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeA").value("A"))
                .andExpect(jsonPath("$.nodeB").value("C"))
                .andExpect(jsonPath("$.distance").value(10))
                .andExpect(jsonPath("$.path.length()").value(2))
                .andExpect(jsonPath("$.path[0].from").value("A"))
                .andExpect(jsonPath("$.path[1].to").value("C"));
    }

    @Test
    void saveRoute_serviceThrows_returns409() throws Exception {
        when(routeService.saveRoute("A", "GONE")).thenThrow(new MapException("Node not found: GONE"));

        mockMvc.perform(post("/map/route").param("from", "A").param("to", "GONE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Node not found: GONE"));
    }

    // ── GET /map/route ────────────────────────────────────────────────────────

    @Test
    void getRoute_returns200WithBody() throws Exception {
        when(routeService.getRoute("A", "C")).thenReturn(sampleResponse());

        mockMvc.perform(get("/map/route").param("from", "A").param("to", "C"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeA").value("A"))
                .andExpect(jsonPath("$.nodeB").value("C"))
                .andExpect(jsonPath("$.distance").value(10));
    }

    @Test
    void getRoute_notFound_returns409() throws Exception {
        when(routeService.getRoute("A", "C")).thenThrow(new MapException("No saved route from 'A' to 'C'"));

        mockMvc.perform(get("/map/route").param("from", "A").param("to", "C"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No saved route from 'A' to 'C'"));
    }

    // ── DELETE /map/route ─────────────────────────────────────────────────────

    @Test
    void deleteRoute_returns204() throws Exception {
        mockMvc.perform(delete("/map/route").param("from", "A").param("to", "C"))
                .andExpect(status().isNoContent());

        verify(routeService).deleteRoute("A", "C");
    }

    @Test
    void deleteRoute_notFound_returns409() throws Exception {
        doThrow(new MapException("No saved route from 'A' to 'C'"))
                .when(routeService).deleteRoute("A", "C");

        mockMvc.perform(delete("/map/route").param("from", "A").param("to", "C"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No saved route from 'A' to 'C'"));
    }

    // ── GET /map/routes ───────────────────────────────────────────────────────

    @Test
    void getAllRoutes_returns200WithList() throws Exception {
        when(routeService.getAllRoutes()).thenReturn(List.of(
                sampleResponse(),
                new SavedRouteResponse("X", "Y", 3, List.of(new PathSegment("X", "Y", 3)))
        ));

        mockMvc.perform(get("/map/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nodeA").value("A"))
                .andExpect(jsonPath("$[1].nodeA").value("X"));
    }

    @Test
    void getAllRoutes_empty_returns200WithEmptyList() throws Exception {
        when(routeService.getAllRoutes()).thenReturn(List.of());

        mockMvc.perform(get("/map/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
