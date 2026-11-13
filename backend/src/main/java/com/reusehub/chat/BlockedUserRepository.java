package com.reusehub.chat;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {
  boolean existsByBlockerIdAndBlockedUserId(Long blockerId, Long blockedUserId);
}
