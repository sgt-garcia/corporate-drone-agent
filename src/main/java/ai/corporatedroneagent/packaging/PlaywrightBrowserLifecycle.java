package ai.corporatedroneagent.packaging;

import ai.corporatedroneagent.config.BrowserLaunchProperties;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.ViewportSize;
import com.google.gson.JsonObject;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
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
            applyWindowBounds(page);
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
        Rectangle bounds = usableScreenBounds();
        int width = Math.max(640, (int) Math.round(bounds.width * scale));
        int height = Math.max(480, (int) Math.round(bounds.height * scale));
        int left = bounds.x + Math.max(0, (bounds.width - width) / 2);
        int top = bounds.y + Math.max(0, (bounds.height - height) / 2);

        List<String> args = new ArrayList<>();
        args.add("--window-size=" + width + "," + height);
        args.add("--window-position=" + left + "," + top);
        return args;
    }

    private void applyWindowBounds(Page page) {
        if (properties.isHeadless() || GraphicsEnvironment.isHeadless()) {
            return;
        }

        Rectangle bounds = desiredWindowBounds();
        log.info(
                "Applying browser window bounds {}x{} at {},{} using scale {}.",
                bounds.width,
                bounds.height,
                bounds.x,
                bounds.y,
                properties.getWindowScale()
        );
        try {
            CDPSession cdp = browserContext.newCDPSession(page);
            JsonObject window = cdp.send("Browser.getWindowForTarget");
            JsonObject setBounds = new JsonObject();
            setBounds.addProperty("windowId", window.get("windowId").getAsInt());

            JsonObject cdpBounds = new JsonObject();
            cdpBounds.addProperty("windowState", "normal");
            cdpBounds.addProperty("left", bounds.x);
            cdpBounds.addProperty("top", bounds.y);
            cdpBounds.addProperty("width", bounds.width);
            cdpBounds.addProperty("height", bounds.height);
            setBounds.add("bounds", cdpBounds);

            cdp.send("Browser.setWindowBounds", setBounds);
            log.info("Set browser window bounds to {}x{} at {},{}.", bounds.width, bounds.height, bounds.x, bounds.y);
        } catch (Exception ex) {
            log.warn(
                    "Could not set browser window bounds to {}x{} at {},{}.",
                    bounds.width,
                    bounds.height,
                    bounds.x,
                    bounds.y,
                    ex
            );
        }
    }

    private Rectangle desiredWindowBounds() {
        double scale = Math.max(0.1, Math.min(1.0, properties.getWindowScale()));
        Rectangle screenBounds = usableScreenBounds();
        int width = Math.max(640, (int) Math.round(screenBounds.width * scale));
        int height = Math.max(480, (int) Math.round(screenBounds.height * scale));
        int left = screenBounds.x + Math.max(0, (screenBounds.width - width) / 2);
        int top = screenBounds.y + Math.max(0, (screenBounds.height - height) / 2);
        return new Rectangle(left, top, width, height);
    }

    private Rectangle usableScreenBounds() {
        if (GraphicsEnvironment.isHeadless()) {
            return new Rectangle(0, 0, 1280, 720);
        }
        GraphicsConfiguration configuration = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
        Rectangle bounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom
        );
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
