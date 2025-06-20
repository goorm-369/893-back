package com.samyookgoo.palgoosam.auth.service;

import com.samyookgoo.palgoosam.user.domain.User;
import com.samyookgoo.palgoosam.user.exception.UserUnauthorizedException;
import com.samyookgoo.palgoosam.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;

    /**
     * SecurityContext 에 저장된 authentication 으로부터 User 엔티티 조회
     */
    public User getCurrentUser() {
        String providerId = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
        return userRepository.findByProviderId(providerId)
                .orElse(null);
    }

    public User getAuthorizedUser(User user) {
        if (user == null) {
            throw new UserUnauthorizedException();
        }
        return user;
    }
}