package com.reusehub.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
  List<ChatMessage> findByThreadOrderByCreatedAtAsc(ChatThread thread);

  Optional<ChatMessage> findTopByThreadOrderByCreatedAtDesc(ChatThread thread);

  void deleteByThread(ChatThread thread);
}
