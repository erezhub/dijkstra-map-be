package com.eRez.map.controller;

import com.eRez.common.exception.GlobalExceptionHandler;
import com.eRez.map.dto.response.PathSegment;
import com.eRez.map.dto.response.SavedRouteResponse;
import com.eRez.map.exception.MapException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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

    private SavedRouteResponse sampleResponse() {
        return new SavedRouteResponse("id-A", "A", "id-C", "C", 10, List.of(
                new PathSegment("id-A", "A", "id-B", "B", 4),
                new PathSegment("id-B", "B", "id-C", "C", 6)
        ), List.of("admin"));
    }

    // ── POST /map/route ───────────────────────────────────────────────────────

    @Test
    void saveRoute_returns201WithBody() throws Exception {
        when(routeService.saveRoute(eq("id-A"), eq("id-C"), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/map/route").param("from", "id-A").param("to", "id-C"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeA").value("A"))
                .andExpect(jsonPath("$.nodeB").value("C"))
                .andExpect(jsonPath("$.nodeAId").value("id-A"))
                .andExpect(jsonPath("$.nodeBId").value("id-C"))
                .andExpect(jsonPath("$.distance").value(10))
                .andExpect(jsonPath("$.path.length()").value(2))
                .andExpect(jsonPath("$.path[0].from").value("A"))
                .andExpect(jsonPath("$.path[1].to").value("C"));
    }

    @Test
    void saveRoute_serviceThrows_returns409() throws Exception {
        when(routeService.saveRoute(eq("id-A"), eq("id-GONE"), any()))
                .thenThrow(new MapException("Node not found: id-GONE"));

        mockMvc.perform(post("/map/route").param("from", "id-A").param("to", "id-GONE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Node not found: id-GONE"));
    }

    // ── GET /map/route ────────────────────────────────────────────────────────

    @Test
    void getRoute_returns200WithBody() throws Exception {
        when(routeService.getRoute(eq("id-A"), eq("id-C"), any())).thenReturn(sampleResponse());

        mockMvc.perform(get("/map/route").param("from", "id-A").param("to", "id-C"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeA").value("A"))
                .andExpect(jsonPath("$.nodeB").value("C"))
                .andExpect(jsonPath("$.distance").value(10));
    }

    @Test
    void getRoute_notFound_returns409() throws Exception {
        when(routeService.getRoute(eq("id-A"), eq("id-C"), any()))
                .thenThrow(new MapException("No saved route from 'id-A' to 'id-C'"));

        mockMvc.perform(get("/map/route").param("from", "id-A").param("to", "id-C"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No saved route from 'id-A' to 'id-C'"));
    }

    @Test
    void getRoute_regularUser_passesCallerToService() throws Exception {
        setAuth("user@x.com", "REGULAR");
        when(routeService.getRoute(eq("id-A"), eq("id-C"), argThat(u -> u.getUsername().equals("user@x.com"))))
                .thenReturn(sampleResponse());

        mockMvc.perform(get("/map/route").param("from", "id-A").param("to", "id-C"))
                .andExpect(status().isOk());
    }

    // ── DELETE /map/route ─────────────────────────────────────────────────────

    @Test
    void deleteRoute_returns204() throws Exception {
        mockMvc.perform(delete("/map/route").param("from", "id-A").param("to", "id-C"))
                .andExpect(status().isNoContent());

        verify(routeService).deleteRoute(eq("id-A"), eq("id-C"), any());
    }

    @Test
    void deleteRoute_notFound_returns409() throws Exception {
        doThrow(new MapException("No saved route from 'id-A' to 'id-C'"))
                .when(routeService).deleteRoute(eq("id-A"), eq("id-C"), any());

        mockMvc.perform(delete("/map/route").param("from", "id-A").param("to", "id-C"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No saved route from 'id-A' to 'id-C'"));
    }

    @Test
    void deleteRoute_regularUser_passesCallerToService() throws Exception {
        setAuth("user@x.com", "REGULAR");

        mockMvc.perform(delete("/map/route").param("from", "id-A").param("to", "id-C"))
                .andExpect(status().isNoContent());

        verify(routeService).deleteRoute(eq("id-A"), eq("id-C"),
                argThat(u -> u.getUsername().equals("user@x.com")));
    }

    // ── GET /map/routes ───────────────────────────────────────────────────────

    @Test
    void getAllRoutes_returns200WithList() throws Exception {
        when(routeService.getAllRoutes(any())).thenReturn(List.of(
                sampleResponse(),
                new SavedRouteResponse("id-X", "X", "id-Y", "Y", 3,
                        List.of(new PathSegment("id-X", "X", "id-Y", "Y", 3)), List.of("admin"))
        ));

        mockMvc.perform(get("/map/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nodeA").value("A"))
                .andExpect(jsonPath("$[1].nodeA").value("X"));
    }

    @Test
    void getAllRoutes_empty_returns200WithEmptyList() throws Exception {
        when(routeService.getAllRoutes(any())).thenReturn(List.of());

        mockMvc.perform(get("/map/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAllRoutes_regularUser_passesCallerToService() throws Exception {
        setAuth("user@x.com", "REGULAR");
        when(routeService.getAllRoutes(argThat(u -> u.getUsername().equals("user@x.com"))))
                .thenReturn(List.of());

        mockMvc.perform(get("/map/routes"))
                .andExpect(status().isOk());
    }
}