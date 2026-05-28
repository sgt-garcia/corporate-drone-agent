package ai.corporatedroneagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CorporateDroneAgentApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(CorporateDroneAgentApplication.class);
        application.setHeadless(false);
        application.run(args);
    }
}
