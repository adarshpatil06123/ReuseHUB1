package com.reusehub;

import com.reusehub.config.AppProperties;
import com.reusehub.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, JwtProperties.class})
public class ReuseHubApplication {
  public static void main(String[] args) {
    SpringApplication.run(ReuseHubApplication.class, args);
  }
}
