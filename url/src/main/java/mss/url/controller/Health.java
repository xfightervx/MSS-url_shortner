package mss.url.controller;

import org.springframework.web.bind.annotation.GetMapping;

public class Health {

    @GetMapping("/health")
    public String health() {
        return "Health check passed!";
    }

}
