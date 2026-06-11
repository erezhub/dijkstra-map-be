package com.eRez.map.controller;

import com.eRez.map.dto.response.SavedRouteResponse;
import com.eRez.map.services.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @PostMapping("/route")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedRouteResponse saveRoute(@AuthenticationPrincipal UserDetails caller,
                                        @RequestParam String from, @RequestParam String to) {
        return routeService.saveRoute(from, to, caller);
    }

    @GetMapping("/route")
    @ResponseStatus(HttpStatus.OK)
    public SavedRouteResponse getRoute(@AuthenticationPrincipal UserDetails caller,
                                       @RequestParam String from, @RequestParam String to) {
        return routeService.getRoute(from, to, caller);
    }

    @DeleteMapping("/route")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoute(@AuthenticationPrincipal UserDetails caller,
                            @RequestParam String from, @RequestParam String to) {
        routeService.deleteRoute(from, to, caller);
    }

    @GetMapping("/routes")
    @ResponseStatus(HttpStatus.OK)
    public List<SavedRouteResponse> getAllRoutes(@AuthenticationPrincipal UserDetails caller) {
        return routeService.getAllRoutes(caller);
    }
}
