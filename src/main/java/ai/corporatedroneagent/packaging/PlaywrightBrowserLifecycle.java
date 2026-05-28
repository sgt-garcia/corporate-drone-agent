package ai.corporatedroneagent.packaging;

import ai.corporatedroneagent.config.BrowserLaunchProperties;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlaywrightBrowserLifecycle implements ApplicationListener<WebServerInitializedEvent>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowserLifecycle.class);

    private final BrowserLaunchProperties properties;
    private final ConfigurableApplicationContext applicationContext;
    private final Environment environment;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    private Playwright playwright;
    private Browser browser;
    private BrowserContext browserContext;

    public PlaywrightBrowserLifecycle(
            BrowserLaunchProperties properties,
            ConfigurableApplicationContext applicationContext,
            Environment environment
    ) {
        this.properties = properties;
        this.applicationContext = applicationContext;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (!properties.isEnabled() || !started.compareAndSet(false, true)) {
            return;
        }

        String homeUrl = resolveHomeUrl(event);
        Thread browserThread = new Thread(() -> launchAndWatch(homeUrl), "cda-playwright-browser");
        browserThread.setDaemon(false);
        browserThread.start();
    }

    private void launchAndWatch(String homeUrl) {
        boolean launched = false;
        try {
            playwright = Playwright.create();
            browser = launchBrowser();
            browserContext = browser.newContext();
            Page page = browserContext.newPage();
            page.navigate(homeUrl);
            launched = true;
            log.info("Opened Corporate Drone Agent in browser at {}", homeUrl);
            waitForBrowserClose();
        } catch (Exception ex) {
            log.error("Could not launch browser for Corporate Drone Agent. The app is still available at {}.", homeUrl, ex);
        } finally {
            closePlaywright();
            if (launched && properties.isTerminateOnClose() && !stopping.get()) {
                log.info("Browser closed; terminating Corporate Drone Agent.");
                SpringApplication.exit(applicationContext, () -> 0);
            }
        }
    }

    private Browser launchBrowser() {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(properties.isHeadless())
                .setTimeout(toMillis(properties.getLaunchTimeout()));

        if (StringUtils.hasText(properties.getChannel())) {
            options.setChannel(properties.getChannel().trim());
        }

        return playwright.chromium().launch(options);
    }

    private void waitForBrowserClose() throws InterruptedException {
        while (!stopping.get() && browser != null && browser.isConnected()) {
            if (hasNoOpenPages()) {
                return;
            }
            Thread.sleep(500);
        }
    }

    private boolean hasNoOpenPages() {
        try {
            if (browserContext == null) {
                return true;
            }
            List<Page> pages = browserContext.pages();
            return pages.isEmpty() || pages.stream().allMatch(Page::isClosed);
        } catch (PlaywrightException ex) {
            return true;
        }
    }

    private String resolveHomeUrl(WebServerInitializedEvent event) {
        if (StringUtils.hasText(properties.getUrl())) {
            return properties.getUrl().trim();
        }

        int port = event.getWebServer().getPort();
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        if (!StringUtils.hasText(contextPath) || "/".equals(contextPath)) {
            contextPath = "";
        } else if (!contextPath.startsWith("/")) {
            contextPath = "/" + contextPath;
        }

        return "http://localhost:" + port + contextPath + "/";
    }

    private double toMillis(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return 30_000;
        }
        return duration.toMillis();
    }

    @Override
    public void close() {
        stopping.set(true);
        closePlaywright();
    }

    private void closePlaywright() {
        closeQuietly(browserContext);
        closeQuietly(browser);
        closeQuietly(playwright);
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ex) {
            log.debug("Ignoring browser shutdown error.", ex);
        }
    }
}
