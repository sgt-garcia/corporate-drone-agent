package ai.corporatedroneagent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cda.browser")
public class BrowserLaunchProperties {

    private boolean enabled = true;
    private boolean headless = false;
    private boolean terminateOnClose = true;
    private String channel = "";
    private String url = "";
    private double windowScale = 0.9;
    private Duration launchTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public boolean isTerminateOnClose() {
        return terminateOnClose;
    }

    public void setTerminateOnClose(boolean terminateOnClose) {
        this.terminateOnClose = terminateOnClose;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public double getWindowScale() {
        return windowScale;
    }

    public void setWindowScale(double windowScale) {
        this.windowScale = windowScale;
    }

    public Duration getLaunchTimeout() {
        return launchTimeout;
    }

    public void setLaunchTimeout(Duration launchTimeout) {
        this.launchTimeout = launchTimeout;
    }
}
