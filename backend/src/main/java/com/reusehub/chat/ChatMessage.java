package com.reusehub.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "thread_id")
  private ChatThread thread;

  @Column(name = "sender_id", nullable = false)
  private Long senderId;

  @Column(nullable = false, length = 2000)
  private String content;

  private Instant createdAt;

  @PrePersist
  public void prePersist() {
    createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public ChatThread getThread() {
    return thread;
  }

  public void setThread(ChatThread thread) {
    this.thread = thread;
  }

  public Long getSenderId() {
    return senderId;
  }

  public void setSenderId(Long senderId) {
    this.senderId = senderId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
