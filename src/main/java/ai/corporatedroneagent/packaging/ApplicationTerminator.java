package ai.corporatedroneagent.packaging;

import org.springframework.context.ConfigurableApplicationContext;

public interface ApplicationTerminator {

    void terminate(ConfigurableApplicationContext applicationContext);
}
