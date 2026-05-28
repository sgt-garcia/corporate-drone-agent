package ai.corporatedroneagent.packaging;

import ai.corporatedroneagent.config.BrowserLaunchProperties;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.ViewportSize;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            playwright = Playwright.create(new Playwright.CreateOptions()
                    .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            browser = launchBrowser();
            browserContext = newBrowserContext();
            AtomicBoolean closed = new AtomicBoolean(false);
            browser.onDisconnected(closedBrowser -> closed.set(true));
            browserContext.onClose(closedContext -> closed.set(true));
            Page page = browserContext.newPage();
            page.onClose(closedPage -> closed.set(true));
            page.navigate(homeUrl);
            launched = true;
            log.info("Opened Corporate Drone Agent in browser at {}", homeUrl);
            waitForBrowserClose(page, closed);
        } catch (Exception ex) {
            log.error("Could not launch browser for Corporate Drone Agent. The app is still available at {}.", homeUrl, ex);
        } finally {
            closePlaywright();
            if (launched && properties.isTerminateOnClose() && !stopping.get()) {
                log.info("Browser closed; terminating Corporate Drone Agent.");
                int exitCode = SpringApplication.exit(applicationContext, () -> 0);
                System.exit(exitCode);
            }
        }
    }

    private Browser launchBrowser() {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(properties.isHeadless())
                .setTimeout(toMillis(properties.getLaunchTimeout()));

        List<String> args = windowArgs();
        if (!args.isEmpty()) {
            options.setArgs(args);
        }

        if (StringUtils.hasText(properties.getChannel())) {
            options.setChannel(properties.getChannel().trim());
        }

        return playwright.chromium().launch(options);
    }

    private BrowserContext newBrowserContext() {
        return browser.newContext(new Browser.NewContextOptions().setViewportSize((ViewportSize) null));
    }

    private List<String> windowArgs() {
        if (properties.isHeadless() || GraphicsEnvironment.isHeadless()) {
            return List.of();
        }

        double scale = Math.max(0.1, Math.min(1.0, properties.getWindowScale()));
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.max(640, (int) Math.round(screenSize.width * scale));
        int height = Math.max(480, (int) Math.round(screenSize.height * scale));
        int left = Math.max(0, (screenSize.width - width) / 2);
        int top = Math.max(0, (screenSize.height - height) / 2);

        List<String> args = new ArrayList<>();
        args.add("--window-size=" + width + "," + height);
        args.add("--window-position=" + left + "," + top);
        return args;
    }

    private void waitForBrowserClose(Page page, AtomicBoolean closed) {
        while (!stopping.get() && !closed.get() && isBrowserOpen(page)) {
            try {
                page.waitForTimeout(500);
            } catch (PlaywrightException ex) {
                return;
            }
        }
    }

    private boolean isBrowserOpen(Page page) {
        try {
            return browser != null
                    && browser.isConnected()
                    && browserContext != null
                    && !browserContext.isClosed()
                    && page != null
                    && !page.isClosed();
        } catch (PlaywrightException ex) {
            return false;
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
