package com.eRez.map.services;

import com.eRez.map.database.document.RouteDocument;
import com.eRez.map.database.repository.RouteRepository;
import com.eRez.map.dto.event.RouteRecalculatedEvent;
import com.eRez.map.dto.response.PathResponse;
import com.eRez.map.dto.response.PathSegment;
import com.eRez.map.dto.response.SavedRouteResponse;
import com.eRez.map.exception.MapException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
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
    private final UserLookupService userLookupService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key.route-recalculated}")
    private String routeRecalculatedKey;

    public Optional<SavedRouteResponse> findCachedRoute(String from, String to) {
        return routeRepository.findRoute(from, to)
                .filter(r -> !r.isStale())
                .map(r -> toResponse(r, !r.getNodeA().equals(from)));
    }

    public SavedRouteResponse saveRoute(String from, String to, UserDetails caller) {
        PathResponse pathResponse = pathService.getPath(from, to);
        RouteDocument route = routeRepository.findRoute(from, to).orElse(new RouteDocument());
        route.setNodeA(from);
        route.setNodeB(to);
        if (route.getCreatedBy() == null) {
            route.setCreatedBy(new ArrayList<>());
        }
        if (!route.getCreatedBy().contains(caller.getUsername())) {
            route.getCreatedBy().add(caller.getUsername());
        }
        populateFromPathResponse(route, pathResponse);
        routeRepository.save(route);
        log.info("Route saved: {} → {} (distance: {}) by {}", from, to, pathResponse.getDistance(), caller.getUsername());
        return toResponse(route, false);
    }

    public SavedRouteResponse getRoute(String from, String to, UserDetails caller) {
        RouteDocument route = findRouteForCaller(from, to, caller);
        if (route.isStale()) {
            route = recalculateRoute(route);
        }
        return toResponse(route, !route.getNodeA().equals(from));
    }

    public void deleteRoute(String from, String to, UserDetails caller) {
        RouteDocument route = findRouteForCaller(from, to, caller);
        if (isRegular(caller)) {
            route.getCreatedBy().remove(caller.getUsername());
            if (route.getCreatedBy().isEmpty()) {
                routeRepository.delete(route);
            } else {
                routeRepository.save(route);
            }
        } else {
            routeRepository.delete(route);
        }
        log.info("Route deleted: {} ↔ {} by {}", from, to, caller.getUsername());
    }

    public List<SavedRouteResponse> getAllRoutes(UserDetails caller) {
        List<RouteDocument> routes = isRegular(caller)
                ? routeRepository.findByCreatedByContaining(caller.getUsername())
                : routeRepository.findAll();
        return routes.stream()
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

    private RouteDocument findRouteForCaller(String from, String to, UserDetails caller) {
        String notFoundMsg = "No saved route from '" + from + "' to '" + to + "'";
        if (isRegular(caller)) {
            return routeRepository.findRouteByCreator(from, to, caller.getUsername())
                    .orElseThrow(() -> new MapException(notFoundMsg));
        }
        return routeRepository.findRoute(from, to)
                .orElseThrow(() -> new MapException(notFoundMsg));
    }

    private boolean isRegular(UserDetails caller) {
        return caller.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_REGULAR"));
    }

    private RouteDocument recalculateRoute(RouteDocument route) {
        int oldDistance = route.getDistance();
        List<String> oldPath = route.getPath() != null ? List.copyOf(route.getPath()) : List.of();

        PathResponse pathResponse = pathService.getPath(route.getNodeA(), route.getNodeB());
        populateFromPathResponse(route, pathResponse);
        routeRepository.save(route);
        log.info("Route recalculated: {} → {} (distance: {})", route.getNodeA(), route.getNodeB(), pathResponse.getDistance());

        List<String> newPath = route.getPath() != null ? route.getPath() : List.of();
        boolean changed = route.getDistance() != oldDistance || !newPath.equals(oldPath);
        if (changed) {
            List<String> recipients = userLookupService.resolveRecipients(route.getCreatedBy());
            if (!recipients.isEmpty()) {
                rabbitTemplate.convertAndSend(exchange, routeRecalculatedKey,
                        new RouteRecalculatedEvent(route.getNodeA(), route.getNodeB(),
                                route.getDistance(), recipients));
                log.info("Route update notification published for {} ↔ {} to {} recipient(s)",
                        route.getNodeA(), route.getNodeB(), recipients.size());
            }
        }
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
        return new SavedRouteResponse(nodeA, nodeB, route.getDistance(), segments, route.getCreatedBy());
    }
}