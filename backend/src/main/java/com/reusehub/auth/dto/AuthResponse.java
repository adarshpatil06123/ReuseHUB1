package com.reusehub.auth.dto;

import java.time.Instant;

public record AuthResponse(Instant tokenExpiresAt) {}
