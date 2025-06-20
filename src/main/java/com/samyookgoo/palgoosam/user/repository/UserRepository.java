package com.samyookgoo.palgoosam.user.repository;

import com.samyookgoo.palgoosam.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
    Optional<User> findByProviderId(String providerId);
}