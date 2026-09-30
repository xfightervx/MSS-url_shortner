package mss.url.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import mss.url.service.UrlService;

@RestController
public class Redirect {

    private final UrlService urlService;

    public Redirect(UrlService urlService) {
        this.urlService = urlService;
    }

    @GetMapping("/redirect/{shortUrl}")
    public ResponseEntity<Void> redirect(@PathVariable String shortUrl, HttpServletRequest request) {
        String target = urlService.resolve(shortUrl, clientIp(request));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
