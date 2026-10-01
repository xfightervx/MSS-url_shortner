package mss.url.service;

import mss.url.dto.CreateLinkRequest;
import mss.url.dto.LinkResponse;
import mss.url.exception.*;
import mss.url.model.mss_transaction.Url;
import mss.url.model.mss_transaction.User;
import mss.url.repository.UrlRepository;
import mss.url.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.zip.CRC32;
@Service
public class UrlService {
    private final UrlRepository urlRepository;
    private final UserRepository userRepository;
    private final String baseUrl;

    public UrlService(UrlRepository urlRepository,
            UserRepository userRepository,
            ApplicationEventPublisher events,
            @Value("${app.base-url}") String baseUrl) {
        this.urlRepository = urlRepository;
        this.userRepository = userRepository;
        this.baseUrl = baseUrl;
    }

    @Transactional
    /**
     * Creates a short link for the requested URL and user.
     * it require the user to exist in the database, otherwise it will throw an InvalidRequestException.
     * it generate a short code by getting the first 8 characters of the CRC32 hash of the concatenation of the user id and the requested URL.
     * one it detecte a collision, it will increment the user id by 1e9 and generate a new code until the uniqueness is insured
     * @param request the link creation request
     * @return the created link details
     * @throws InvalidRequestException if the requested user does not exist
     */
    public LinkResponse create(CreateLinkRequest request) {
        
        String target = request.url() == null ? null : request.url().trim();
        if (!validUser(request.user_id())) {
            throw new InvalidRequestException("User not found");
        }
        int userid = request.user_id();
        String code;
        do {
            code = resolveCode(request.url(), userid);
            userid += 1e9;
        } while (validUrl(code));
        Url url = new Url();
        url.setShortUrl(code);
        url.setUrl(target);
        urlRepository.save(url);

        return toResponse(url);
    }

    @Transactional(readOnly = true)
    public LinkResponse get(String code) {
        return urlRepository.findByShortUrl(code)
                .map(this::toResponse)
                .orElseThrow(() -> new LinkNotFoundException(code));
    }

    private String resolveCode(String url, int userId) {
        CRC32 crc32 = new CRC32();
        crc32.update((userId + url).getBytes());
        return Long.toHexString(crc32.getValue()).substring(0, 8);
    }

    private boolean validUrl(String hash) {
        Url url = urlRepository.findByShortUrl(hash).orElse(null);
        return url != null;
    }

    private boolean validUser(int userId) {
        User user = userRepository.findById(userId).orElse(null);
        return user != null;
    }

    private LinkResponse toResponse(Url url) {
        return new LinkResponse(url.getShortUrl(), baseUrl + "/redirect/" + url.getShortUrl(), url.getUrl());
    }
}
