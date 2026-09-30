package mss.url.service;

import mss.url.dto.CreateLinkRequest;
import mss.url.dto.LinkResponse;
import mss.url.event.UrlHitEvent;
import mss.url.exception.*;
import mss.url.model.mss_transaction.Url;
import mss.url.repository.UrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class UrlService {

    private static final String ALPHABET
            = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 7;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");

    private final SecureRandom random = new SecureRandom();
    private final UrlRepository urlRepository;
    private final ApplicationEventPublisher events;
    private final String baseUrl;

    public UrlService(UrlRepository urlRepository,
            ApplicationEventPublisher events,
            @Value("${app.base-url}") String baseUrl) {
        this.urlRepository = urlRepository;
        this.events = events;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public LinkResponse create(CreateLinkRequest request) {
        String target = request.url() == null ? null : request.url().trim();
        validateTargetUrl(target);

        String code = resolveCode(request.alias());

        Url url = new Url();
        url.setShortUrl(code);
        url.setUrl(target);
        urlRepository.save(url);

        return toResponse(url);
    }

    @Transactional(readOnly = true)
    public String resolve(String code, String ipAddress) {
        Url url = urlRepository.findByShortUrl(code)
                .orElseThrow(() -> new LinkNotFoundException(code));
        events.publishEvent(new UrlHitEvent(code, ipAddress, Instant.now()));
        return url.getUrl();
    }

    @Transactional(readOnly = true)
    public LinkResponse get(String code) {
        return urlRepository.findByShortUrl(code)
                .map(this::toResponse)
                .orElseThrow(() -> new LinkNotFoundException(code));
    }

    private String resolveCode(String alias) {
        if (alias != null && !alias.isBlank()) {
            if (!ALIAS_PATTERN.matcher(alias).matches()) {
                throw new InvalidRequestException(
                        "Alias must be 3-32 characters: letters, digits, '-' or '_'");
            }
            if (urlRepository.existsById(alias)) {
                throw new AliasTakenException(alias);
            }
            return alias;
        }
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = randomCode();
            if (!urlRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique code");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private void validateTargetUrl(String target) {
        if (target == null || target.isEmpty() || target.length() > MAX_URL_LENGTH) {
            throw new InvalidRequestException("URL is required and must be at most 2048 characters");
        }
        URI uri;
        try {
            uri = new URI(target);
        } catch (URISyntaxException e) {
            throw new InvalidRequestException("Malformed URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new InvalidRequestException("URL must be an absolute http(s) URL");
        }
    }

    private LinkResponse toResponse(Url url) {
        return new LinkResponse(url.getShortUrl(), baseUrl + "/redirect/" + url.getShortUrl(), url.getUrl());
    }
}
