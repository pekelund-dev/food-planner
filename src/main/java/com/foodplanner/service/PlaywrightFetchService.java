package com.foodplanner.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fetches the rendered text content of a URL using a Playwright headless Chromium browser.
 * This correctly handles JavaScript-heavy SPAs (like Swedish grocery store offer pages)
 * that return only an empty shell on a plain HTTP GET.
 */
@Service
public class PlaywrightFetchService {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightFetchService.class);

    /**
     * Maximum characters of page text to return, to stay within AI token limits.
     */
    private static final int MAX_CONTENT_LENGTH = 80_000;

    /** Initial pause after first scroll-to-bottom, allowing first batch of lazy tiles to load. */
    private static final int LAZY_LOAD_INITIAL_WAIT_MS = 2000;

    /** Shorter pause after second scroll, letting any remaining lazy tiles settle. */
    private static final int LAZY_LOAD_SUBSEQUENT_WAIT_MS = 1000;

    /**
     * Navigates to {@code url} with a headless Chromium browser, waits for the page to
     * finish loading, scrolls to the bottom to trigger lazy-loaded content, and returns
     * the visible text body (via {@code innerText("body")}).
     * Returns an empty string if the fetch fails for any reason (browser not installed,
     * network error, timeout, etc.) so callers can fall back gracefully.
     */
    public String fetchPageContent(String url) {
        log.info("Fetching rendered content from {}", url);
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            page.navigate(url, new Page.NavigateOptions().setTimeout(30_000));
            // Wait until network is idle so JS-rendered content is fully populated
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE,
                        new Page.WaitForLoadStateOptions().setTimeout(15_000));
            } catch (Exception timeout) {
                // Proceed with whatever has loaded so far
                log.debug("Network-idle timeout for {}; continuing with partial content", url);
            }
            // Scroll to bottom to trigger any lazy-loaded offer tiles, then wait briefly
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
            page.waitForTimeout(LAZY_LOAD_INITIAL_WAIT_MS);
            // Scroll to bottom a second time in case more content was loaded after first scroll
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
            page.waitForTimeout(LAZY_LOAD_SUBSEQUENT_WAIT_MS);

            String content = page.innerText("body");
            int rawLength = content != null ? content.length() : 0;
            log.info("Playwright fetched {} characters from {}", rawLength, url);
            if (content != null && content.length() > MAX_CONTENT_LENGTH) {
                log.info("Truncating content from {} to {} characters", rawLength, MAX_CONTENT_LENGTH);
                content = content.substring(0, MAX_CONTENT_LENGTH);
            }
            log.info("Playwright page content for {}:\n{}", url, content);
            return content != null ? content : "";
        } catch (Exception e) {
            log.warn("Playwright could not fetch {}: {}", url, e.getMessage());
            return "";
        }
    }
}
