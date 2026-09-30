package mss.url.event;

import java.time.Instant;

public record UrlHitEvent(String code, String ipAddress, Instant hitTime) {

}
