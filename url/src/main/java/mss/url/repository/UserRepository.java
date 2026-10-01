package mss.url.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import mss.url.model.mss_transaction.User;

public interface UserRepository extends JpaRepository<User, Integer> {
}
