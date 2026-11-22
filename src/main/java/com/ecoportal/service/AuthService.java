package com.ecoportal.service;

import com.ecoportal.domain.User;
import com.ecoportal.domain.UserRole;
import com.ecoportal.dto.LoginRequest;
import com.ecoportal.dto.RegisterRequest;
import com.ecoportal.dto.UserDto;
import com.ecoportal.exception.BadRequestException;
import com.ecoportal.exception.ConflictException;
import com.ecoportal.repository.UserRepository;
import com.ecoportal.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;
    private final String dummyHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       SecurityContextRepository securityContextRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
        // Хеш-заглушка вирівнює час відповіді, коли користувача з таким email не існує.
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * Реєстрація завжди створює користувача з роллю PARTICIPANT.
     * Метод навмисно без @Transactional: порушення унікальності обробляється тут.
     */
    public UserDto register(RegisterRequest request) {
        String email = request.email().trim();
        String fullName = request.fullName().trim();
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BadRequestException("VALIDATION_ERROR", "Пароль занадто довгий (максимум 72 байти)");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw emailTaken();
        }
        User user = new User(fullName, email, passwordEncoder.encode(request.password()), UserRole.PARTICIPANT);
        try {
            return UserDto.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            throw emailTaken();
        }
    }

    public UserDto login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches) {
            throw new BadCredentialsException("Невірний email або пароль");
        }

        // Захист від фіксації сесії: наявна сесія отримує новий ідентифікатор.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        } else {
            httpRequest.getSession(true);
        }

        AuthenticatedUser principal =
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return UserDto.from(user);
    }

    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        new SecurityContextLogoutHandler().logout(
                httpRequest, httpResponse, SecurityContextHolder.getContext().getAuthentication());
    }

    public UserDto currentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserDto::from)
                .orElseThrow(() -> new BadCredentialsException("Сесія недійсна, увійдіть повторно"));
    }

    private ConflictException emailTaken() {
        return new ConflictException("EMAIL_ALREADY_EXISTS", "Користувач із таким email уже зареєстрований");
    }
}
