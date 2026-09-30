package mss.url.repository;

import mss.url.model.mss_transaction.Url;
import org.springframework.data.repository.Repository;
import java.util.Optional;

public interface UrlRepository extends Repository<Url, String> {
    Optional<Url> findByShortUrl(String shortUrl);
}