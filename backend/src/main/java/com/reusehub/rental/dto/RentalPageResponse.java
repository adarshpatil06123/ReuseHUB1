package com.reusehub.rental.dto;

import java.util.List;

public record RentalPageResponse(
    List<RentalResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
