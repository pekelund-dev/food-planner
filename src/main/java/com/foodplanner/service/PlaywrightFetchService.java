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

    /**
     * How long to wait after each scroll before measuring whether new content appeared.
     * Long enough for a typical XHR/fetch triggered by the scroll to complete.
     */
    private static final int SCROLL_WAIT_MS = 2000;

    /**
     * Maximum number of scroll iterations, to prevent an infinite loop on pages
     * that always report new height (e.g. auto-playing carousels).
     */
    private static final int MAX_SCROLL_ITERATIONS = 20;

    /**
     * Navigates to {@code url} with a headless Chromium browser, waits for initial load,
     * then repeatedly scrolls to the bottom until the page height stops growing (all
     * lazy-loaded offer tiles are rendered), and returns the visible text body.
     * Returns an empty string on any failure so callers can fall back gracefully.
     */
    public String fetchPageContent(String url) {
        log.info("Fetching rendered content from {}", url);
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            page.navigate(url, new Page.NavigateOptions().setTimeout(30_000));
            // Wait until network is idle so the initial JS render completes
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE,
                        new Page.WaitForLoadStateOptions().setTimeout(15_000));
            } catch (Exception timeout) {
                log.debug("Network-idle timeout for {}; continuing with partial content", url);
            }

            // Scroll-until-stable loop: keep scrolling until page height no longer grows.
            // This handles infinite-scroll / progressively-loaded offer pages.
            long previousHeight = 0;
            for (int i = 0; i < MAX_SCROLL_ITERATIONS; i++) {
                long currentHeight = (Long) page.evaluate("document.body.scrollHeight");
                if (currentHeight == previousHeight) {
                    log.info("Page height stable at {} px after {} scroll(s) for {}", currentHeight, i, url);
                    break;
                }
                log.debug("Scroll {}: height {} → {} px for {}", i + 1, previousHeight, currentHeight, url);
                previousHeight = currentHeight;
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                page.waitForTimeout(SCROLL_WAIT_MS);
            }

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
