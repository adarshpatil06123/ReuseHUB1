package com.reusehub.order.dto;

public record OrderEventResponse(
    String action,
    OrderResponse order
) {}
