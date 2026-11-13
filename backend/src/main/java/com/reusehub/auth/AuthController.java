package com.reusehub.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reusehub.auth.dto.AuthResponse;
import com.reusehub.auth.dto.LoginRequest;
import com.reusehub.auth.dto.RegisterRequest;
import com.reusehub.auth.dto.VerifyEmailRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {
  private static final String COOKIE_NAME = "rh_access";

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(
      @Valid @RequestBody RegisterRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response
  ) {
    JwtToken token = authService.register(
        request.email(),
        request.password(),
        request.name(),
      request.department(),
      request.college()
    );
    setCookie(httpRequest, response, token);
    return ResponseEntity.ok(new AuthResponse(token.expiresAt()));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response
  ) {
    JwtToken token = authService.login(request.email(), request.password());
    setCookie(httpRequest, response, token);
    return ResponseEntity.ok(new AuthResponse(token.expiresAt()));
  }

  @PostMapping("/verify-email")
  public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    authService.verifyEmail(request.token());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      HttpServletRequest httpRequest,
      HttpServletResponse response
  ) {
    ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, "")
        .httpOnly(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ZERO);
    applyLocalhostDomain(httpRequest, builder);
    ResponseCookie cookie = builder.build();
    response.addHeader("Set-Cookie", cookie.toString());
    return ResponseEntity.noContent().build();
  }

  private void setCookie(
      HttpServletRequest httpRequest,
      HttpServletResponse response,
      JwtToken token
  ) {
    long seconds = Duration.between(Instant.now(), token.expiresAt()).getSeconds();
    ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, token.token())
        .httpOnly(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ofSeconds(seconds));
    applyLocalhostDomain(httpRequest, builder);
    ResponseCookie cookie = builder.build();
    response.addHeader("Set-Cookie", cookie.toString());
  }

  private void applyLocalhostDomain(
      HttpServletRequest request,
      ResponseCookie.ResponseCookieBuilder builder
  ) {
    String host = request.getServerName();
    if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
      builder.domain("localhost");
    }
  }
}
