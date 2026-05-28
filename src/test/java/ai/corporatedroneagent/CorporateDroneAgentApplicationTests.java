package ai.corporatedroneagent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CorporateDroneAgentApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void browserModeRunsWithDesktopAwtEnabledByDefault() {
        assertThat(CorporateDroneAgentApplication.shouldRunHeadless(new String[0], null, null)).isFalse();
    }

    @Test
    void explicitBrowserDisableKeepsServerModeHeadless() {
        assertThat(CorporateDroneAgentApplication.shouldRunHeadless(
                new String[]{"--cda.browser.enabled=false"},
                null,
                null
        )).isTrue();
        assertThat(CorporateDroneAgentApplication.shouldRunHeadless(
                new String[]{"--cda.browser.enabled", "false"},
                null,
                null
        )).isTrue();
        assertThat(CorporateDroneAgentApplication.shouldRunHeadless(new String[0], "false", null)).isTrue();
        assertThat(CorporateDroneAgentApplication.shouldRunHeadless(new String[0], null, "0")).isTrue();
    }
}
