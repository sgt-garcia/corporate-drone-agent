package ai.corporatedroneagent;

import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CorporateDroneAgentApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(CorporateDroneAgentApplication.class);
        application.setHeadless(shouldRunHeadless(args));
        application.run(args);
    }

    static boolean shouldRunHeadless(String[] args) {
        return shouldRunHeadless(
                args,
                System.getProperty("cda.browser.enabled"),
                System.getenv("CDA_BROWSER_ENABLED")
        );
    }

    static boolean shouldRunHeadless(String[] args, String browserEnabledProperty, String browserEnabledEnv) {
        return hasDisabledBrowserArgument(args)
                || isExplicitFalse(browserEnabledProperty)
                || isExplicitFalse(browserEnabledEnv);
    }

    private static boolean hasDisabledBrowserArgument(String[] args) {
        if (args == null) {
            return false;
        }

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null) {
                continue;
            }
            if (arg.startsWith("--cda.browser.enabled=")) {
                return isExplicitFalse(arg.substring("--cda.browser.enabled=".length()));
            }
            if ("--cda.browser.enabled".equals(arg) && i + 1 < args.length) {
                return isExplicitFalse(args[i + 1]);
            }
        }
        return false;
    }

    private static boolean isExplicitFalse(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "false".equals(normalized)
                || "0".equals(normalized)
                || "off".equals(normalized)
                || "no".equals(normalized);
    }
}
