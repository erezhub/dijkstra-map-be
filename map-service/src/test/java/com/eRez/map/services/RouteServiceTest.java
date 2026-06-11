package com.eRez.map.services;

import com.eRez.map.database.document.RouteDocument;
import com.eRez.map.database.repository.RouteRepository;
import com.eRez.map.dto.response.PathResponse;
import com.eRez.map.dto.response.PathSegment;
import com.eRez.map.dto.response.SavedRouteResponse;
import com.eRez.map.exception.MapException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock RouteRepository routeRepository;
    @Mock PathService pathService;

    @InjectMocks RouteService routeService;

    private PathResponse abPath;

    @BeforeEach
    void setUp() {
        abPath = new PathResponse(10, List.of(
                new PathSegment("A", "B", 4),
                new PathSegment("B", "C", 6)
        ));
    }

    // ── saveRoute ─────────────────────────────────────────────────────────────

    @Test
    void saveRoute_calculatesAndPersists() {
        when(pathService.getPath("A", "C")).thenReturn(abPath);
        when(routeRepository.findRoute("A", "C")).thenReturn(Optional.empty());
        when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavedRouteResponse response = routeService.saveRoute("A", "C", caller("admin", "ADMIN"));

        assertThat(response.getNodeA()).isEqualTo("A");
        assertThat(response.getNodeB()).isEqualTo("C");
        assertThat(response.getDistance()).isEqualTo(10);
        assertThat(response.getPath()).hasSize(2);
        assertThat(response.getPath().get(0).getFrom()).isEqualTo("A");
        assertThat(response.getPath().get(1).getTo()).isEqualTo("C");

        ArgumentCaptor<RouteDocument> captor = ArgumentCaptor.forClass(RouteDocument.class);
        verify(routeRepository).save(captor.capture());
        assertThat(captor.getValue().isStale()).isFalse();
    }

    @Test
    void saveRoute_setsCreatedBy_newDocument() {
        when(pathService.getPath("A", "C")).thenReturn(abPath);
        when(routeRepository.findRoute("A", "C")).thenReturn(Optional.empty());
        when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavedRouteResponse response = routeService.saveRoute("A", "C", caller("mgr@x.com", "MANAGER"));

        ArgumentCaptor<RouteDocument> captor = ArgumentCaptor.forClass(RouteDocument.class);
        verify(routeRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).containsExactly("mgr@x.com");
        assertThat(response.getCreatedBy()).containsExactly("mgr@x.com");
    }

    @Test
    void saveRoute_addsSecondUserToExistingCreatedByList() {
        RouteDocument existing = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, false,
                new ArrayList<>(List.of("user1@x.com")));
        when(pathService.getPath("A", "C")).thenReturn(abPath);
        when(routeRepository.findRoute("A", "C")).thenReturn(Optional.of(existing));
        when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        routeService.saveRoute("A", "C", caller("user2@x.com", "REGULAR"));

        assertThat(existing.getCreatedBy()).containsExactlyInAnyOrder("user1@x.com", "user2@x.com");
    }

    @Test
    void saveRoute_doesNotDuplicateUsernameIfSameCallerSavesAgain() {
        RouteDocument existing = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, false,
                new ArrayList<>(List.of("user1@x.com")));
        when(pathService.getPath("A", "C")).thenReturn(abPath);
        when(routeRepository.findRoute("A", "C")).thenReturn(Optional.of(existing));
        when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        routeService.saveRoute("A", "C", caller("user1@x.com", "REGULAR"));

        assertThat(existing.getCreatedBy()).containsExactly("user1@x.com");
    }

    @Test
    void saveRoute_upserts_existingRoute() {
        RouteDocument existing = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, false, null);
        when(pathService.getPath("A", "C")).thenReturn(abPath);
        when(routeRepository.findRoute("A", "C")).thenReturn(Optional.of(existing));
        when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        routeService.saveRoute("A", "C", caller("admin", "ADMIN"));

        verify(routeRepository, never()).delete(any());
        verify(routeRepository).save(existing);
    }

    // ── getRoute ──────────────────────────────────────────────────────────────

    @Test
    void getRoute_admin_usesUnrestrictedQuery() {
        RouteDocument route = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, false,
                List.of("someone@x.com"));
        when(routeRepository.findRoute("A", "C")).thenReturn(Optional.of(route));

        SavedRouteResponse response = routeService.getRoute("A", "C", caller("admin", "ADMIN"));

        assertThat(response.getNodeA()).isEqualTo("A");
        verify(routeRepository).findRoute("A", "C");
        verify(routeRepository, never()).findRouteByCreator(any(), any(), any());
        verifyNoInteractions(pathService);
    }

    @Test
    void getRoute_regular_usesOwnershipQuery() {
        RouteDocument route = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, false,
                List.of("user@x.com"));
        when(routeRepository.findRouteByCreator("A", "C", "user@x.com")).thenReturn(Optional.of(route));

        SavedRouteResponse response = routeService.getRoute("A", "C", caller("user@x.com", "REGULAR"));

        assertThat(response.getNodeA()).isEqualTo("A");
        verify(routeRepository).findRouteByCreator("A", "C", "user@x.com");
        verify(routeRepository, never()).findRoute(any(), any());
    }

    @Test
    void getRoute_regular_notInCreatedBy_throws() {
        when(routeRepository.findRouteByCreator("A", "C", "user@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeService.getRoute("A", "C", caller("user@x.com", "REGULAR")))
                .isInstanceOf(MapException.class)
                .hasMessage("No saved route from 'A' to 'C'");
    }

    @Test
    void getRoute_notStale_returnsCached() {
        RouteDocument route = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, false, null);
        when(routeRepository.findRoute("A", "C")).thenReturn(Optional.of(route));

        SavedRouteResponse response = routeService.getRoute("A", "C", caller("admin", "ADMIN"));

        assertThat(response.getNodeA()).isEqualTo("A");
        assertThat(response.getNodeB()).isEqualTo("C");
        verifyNoInteractions(pathService);
    }

    @Test
    void getRoute_stale_recalculatesOnTheFly() {
        RouteDocument route = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, true, null);
        when(routeRepository.findRoute("A", "C")).thenReturn(Optional.of(route));
        when(pathService.getPath("A", "C")).thenReturn(abPath);
        when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavedRouteResponse response = routeService.getRoute("A", "C", caller("admin", "ADMIN"));

        assertThat(response.getDistance()).isEqualTo(10);
        verify(pathService).getPath("A", "C");
        verify(routeRepository).save(route);
    }

    @Test
    void getRoute_notFound_throws() {
        when(routeRepository.findRoute("A", "C")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeService.getRoute("A", "C", caller("admin", "ADMIN")))
                .isInstanceOf(MapException.class);
    }

    @Test
    void getRoute_storedAsReverse_reversesPath() {
        // stored as C→A, user requests A→C
        RouteDocument route = routeDocument("C", "A", List.of("C", "B", "A"), List.of(6, 4), 10, false, null);
        when(routeRepository.findRoute("A", "C")).thenReturn(Optional.of(route));

        SavedRouteResponse response = routeService.getRoute("A", "C", caller("admin", "ADMIN"));

        assertThat(response.getNodeA()).isEqualTo("A");
        assertThat(response.getNodeB()).isEqualTo("C");
        assertThat(response.getPath().get(0).getFrom()).isEqualTo("A");
        assertThat(response.getPath().get(0).getTo()).isEqualTo("B");
        assertThat(response.getPath().get(1).getFrom()).isEqualTo("B");
        assertThat(response.getPath().get(1).getTo()).isEqualTo("C");
    }

    // ── deleteRoute ───────────────────────────────────────────────────────────

    @Test
    void deleteRoute_admin_deletesEntireDocument() {
        RouteDocument route = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, false,
                new ArrayList<>(List.of("user@x.com")));
        when(routeRepository.findRoute("A", "C")).thenReturn(Optional.of(route));

        routeService.deleteRoute("A", "C", caller("admin", "ADMIN"));

        verify(routeRepository).delete(route);
        verify(routeRepository, never()).save(any());
    }

    @Test
    void deleteRoute_regular_lastOwner_deletesDocument() {
        RouteDocument route = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, false,
                new ArrayList<>(List.of("user@x.com")));
        when(routeRepository.findRouteByCreator("A", "C", "user@x.com")).thenReturn(Optional.of(route));

        routeService.deleteRoute("A", "C", caller("user@x.com", "REGULAR"));

        verify(routeRepository).delete(route);
        verify(routeRepository, never()).save(any());
    }

    @Test
    void deleteRoute_regular_notLastOwner_removesFromListAndSaves() {
        RouteDocument route = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, false,
                new ArrayList<>(List.of("user1@x.com", "user2@x.com")));
        when(routeRepository.findRouteByCreator("A", "C", "user1@x.com")).thenReturn(Optional.of(route));

        routeService.deleteRoute("A", "C", caller("user1@x.com", "REGULAR"));

        assertThat(route.getCreatedBy()).containsExactly("user2@x.com");
        verify(routeRepository).save(route);
        verify(routeRepository, never()).delete(any());
    }

    @Test
    void deleteRoute_regular_notInCreatedBy_throws() {
        when(routeRepository.findRouteByCreator("A", "C", "user@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeService.deleteRoute("A", "C", caller("user@x.com", "REGULAR")))
                .isInstanceOf(MapException.class)
                .hasMessage("No saved route from 'A' to 'C'");
    }

    @Test
    void deleteRoute_notFound_throws() {
        when(routeRepository.findRoute("A", "C")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeService.deleteRoute("A", "C", caller("admin", "ADMIN")))
                .isInstanceOf(MapException.class);
    }

    // ── getAllRoutes ──────────────────────────────────────────────────────────

    @Test
    void getAllRoutes_admin_usesFinAll() {
        RouteDocument r1 = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, false,
                List.of("user@x.com"));
        when(routeRepository.findAll()).thenReturn(List.of(r1));

        List<SavedRouteResponse> result = routeService.getAllRoutes(caller("admin", "ADMIN"));

        assertThat(result).hasSize(1);
        verify(routeRepository).findAll();
        verify(routeRepository, never()).findByCreatedByContaining(any());
    }

    @Test
    void getAllRoutes_regular_usesOwnershipQuery() {
        RouteDocument own = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, false,
                List.of("user@x.com"));
        when(routeRepository.findByCreatedByContaining("user@x.com")).thenReturn(List.of(own));

        List<SavedRouteResponse> result = routeService.getAllRoutes(caller("user@x.com", "REGULAR"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeA()).isEqualTo("A");
        verify(routeRepository).findByCreatedByContaining("user@x.com");
        verify(routeRepository, never()).findAll();
    }

    @Test
    void getAllRoutes_returnsMappedList() {
        RouteDocument r1 = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, false, null);
        RouteDocument r2 = routeDocument("X", "Y", List.of("X", "Y"), List.of(3), 3, false, null);
        when(routeRepository.findAll()).thenReturn(List.of(r1, r2));

        List<SavedRouteResponse> result = routeService.getAllRoutes(caller("admin", "ADMIN"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getNodeA()).isEqualTo("A");
        assertThat(result.get(1).getNodeA()).isEqualTo("X");
    }

    // ── markAllStale ──────────────────────────────────────────────────────────

    @Test
    void markAllStale_updatesAllRoutes() {
        RouteDocument r1 = routeDocument("A", "C", List.of("A", "C"), List.of(5), 5, false, null);
        RouteDocument r2 = routeDocument("X", "Y", List.of("X", "Y"), List.of(3), 3, false, null);
        when(routeRepository.findAll()).thenReturn(List.of(r1, r2));

        routeService.markAllStale();

        assertThat(r1.isStale()).isTrue();
        assertThat(r2.isStale()).isTrue();
        verify(routeRepository).saveAll(List.of(r1, r2));
    }

    @Test
    void markAllStale_emptyList_doesNothing() {
        when(routeRepository.findAll()).thenReturn(List.of());

        routeService.markAllStale();

        verify(routeRepository, never()).saveAll(any());
    }

    @Test
    void markStaleByPath_marksOnlyAffectedRoutes() {
        RouteDocument affected = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, false, null);
        when(routeRepository.findByPathContaining("B")).thenReturn(List.of(affected));

        routeService.markStaleByPath("B");

        assertThat(affected.isStale()).isTrue();
        verify(routeRepository).saveAll(List.of(affected));
    }

    @Test
    void markStaleByPath_noMatch_doesNothing() {
        when(routeRepository.findByPathContaining("Z")).thenReturn(List.of());

        routeService.markStaleByPath("Z");

        verify(routeRepository, never()).saveAll(any());
    }

    @Test
    void deleteByEndpoint_callsRepository() {
        routeService.deleteByEndpoint("A");

        verify(routeRepository).deleteByNodeAOrNodeB("A", "A");
    }

    @Test
    void deleteAll_callsRepository() {
        routeService.deleteAll();

        verify(routeRepository).deleteAll();
    }

    // ── recalculateAllStale ───────────────────────────────────────────────────

    @Test
    void recalculateAllStale_recalculatesEachStaleRoute() {
        RouteDocument stale = routeDocument("A", "C", List.of("A", "B", "C"), List.of(4, 6), 10, true, null);
        when(routeRepository.findByStaleTrue()).thenReturn(List.of(stale));
        when(pathService.getPath("A", "C")).thenReturn(abPath);
        when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        routeService.recalculateAllStale();

        verify(pathService).getPath("A", "C");
        verify(routeRepository).save(stale);
        assertThat(stale.isStale()).isFalse();
    }

    @Test
    void recalculateAllStale_deletesRouteWhenPathServiceThrows() {
        RouteDocument stale = routeDocument("A", "GONE", List.of("A", "GONE"), List.of(5), 5, true, null);
        when(routeRepository.findByStaleTrue()).thenReturn(List.of(stale));
        when(pathService.getPath("A", "GONE")).thenThrow(new MapException("Node not found"));

        routeService.recalculateAllStale();

        verify(routeRepository).delete(stale);
        verify(routeRepository, never()).save(any());
    }

    @Test
    void recalculateAllStale_empty_doesNothing() {
        when(routeRepository.findByStaleTrue()).thenReturn(List.of());

        routeService.recalculateAllStale();

        verifyNoMoreInteractions(routeRepository);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserDetails caller(String username, String role) {
        return User.withUsername(username).password("")
                .authorities(new SimpleGrantedAuthority("ROLE_" + role)).build();
    }

    private RouteDocument routeDocument(String nodeA, String nodeB,
                                        List<String> path, List<Integer> segmentDistances,
                                        int distance, boolean stale, List<String> createdBy) {
        RouteDocument doc = new RouteDocument();
        doc.setNodeA(nodeA);
        doc.setNodeB(nodeB);
        doc.setPath(path);
        doc.setSegmentDistances(segmentDistances);
        doc.setDistance(distance);
        doc.setStale(stale);
        doc.setCreatedBy(createdBy != null ? new ArrayList<>(createdBy) : null);
        return doc;
    }
}
