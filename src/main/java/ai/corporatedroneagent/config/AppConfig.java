package ai.corporatedroneagent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({StorageProperties.class, BrowserLaunchProperties.class})
public class AppConfig {
}
