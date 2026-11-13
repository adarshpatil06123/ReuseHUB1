package com.reusehub.auth;

import com.reusehub.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
  private static final String COOKIE_NAME = "rh_access";

  private final JwtService jwtService;
  private final UserRepository userRepository;

  public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String token = resolveToken(request);
    if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        JwtClaims claims = jwtService.parse(token);
        var user = userRepository.findById(claims.userId()).orElse(null);
        if (user != null && user.isActive() && user.isEmailVerified()) {
          var principal = new UserPrincipal(user.getId(), user.getEmail());
          var auth = new UsernamePasswordAuthenticationToken(
              principal,
              null,
              principal.getAuthorities()
          );
          auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      } catch (Exception ex) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=invalid_token");
      }
    }

    filterChain.doFilter(request, response);
  }

  private String resolveToken(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return null;
    }
    Cookie cookie = Arrays.stream(request.getCookies())
        .filter(c -> COOKIE_NAME.equals(c.getName()))
        .findFirst()
        .orElse(null);
    return cookie != null ? cookie.getValue() : null;
  }
}
