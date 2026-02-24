package com.foodplanner.config;

import com.foodplanner.model.Store;
import com.foodplanner.model.User;
import com.foodplanner.service.FirebaseService;
import com.foodplanner.service.StoreOfferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Handles successful OAuth2 logins by starting a background virtual thread that
 * downloads the latest offers for the user's favourite stores.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final FirebaseService firebaseService;
    private final StoreOfferService storeOfferService;

    public OAuth2LoginSuccessHandler(FirebaseService firebaseService,
                                     StoreOfferService storeOfferService) {
        super("/dashboard");
        this.firebaseService = firebaseService;
        this.storeOfferService = storeOfferService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, jakarta.servlet.ServletException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        String userId = principal.getAttribute("sub");

        if (userId != null) {
            Thread.ofVirtual().start(() -> fetchOffersForUserStores(userId));
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }

    private void fetchOffersForUserStores(String userId) {
        try {
            User user = firebaseService.getUser(userId);
            if (user == null) return;

            List<Store> stores = user.getSelectedStores();
            if (stores == null || stores.isEmpty()) return;

            log.info("Fetching offers for {} favourite store(s) for user {}", stores.size(), userId);
            for (Store store : stores) {
                Thread.ofVirtual().start(() -> {
                    try {
                        storeOfferService.refreshOffersForSpecificStore(store);
                    } catch (Exception e) {
                        log.warn("Failed to refresh offers for store '{}': {}", store.getName(), e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Error fetching offers on login for user {}: {}", userId, e.getMessage());
        }
    }
}
