package com.foodplanner.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${firebase.service-account-json:}")
    private String serviceAccountJson;

    @Value("${firebase.project-id:food-planner-app}")
    private String projectId;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials = loadCredentials();

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build();

        return FirebaseApp.initializeApp(options);
    }

    private GoogleCredentials loadCredentials() throws IOException {
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            log.info("Initializing Firebase with service account JSON from environment");
            InputStream stream = new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
            return GoogleCredentials.fromStream(stream);
        }
        if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
            log.info("Initializing Firebase with service account file: {}", serviceAccountPath);
            return GoogleCredentials.fromStream(new FileInputStream(serviceAccountPath));
        }
        log.warn("No Firebase credentials configured – using application default credentials. "
                + "Set FIREBASE_SERVICE_ACCOUNT_JSON or FIREBASE_SERVICE_ACCOUNT_PATH for production.");
        return GoogleCredentials.getApplicationDefault();
    }
}
