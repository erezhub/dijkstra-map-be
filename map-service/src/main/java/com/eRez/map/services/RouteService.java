package com.eRez.map.services;

import com.eRez.map.database.document.RouteDocument;
import com.eRez.map.database.repository.RouteRepository;
import com.eRez.map.dto.response.PathResponse;
import com.eRez.map.dto.response.PathSegment;
import com.eRez.map.dto.response.SavedRouteResponse;
import com.eRez.map.exception.MapException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;
    private final PathService pathService;

    public Optional<SavedRouteResponse> findCachedRoute(String from, String to) {
        return routeRepository.findRoute(from, to)
                .filter(r -> !r.isStale())
                .map(r -> toResponse(r, !r.getNodeA().equals(from)));
    }

    public SavedRouteResponse saveRoute(String from, String to) {
        PathResponse pathResponse = pathService.getPath(from, to);
        RouteDocument route = routeRepository.findRoute(from, to).orElse(new RouteDocument());
        route.setNodeA(from);
        route.setNodeB(to);
        populateFromPathResponse(route, pathResponse);
        routeRepository.save(route);
        log.info("Route saved: {} → {} (distance: {})", from, to, pathResponse.getDistance());
        return toResponse(route, false);
    }

    public SavedRouteResponse getRoute(String from, String to) {
        RouteDocument route = routeRepository.findRoute(from, to)
                .orElseThrow(() -> new MapException("No saved route from '" + from + "' to '" + to + "'"));
        if (route.isStale()) {
            route = recalculateRoute(route);
        }
        return toResponse(route, !route.getNodeA().equals(from));
    }

    public void deleteRoute(String from, String to) {
        RouteDocument route = routeRepository.findRoute(from, to)
                .orElseThrow(() -> new MapException("No saved route from '" + from + "' to '" + to + "'"));
        routeRepository.delete(route);
        log.info("Route deleted: {} ↔ {}", from, to);
    }

    public List<SavedRouteResponse> getAllRoutes() {
        return routeRepository.findAll().stream()
                .map(r -> toResponse(r, false))
                .toList();
    }

    public void markAllStale() {
        List<RouteDocument> routes = routeRepository.findAll();
        if (routes.isEmpty()) return;
        routes.forEach(r -> r.setStale(true));
        routeRepository.saveAll(routes);
        log.info("Marked {} route(s) as stale", routes.size());
    }

    public void deleteByEndpoint(String nodeName) {
        routeRepository.deleteByNodeAOrNodeB(nodeName, nodeName);
        log.info("Deleted routes with endpoint '{}'", nodeName);
    }

    public void markStaleByPath(String nodeName) {
        List<RouteDocument> affected = routeRepository.findByPathContaining(nodeName);
        if (affected.isEmpty()) return;
        affected.forEach(r -> r.setStale(true));
        routeRepository.saveAll(affected);
        log.info("Marked {} route(s) as stale (containing '{}')", affected.size(), nodeName);
    }

    public void deleteAll() {
        routeRepository.deleteAll();
        log.info("All saved routes deleted");
    }

    public void recalculateAllStale() {
        List<RouteDocument> staleRoutes = routeRepository.findByStaleTrue();
        if (staleRoutes.isEmpty()) return;
        log.info("Recalculating {} stale route(s)", staleRoutes.size());
        for (RouteDocument route : staleRoutes) {
            try {
                recalculateRoute(route);
            } catch (MapException e) {
                routeRepository.delete(route);
                log.info("Route {} ↔ {} deleted ({})", route.getNodeA(), route.getNodeB(), e.getMessage());
            }
        }
    }

    private RouteDocument recalculateRoute(RouteDocument route) {
        PathResponse pathResponse = pathService.getPath(route.getNodeA(), route.getNodeB());
        populateFromPathResponse(route, pathResponse);
        routeRepository.save(route);
        log.info("Route recalculated: {} → {} (distance: {})", route.getNodeA(), route.getNodeB(), pathResponse.getDistance());
        return route;
    }

    private void populateFromPathResponse(RouteDocument route, PathResponse pathResponse) {
        List<String> path = new ArrayList<>();
        List<Integer> segmentDistances = new ArrayList<>();
        if (!pathResponse.getPath().isEmpty()) {
            path.add(pathResponse.getPath().get(0).getFrom());
            for (PathSegment seg : pathResponse.getPath()) {
                path.add(seg.getTo());
                segmentDistances.add(seg.getDistance());
            }
        }
        route.setPath(path);
        route.setSegmentDistances(segmentDistances);
        route.setDistance(pathResponse.getDistance());
        route.setStale(false);
    }

    private SavedRouteResponse toResponse(RouteDocument route, boolean reversed) {
        List<String> path = route.getPath();
        List<Integer> dists = route.getSegmentDistances();
        if (reversed) {
            path = new ArrayList<>(path);
            Collections.reverse(path);
            dists = new ArrayList<>(dists);
            Collections.reverse(dists);
        }
        List<PathSegment> segments = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            segments.add(new PathSegment(path.get(i), path.get(i + 1), dists.get(i)));
        }
        String nodeA = reversed ? route.getNodeB() : route.getNodeA();
        String nodeB = reversed ? route.getNodeA() : route.getNodeB();
        return new SavedRouteResponse(nodeA, nodeB, route.getDistance(), segments);
    }
}
