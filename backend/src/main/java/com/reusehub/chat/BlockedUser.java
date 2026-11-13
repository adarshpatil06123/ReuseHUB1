package com.reusehub.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "blocked_users", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"blocker_id", "blocked_user_id"})
})
public class BlockedUser {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "blocker_id", nullable = false)
  private Long blockerId;

  @Column(name = "blocked_user_id", nullable = false)
  private Long blockedUserId;

  public Long getId() {
    return id;
  }

  public Long getBlockerId() {
    return blockerId;
  }

  public void setBlockerId(Long blockerId) {
    this.blockerId = blockerId;
  }

  public Long getBlockedUserId() {
    return blockedUserId;
  }

  public void setBlockedUserId(Long blockedUserId) {
    this.blockedUserId = blockedUserId;
  }
}
