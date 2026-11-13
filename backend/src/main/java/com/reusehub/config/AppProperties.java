package com.reusehub.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
  private List<String> allowedEmailDomains;
  private boolean autoVerifyEmail = false;

  public List<String> getAllowedEmailDomains() {
    return allowedEmailDomains;
  }

  public void setAllowedEmailDomains(List<String> allowedEmailDomains) {
    this.allowedEmailDomains = allowedEmailDomains;
  }

  public boolean isAutoVerifyEmail() {
    return autoVerifyEmail;
  }

  public void setAutoVerifyEmail(boolean autoVerifyEmail) {
    this.autoVerifyEmail = autoVerifyEmail;
  }
}
