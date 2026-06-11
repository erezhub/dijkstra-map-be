package com.eRez.map.controller;

import com.eRez.map.dto.response.SavedRouteResponse;
import com.eRez.map.services.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    public SavedRouteResponse saveRoute(@RequestParam String from, @RequestParam String to) {
        return routeService.saveRoute(from, to);
    }

    @GetMapping("/route")
    @ResponseStatus(HttpStatus.OK)
    public SavedRouteResponse getRoute(@RequestParam String from, @RequestParam String to) {
        return routeService.getRoute(from, to);
    }

    @DeleteMapping("/route")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoute(@RequestParam String from, @RequestParam String to) {
        routeService.deleteRoute(from, to);
    }

    @GetMapping("/routes")
    @ResponseStatus(HttpStatus.OK)
    public List<SavedRouteResponse> getAllRoutes() {
        return routeService.getAllRoutes();
    }
}
