package mss.url.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import mss.url.model.mss_transaction.Url;

public interface UrlRepository extends JpaRepository<Url, String> {

    Optional<Url> findByShortUrl(String shortUrl);
}
