package com.reusehub.chat.dto;

import java.util.List;

public record ChatHistoryResponse(
    String threadKey,
    List<ChatMessageDto> messages
) {}
