package com.reusehub.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NoopEmailService implements EmailService {
  private static final Logger logger = LoggerFactory.getLogger(NoopEmailService.class);

  @Override
  public void sendEmailVerification(String email, String verificationLink) {
    logger.info("Verification for {} -> {}", email, verificationLink);
  }
}
