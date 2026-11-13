package com.reusehub.auth;

import java.time.Instant;

public record JwtToken(String token, Instant expiresAt) {}
