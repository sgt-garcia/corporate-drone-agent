package ai.corporatedroneagent.packaging;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class JvmApplicationTerminator implements ApplicationTerminator {

    @Override
    public void terminate(ConfigurableApplicationContext applicationContext) {
        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(exitCode);
    }
}
