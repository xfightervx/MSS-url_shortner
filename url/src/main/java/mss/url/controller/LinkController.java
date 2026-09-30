package mss.url.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mss.url.dto.CreateLinkRequest;
import mss.url.dto.LinkResponse;
import mss.url.service.UrlService;

@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final UrlService urlService;

    public LinkController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@RequestBody CreateLinkRequest request) {
        LinkResponse response = urlService.create(request);
        return ResponseEntity.created(URI.create("/api/links/" + response.code())).body(response);
    }

    @GetMapping("/{code}")
    public LinkResponse get(@PathVariable String code) {
        return urlService.get(code);
    }
}
