package com.reusehub.chat;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatThreadRepository extends JpaRepository<ChatThread, Long> {
  Optional<ChatThread> findByThreadKey(String threadKey);

  List<ChatThread> findByParticipantLowIdOrParticipantHighId(Long lowId, Long highId);
}
