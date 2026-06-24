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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final ApplicationTerminator applicationTerminator;
    private final Environment environment;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    private Playwright playwright;
    private Browser browser;
    private BrowserContext browserContext;

    public PlaywrightBrowserLifecycle(
            BrowserLaunchProperties properties,
            ConfigurableApplicationContext applicationContext,
            ApplicationTerminator applicationTerminator,
            Environment environment
    ) {
        this.properties = properties;
        this.applicationContext = applicationContext;
        this.applicationTerminator = applicationTerminator;
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
            browserContext = newBrowserContext();
            AtomicBoolean browserClosed = new AtomicBoolean(false);
            browser.onDisconnected(closedBrowser -> browserClosed.set(true));
            browserContext.onClose(closedContext -> browserClosed.set(true));
            Page page = browserContext.newPage();
            applyWindowBounds(page);
            page.navigate(homeUrl);
            launched = true;
            log.info("Opened Corporate Drone's Agent in browser at {}", homeUrl);
            waitForBrowserClose(browserClosed);
        } catch (Exception ex) {
            log.error("Could not launch browser for Corporate Drone's Agent. The app is still available at {}.", homeUrl, ex);
        } finally {
            closePlaywright();
            if (launched && properties.isTerminateOnClose() && !stopping.get()) {
                log.info("Browser closed; terminating Corporate Drone's Agent.");
                applicationTerminator.terminate(applicationContext);
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
        if (properties.isHeadless()) {
            return List.of();
        }
        if (GraphicsEnvironment.isHeadless()) {
            log.warn("Skipping browser launch window args because Java AWT is running in headless mode.");
            return List.of();
        }

        Rectangle bounds = desiredWindowBounds();
        return List.of(
                "--window-size=" + bounds.width + "," + bounds.height,
                "--window-position=" + bounds.x + "," + bounds.y
        );
    }

    private void applyWindowBounds(Page page) {
        if (properties.isHeadless()) {
            log.info("Skipping browser window bounds because cda.browser.headless is enabled.");
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            log.warn("Skipping browser window bounds because Java AWT is running in headless mode.");
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

    private void waitForBrowserClose(AtomicBoolean browserClosed) {
        while (!stopping.get() && !browserClosed.get() && isBrowserOpen()) {
            try {
                Page page = firstOpenPage();
                if (page == null) {
                    return;
                }
                page.waitForTimeout(500);
            } catch (PlaywrightException ex) {
                if (!hasOpenPages()) {
                    return;
                }
            }
        }
    }

    private boolean isBrowserOpen() {
        try {
            return browser != null
                    && browser.isConnected()
                    && browserContext != null
                    && !browserContext.isClosed()
                    && hasOpenPages();
        } catch (PlaywrightException ex) {
            return false;
        }
    }

    private boolean hasOpenPages() {
        return firstOpenPage() != null;
    }

    private Page firstOpenPage() {
        try {
            if (browserContext == null || browserContext.isClosed()) {
                return null;
            }
            for (Page page : browserContext.pages()) {
                if (!page.isClosed()) {
                    return page;
                }
            }
        } catch (PlaywrightException ex) {
            return null;
        }
        return null;
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
