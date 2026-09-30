package mss.url.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import mss.url.model.mss_analytics.Raw;

public interface RawRepository extends JpaRepository<Raw, Integer> {
}
