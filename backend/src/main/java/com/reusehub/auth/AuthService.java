package com.reusehub.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.reusehub.common.ApiException;
import com.reusehub.config.AppProperties;
import com.reusehub.user.User;
import com.reusehub.user.UserRepository;

@Service
public class AuthService {
  private static final int VERIFICATION_TOKEN_BYTES = 32;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final EmailService emailService;
  private final AppProperties appProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      EmailService emailService,
      AppProperties appProperties
  ) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.emailService = emailService;
    this.appProperties = appProperties;
  }

  public JwtToken register(
      String email,
      String password,
      String name,
      String department,
      String college
  ) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    if (!isAllowedDomain(normalizedEmail)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Email domain not allowed");
    }
    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
    }

    User user = new User();
    user.setEmail(normalizedEmail);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setName(name);
    user.setDepartment(department);
    user.setCollege(college);
    user.setActive(true);
    user.setEmailVerified(appProperties.isAutoVerifyEmail());

    String rawToken = null;
    if (!user.isEmailVerified()) {
      rawToken = generateToken();
      user.setVerificationTokenHash(hashToken(rawToken));
      user.setVerificationTokenExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
    }

    userRepository.save(user);

    if (!user.isEmailVerified() && rawToken != null) {
      emailService.sendEmailVerification(
        user.getEmail(),
        "https://reusehub.local/verify?token=" + rawToken
      );
    }

    return jwtService.createAccessToken(user.getId(), user.getEmail());
  }

  public JwtToken login(String email, String password) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    User user = userRepository.findByEmail(normalizedEmail)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

    if (!user.isActive()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Account inactive");
    }
    if (!user.isEmailVerified()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Email not verified");
    }
    String storedHash = user.getPasswordHash();
    boolean matches;
    if (storedHash != null && storedHash.startsWith("$2")) {
      matches = passwordEncoder.matches(password, storedHash);
    } else {
      matches = password.equals(storedHash);
      if (matches) {
        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
      }
    }

    if (!matches) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    return jwtService.createAccessToken(user.getId(), user.getEmail());
  }

  public void verifyEmail(String rawToken) {
    String hashed = hashToken(rawToken);
    User user = userRepository.findByVerificationTokenHash(hashed)
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid token"));

    if (user.getVerificationTokenExpiresAt() == null
        || user.getVerificationTokenExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Token expired");
    }

    user.setEmailVerified(true);
    user.setVerificationTokenHash(null);
    user.setVerificationTokenExpiresAt(null);
    userRepository.save(user);
  }

  private boolean isAllowedDomain(String email) {
    int at = email.lastIndexOf('@');
    if (at < 0) {
      return false;
    }
    String domain = email.substring(at + 1);
    return appProperties.getAllowedEmailDomains().stream()
        .anyMatch(allowed -> allowed.equalsIgnoreCase(domain));
  }

  private String generateToken() {
    byte[] bytes = new byte[VERIFICATION_TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Token hashing failed");
    }
  }
}
