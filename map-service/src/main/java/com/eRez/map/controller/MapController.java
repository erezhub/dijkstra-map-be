package com.eRez.map.controller;

import com.eRez.map.dto.request.AddNodeRequest;
import com.eRez.map.dto.request.CreateMapRequest;
import com.eRez.map.dto.request.UpdateNodeRequest;
import com.eRez.map.dto.response.MapResponse;
import com.eRez.map.dto.response.PathResponse;
import com.eRez.map.exception.MapException;
import com.eRez.map.services.NodeService;
import com.eRez.map.services.PathService;
import com.eRez.map.services.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
public class MapController {

    private final NodeService nodeService;
    private final PathService pathService;
    private final RouteService routeService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public MapResponse getMap() {
        return nodeService.getMap();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createMap(@AuthenticationPrincipal UserDetails caller,
                          @RequestBody @Valid CreateMapRequest request) {
        denyRegular(caller);
        nodeService.createMap(request);
    }

    @PostMapping("/node")
    @ResponseStatus(HttpStatus.CREATED)
    public void addNode(@AuthenticationPrincipal UserDetails caller,
                        @RequestBody @Valid AddNodeRequest request) {
        denyRegular(caller);
        nodeService.addNode(request);
    }

    @PutMapping("/node/{id}")
    @ResponseStatus(HttpStatus.OK)
    public void updateNode(@AuthenticationPrincipal UserDetails caller,
                           @PathVariable String id, @RequestBody UpdateNodeRequest request) {
        denyRegular(caller);
        nodeService.updateNode(id, request);
    }

    @DeleteMapping("/node/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNode(@AuthenticationPrincipal UserDetails caller,
                           @PathVariable String id) {
        denyRegular(caller);
        nodeService.deleteNode(id);
    }

    @GetMapping("/path")
    @ResponseStatus(HttpStatus.OK)
    public PathResponse getPath(@RequestParam String from, @RequestParam String to) {
        return routeService.findCachedRoute(from, to)
                .map(r -> new PathResponse(r.getDistance(), r.getPath()))
                .orElseGet(() -> pathService.getPath(from, to));
    }

    private void denyRegular(UserDetails caller) {
        boolean isRegular = caller.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_REGULAR"));
        if (isRegular) throw new MapException("Access denied");
    }
}