package com.eRez.map.services;

import com.eRez.map.data.CacheData;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;
    private final PathService pathService;
    private final UserLookupService userLookupService;
    private final RabbitTemplate rabbitTemplate;
    private final CacheData cacheData;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key.route-recalculated}")
    private String routeRecalculatedKey;

    public Optional<SavedRouteResponse> findCachedRoute(String fromId, String toId) {
        return routeRepository.findRoute(fromId, toId)
                .filter(r -> !r.isStale())
                .map(r -> toResponse(r, !r.getNodeA().equals(fromId)));
    }

    public SavedRouteResponse saveRoute(String fromId, String toId, UserDetails caller) {
        PathResponse pathResponse = pathService.getPath(fromId, toId);
        RouteDocument route = routeRepository.findRoute(fromId, toId).orElse(new RouteDocument());
        route.setNodeA(fromId);
        route.setNodeB(toId);
        if (route.getCreatedBy() == null) {
            route.setCreatedBy(new ArrayList<>());
        }
        if (!route.getCreatedBy().contains(caller.getUsername())) {
            route.getCreatedBy().add(caller.getUsername());
        }
        populateFromPathResponse(route, pathResponse);
        routeRepository.save(route);
        Map<String, String> idToName = cacheData.getIdToName();
        log.info("Route saved: {} → {} (distance: {}) by {}",
                idToName.getOrDefault(fromId, fromId), idToName.getOrDefault(toId, toId),
                pathResponse.getDistance(), caller.getUsername());
        return toResponse(route, false);
    }

    public SavedRouteResponse getRoute(String fromId, String toId, UserDetails caller) {
        RouteDocument route = findRouteForCaller(fromId, toId, caller);
        if (route.isStale()) {
            route = recalculateRoute(route);
        }
        return toResponse(route, !route.getNodeA().equals(fromId));
    }

    public void deleteRoute(String fromId, String toId, UserDetails caller) {
        RouteDocument route = findRouteForCaller(fromId, toId, caller);
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
        Map<String, String> idToName = cacheData.getIdToName();
        log.info("Route deleted: {} ↔ {} by {}",
                idToName.getOrDefault(fromId, fromId), idToName.getOrDefault(toId, toId), caller.getUsername());
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

    public void deleteByEndpoint(String nodeId) {
        routeRepository.deleteByNodeAOrNodeB(nodeId, nodeId);
        log.info("Deleted routes with endpoint id '{}'", nodeId);
    }

    public void markStaleByPath(String nodeId) {
        List<RouteDocument> affected = routeRepository.findByPathContaining(nodeId);
        if (affected.isEmpty()) return;
        affected.forEach(r -> r.setStale(true));
        routeRepository.saveAll(affected);
        log.info("Marked {} route(s) as stale (containing id '{}')", affected.size(), nodeId);
    }

    public void deleteAll() {
        routeRepository.deleteAll();
        log.info("All saved routes deleted");
    }

    public void recalculateAllStale() {
        List<RouteDocument> staleRoutes = routeRepository.findByStaleTrue();
        if (staleRoutes.isEmpty()) return;
        log.info("Recalculating {} stale route(s)", staleRoutes.size());
        Map<String, String> idToName = cacheData.getIdToName();
        for (RouteDocument route : staleRoutes) {
            try {
                recalculateRoute(route);
            } catch (MapException e) {
                routeRepository.delete(route);
                log.info("Route {} ↔ {} deleted ({})",
                        idToName.getOrDefault(route.getNodeA(), route.getNodeA()),
                        idToName.getOrDefault(route.getNodeB(), route.getNodeB()),
                        e.getMessage());
            }
        }
    }

    private RouteDocument findRouteForCaller(String fromId, String toId, UserDetails caller) {
        String notFoundMsg = "No saved route from '" + fromId + "' to '" + toId + "'";
        if (isRegular(caller)) {
            return routeRepository.findRouteByCreator(fromId, toId, caller.getUsername())
                    .orElseThrow(() -> new MapException(notFoundMsg));
        }
        return routeRepository.findRoute(fromId, toId)
                .orElseThrow(() -> new MapException(notFoundMsg));
    }

    private boolean isRegular(UserDetails caller) {
        return caller.getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_REGULAR"));
    }

    private RouteDocument recalculateRoute(RouteDocument route) {
        int oldDistance = route.getDistance();
        List<String> oldPath = route.getPath() != null ? List.copyOf(route.getPath()) : List.of();

        Map<String, String> idToName = cacheData.getIdToName();
        String nodeAName = idToName.getOrDefault(route.getNodeA(), route.getNodeA());
        String nodeBName = idToName.getOrDefault(route.getNodeB(), route.getNodeB());
        PathResponse pathResponse = pathService.getPath(route.getNodeA(), route.getNodeB());
        populateFromPathResponse(route, pathResponse);
        routeRepository.save(route);
        log.info("Route recalculated: {} → {} (distance: {})", nodeAName, nodeBName, pathResponse.getDistance());

        List<String> newPath = route.getPath() != null ? route.getPath() : List.of();
        boolean changed = route.getDistance() != oldDistance || !newPath.equals(oldPath);
        if (changed) {
            List<String> recipients = userLookupService.resolveRecipients(route.getCreatedBy());
            if (!recipients.isEmpty()) {
                rabbitTemplate.convertAndSend(exchange, routeRecalculatedKey,
                        new RouteRecalculatedEvent(nodeAName, nodeBName, route.getDistance(), recipients));
                log.info("Route update notification published for {} ↔ {} to {} recipient(s)",
                        nodeAName, nodeBName, recipients.size());
            }
        }
        return route;
    }

    private void populateFromPathResponse(RouteDocument route, PathResponse pathResponse) {
        List<String> path = new ArrayList<>();
        List<Integer> segmentDistances = new ArrayList<>();
        if (!pathResponse.getPath().isEmpty()) {
            path.add(pathResponse.getPath().get(0).getFromId());
            for (PathSegment seg : pathResponse.getPath()) {
                path.add(seg.getToId());
                segmentDistances.add(seg.getDistance());
            }
        }
        route.setPath(path);
        route.setSegmentDistances(segmentDistances);
        route.setDistance(pathResponse.getDistance());
        route.setStale(false);
    }

    private SavedRouteResponse toResponse(RouteDocument route, boolean reversed) {
        List<String> pathIds = new ArrayList<>(route.getPath());
        List<Integer> dists = new ArrayList<>(route.getSegmentDistances());
        if (reversed) {
            Collections.reverse(pathIds);
            Collections.reverse(dists);
        }
        List<PathSegment> segments = new ArrayList<>();
        Map<String, String> idToName = cacheData.getIdToName();
        for (int i = 0; i < pathIds.size() - 1; i++) {
            String fId = pathIds.get(i);
            String tId = pathIds.get(i + 1);
            segments.add(new PathSegment(fId, idToName.getOrDefault(fId, fId), tId, idToName.getOrDefault(tId, tId), dists.get(i)));
        }
        String aId = reversed ? route.getNodeB() : route.getNodeA();
        String bId = reversed ? route.getNodeA() : route.getNodeB();
        return new SavedRouteResponse(aId, idToName.getOrDefault(aId, aId), bId, idToName.getOrDefault(bId, bId),
                route.getDistance(), segments, route.getCreatedBy());
    }
}