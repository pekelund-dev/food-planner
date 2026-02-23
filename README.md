# 🥗 Food Planner

An AI-powered food planning application built with Spring Boot, Thymeleaf, HTMX, and Firebase.

## Features

- 🤖 **AI Menu Generation** – Gemini AI creates personalized weekly menus based on your preferences
- 📖 **Recipe Creation** – Auto-generate detailed recipes with ingredients and step-by-step instructions
- 🛒 **Smart Shopping Lists** – Auto-generate consolidated shopping lists from weekly menus
- 🏪 **Store Offers** – Fetch current grocery store deals and use them in menu planning
- ⭐ **Meal Ratings** – Rate meals to help AI improve future suggestions
- ⚙️ **Configurable** – Set dietary preferences, allergies, cuisine preferences, and custom AI instructions
- 📱 **Responsive** – Works on both mobile and desktop
- 🔐 **Google Sign-In** – Secure authentication via Google OAuth2

## Tech Stack

- **Backend**: Spring Boot 3.4, Spring Security OAuth2
- **Frontend**: Thymeleaf, HTMX, Tailwind CSS
- **Database**: Firebase Firestore
- **AI**: Google Gemini API
- **Auth**: Google OAuth2

## Setup

### Prerequisites

- Java 17+
- Maven 3.9+
- A Google Cloud project with:
  - Firebase Firestore enabled
  - Google OAuth2 credentials (OAuth 2.0 Client ID)
  - Gemini API key

### Configuration

Copy the environment variables and configure them:

```bash
# Google OAuth2 (from Google Cloud Console → APIs & Services → Credentials)
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret

# Firebase (from Firebase Console → Project Settings → Service Accounts)
export FIREBASE_PROJECT_ID=your-firebase-project-id
export FIREBASE_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}'

# Gemini AI (from Google AI Studio → Get API Key)
export GEMINI_API_KEY=your-gemini-api-key

# Optional: Store integrations
export ICA_ENABLED=true
export WILLYS_ENABLED=true
export COOP_ENABLED=true
```

### Running locally

```bash
mvn spring-boot:run
```

Open http://localhost:8080 in your browser.

### Building

```bash
mvn package
java -jar target/food-planner-1.0.0-SNAPSHOT.jar
```

## Firebase Firestore Structure

```
users/{userId}
  ├── menuConfig          # User's menu preferences
  ├── selectedStoreIds    # List of store IDs
  └── meals/{mealId}      # User's meals
  └── recipes/{recipeId}  # User's recipes
  └── menus/{weekId}      # Weekly menus
  └── ratings/{ratingId}  # Meal ratings
  └── shopping-lists/{id} # Shopping lists

store-offers/{offerId}    # Current store offers (global)
```

## Store Integrations

Currently supported stores (Sweden):
- **ICA** – Swedish supermarket chain
- **Willys** – Budget-friendly supermarket
- **Coop** – Member-owned supermarket
- **Hemköp** – Premium supermarket
- **Lidl** – European discount chain
- **Mathem** – Online grocery delivery

Offers are automatically refreshed every Monday at 06:00. You can also manually refresh from the Offers page.

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

