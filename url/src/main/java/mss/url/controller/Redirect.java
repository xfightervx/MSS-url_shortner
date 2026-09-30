package mss.url.controller;

import mss.url.model.Url;
import mss.url.repository.UrlRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class Redirect {
    private final UrlRepository urlRepository;

    public Redirect(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    @GetMapping("/redirect/{shortUrl}")
    public ResponseEntity<Void> redirect(@PathVariable String shortUrl) {
        Url url = urlRepository.findByShortUrl(shortUrl)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "URL not found for short URL: " + shortUrl));

        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, url.getLongUrl())
                .build();
    }
}
