package com.ecoportal.service;

import com.ecoportal.domain.User;
import com.ecoportal.domain.UserRole;
import com.ecoportal.exception.ForbiddenException;
import com.ecoportal.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Перевірка ролі COORDINATOR за актуальними даними з БД (а не лише за даними сесії).
 */
@Component
public class CoordinatorAccess {

    private final UserRepository userRepository;

    public CoordinatorAccess(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireCoordinator(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenException("Користувача не знайдено, увійдіть повторно"));
        if (user.getRole() != UserRole.COORDINATOR) {
            throw new ForbiddenException("Ця дія доступна лише координаторам");
        }
        return user;
    }
}
