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

- **Backend**: Spring Boot 4.0.2, Spring Security OAuth2
- **AI**: Spring AI 2.0.0-M2 with Google Gemini (GenAI) integration
- **Frontend**: Thymeleaf, HTMX, Tailwind CSS
- **Database**: Firebase Firestore
- **Auth**: Google OAuth2
- **Java**: 25

## Setup

### Prerequisites

- Java 25+
- Maven 3.9+
- A Google Cloud project (create one free at https://console.cloud.google.com)
- The `gcloud` CLI installed (https://cloud.google.com/sdk/docs/install)

---

### 1. Gemini API Key

The Gemini API key is obtained from **Google AI Studio** (not GCP Console).

**Via browser:**
1. Go to https://aistudio.google.com/app/apikey
2. Click **Create API key**
3. Select your GCP project and copy the key

**Via gcloud CLI** (requires billing-enabled project):
```bash
# The Gemini API key lives in AI Studio – use the browser above.
# However you can verify your project is set correctly:
gcloud config set project YOUR_PROJECT_ID
```

Set the environment variable:
```bash
export GEMINI_API_KEY=your-key-here
```

---

### 2. Google OAuth2 Credentials (for Sign-in with Google)

**Via GCP Console:**
1. Go to https://console.cloud.google.com/apis/credentials
2. Enable the **OAuth consent screen**: APIs & Services → OAuth consent screen  
   - Choose **External**, fill in App name, support email, developer email
3. Back on **Credentials** → **Create Credentials** → **OAuth 2.0 Client ID**
4. Application type: **Web application**
5. Under **Authorised redirect URIs** add: `http://localhost:8080/login/oauth2/code/google`
6. Copy the **Client ID** and **Client Secret**

**Via gcloud CLI:**
```bash
# List existing OAuth clients (note: creation is not supported via CLI – use Console)
gcloud auth application-default login
gcloud config set project YOUR_PROJECT_ID

# Enable required APIs
gcloud services enable oauth2.googleapis.com
gcloud services enable people.googleapis.com
```

Set the environment variables:
```bash
export GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
export GOOGLE_CLIENT_SECRET=your-client-secret
```

---

### 3. Firebase / Firestore Service Account

**Via Firebase Console (browser):**
1. Go to https://console.firebase.google.com and open your project  
   (create one if needed: **Add project** → link to your GCP project)
2. Enable **Firestore Database**: Build → Firestore Database → Create database  
   Choose **Native mode** and a region (e.g. `europe-west1`)
3. Download a Service Account key:  
   Project Settings (⚙️) → **Service accounts** tab  
   → **Generate new private key** → save the JSON file

**Via gcloud / Firebase CLI:**
```bash
# Install Firebase CLI
npm install -g firebase-tools

# Login and pick your project
firebase login
firebase use YOUR_PROJECT_ID

# Enable Firestore via gcloud
gcloud services enable firestore.googleapis.com

# Create a service account (replace SA_NAME with a name you choose)
gcloud iam service-accounts create food-planner-sa \
    --display-name="Food Planner Service Account"

# Grant Firestore access
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
    --member="serviceAccount:food-planner-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/datastore.user"

# Download the key as JSON
gcloud iam service-accounts keys create firebase-sa.json \
    --iam-account=food-planner-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com
```

Set the environment variables (choose one method):
```bash
# Option A – inline JSON (good for containers/CI)
export FIREBASE_PROJECT_ID=YOUR_PROJECT_ID
export FIREBASE_SERVICE_ACCOUNT_JSON=$(cat firebase-sa.json)

# Option B – file path (good for local dev)
export FIREBASE_PROJECT_ID=YOUR_PROJECT_ID
export FIREBASE_SERVICE_ACCOUNT_PATH=/path/to/firebase-sa.json
```

> ⚠️ Never commit `firebase-sa.json` to version control. It is already listed in `.gitignore`.

---

### Running locally

```bash
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...
export FIREBASE_PROJECT_ID=...
export FIREBASE_SERVICE_ACCOUNT_JSON=$(cat firebase-sa.json)
export GEMINI_API_KEY=...

JAVA_HOME=/path/to/java25 mvn spring-boot:run
```

Open http://localhost:8080 in your browser.

### Building

```bash
JAVA_HOME=/path/to/java25 mvn package
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

