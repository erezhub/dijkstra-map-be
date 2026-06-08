package com.eRez.tests.controller;

import com.eRez.tests.dto.request.CreateMapRequest;
import com.eRez.tests.dto.request.NodeRequest;
import com.eRez.tests.dto.request.UpdateNodeRequest;
import com.eRez.tests.dto.response.MapResponse;
import com.eRez.tests.dto.response.PathResponse;
import com.eRez.tests.services.NodeService;
import com.eRez.tests.services.PathService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public MapResponse getMap() {
        return nodeService.getMap();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createMap(@RequestBody @Valid CreateMapRequest request) {
        nodeService.createMap(request);
    }

    @PostMapping("/node")
    @ResponseStatus(HttpStatus.CREATED)
    public void addNode(@RequestBody @Valid NodeRequest request) {
        nodeService.addNode(request);
    }

    @PutMapping("/node/{name}")
    @ResponseStatus(HttpStatus.OK)
    public void updateNode(@PathVariable String name, @RequestBody UpdateNodeRequest request) {
        nodeService.updateNode(name, request);
    }

    @DeleteMapping("/node/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNode(@PathVariable String name) {
        nodeService.deleteNode(name);
    }

    @GetMapping("/path")
    @ResponseStatus(HttpStatus.OK)
    public PathResponse getPath(@RequestParam String from, @RequestParam String to) {
        return pathService.getPath(from, to);
    }
}
