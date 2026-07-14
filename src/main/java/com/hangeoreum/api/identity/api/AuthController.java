package com.hangeoreum.api.identity.api;

import com.hangeoreum.api.identity.application.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;

    public record RegisterRequest(@NotBlank @Size(max = 100) String name,
                                  @NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 100) String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record AuthResponse(String accessToken, UserDto user) {
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody @jakarta.validation.Valid RegisterRequest request) {
        AuthService.TokenPair pair = authService.register(request.name(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(pair.refreshToken(), authService.refreshTtl()).toString())
                .body(new AuthResponse(pair.accessToken(), UserDto.from(pair.user())));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @jakarta.validation.Valid LoginRequest request) {
        AuthService.TokenPair pair = authService.login(request.email(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(pair.refreshToken(), authService.refreshTtl()).toString())
                .body(new AuthResponse(pair.accessToken(), UserDto.from(pair.user())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        AuthService.TokenPair pair = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(pair.refreshToken(), authService.refreshTtl()).toString())
                .body(new AuthResponse(pair.accessToken(), UserDto.from(pair.user())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    private static ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .path("/api/v1/auth")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
    }
}
