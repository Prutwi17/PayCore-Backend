# PayCore — Google Cloud & Gmail OAuth 2.0 Setup Guide

This guide walks you step-by-step through configuring your Google Cloud project and authorizing your Gmail account (`omp27614@gmail.com`) to send real verification and password recovery emails for PayCore.

---

## 1. Google Cloud Console Setup

### Step 1.1: Create or Select a Google Cloud Project
1. Open the [Google Cloud Console](https://console.cloud.google.com/).
2. Log in with your Google account (`omp27614@gmail.com`).
3. Click the project dropdown at the top of the page and select **New Project**.
4. Set the project name (e.g. `PayCore-Production` or `PayCore-Email`) and click **Create**.

### Step 1.2: Enable the Gmail API
1. In the Google Cloud Console, navigate to **APIs & Services > Library** (or search for "Gmail API").
2. Click on **Gmail API**.
3. Click the **Enable** button.

### Step 1.3: Configure the OAuth Consent Screen
1. In the left sidebar, navigate to **APIs & Services > OAuth consent screen**.
2. Select **User Type**:
   - Choose **External** (standard for non-Google Workspace personal `@gmail.com` accounts).
   - Click **Create**.
3. Fill in the **App Information**:
   - **App name:** `PayCore`
   - **User support email:** `omp27614@gmail.com`
   - **Developer contact information:** `omp27614@gmail.com`
   - Click **Save and Continue**.
4. Configure **Scopes**:
   - Click **Add or Remove Scopes**.
   - Search for `gmail.send` or filter by Gmail API.
   - Select the checkbox for:  
     `https://www.googleapis.com/auth/gmail.send` (Send email on your behalf).
   - Click **Update** and then **Save and Continue**.
5. Add **Test Users** (Critical while publishing status is "Testing"):
   - Click **+ Add Users**.
   - Enter: `omp27614@gmail.com`
   - Click **Add** and then **Save and Continue**.
6. Review your summary and return to the dashboard.

---

## 2. Create OAuth 2.0 Client Credentials

1. In the left sidebar, navigate to **APIs & Services > Credentials**.
2. Click **+ Create Credentials** at the top and select **OAuth client ID**.
3. Select **Application type**: **Web application**.
4. Set the **Name**: `PayCore Backend Client`.
5. Under **Authorized redirect URIs**, click **+ Add URI** and enter the exact backend callback URL:
   ```text
   http://localhost:8080/api/v1/auth/google/callback
   ```
   *(Note: If deploying in production or staging, add your production callback URL here as well).*
6. Under **Authorized JavaScript origins** (optional), add:
   ```text
   http://localhost:8080
   http://localhost:5173
   ```
7. Click **Create**.
8. A modal appears displaying your:
   - **Client ID** (e.g. `1234567890-abcdefg.apps.googleusercontent.com`)
   - **Client Secret** (e.g. `GOCSPX-xxxxxxxxxxxxxxxxxxxx`)
9. Copy these values into a secure location. **Never commit them to version control.**

---

## 3. Environment Variables Reference

PayCore reads email credentials from system environment variables or standard `.env` configuration:

| Environment Variable | Description | Example / Recommended Value |
|---|---|---|
| `GOOGLE_CLIENT_ID` | OAuth 2.0 Web Client ID from Google Cloud Console | `1234567890-...apps.googleusercontent.com` |
| `GOOGLE_CLIENT_SECRET` | OAuth 2.0 Client Secret from Google Cloud Console | `GOCSPX-xxxxxxxxxxxxxxxxxxxx` |
| `GOOGLE_REDIRECT_URI` | Exact OAuth callback registered in Google Cloud Console | `http://localhost:8080/api/v1/auth/google/callback` |
| `GOOGLE_REFRESH_TOKEN` | Long-lived refresh token obtained from authorization flow | `1//04xxxxxxxxxxxxxxxxxxxxxxxxxxxx` |
| `GOOGLE_SENDER_EMAIL` | Authorized Gmail account sending emails | `omp27614@gmail.com` |
| `APP_FRONTEND_URL` | Base URL for verification and password reset links | `http://localhost:5173` |

---

## 4. Obtaining the Refresh Token (Self-Service Authorization Flow)

PayCore provides a built-in interactive authorization helper so you can generate your `GOOGLE_REFRESH_TOKEN` without external third-party tools:

### Step 4.1: Start the Backend with Client Credentials
Set the initial environment variables (omitting `GOOGLE_REFRESH_TOKEN` for now):
```powershell
$env:GOOGLE_CLIENT_ID="your_google_client_id.apps.googleusercontent.com"
$env:GOOGLE_CLIENT_SECRET="your_google_client_secret"
$env:GOOGLE_REDIRECT_URI="http://localhost:8080/api/v1/auth/google/callback"
$env:GOOGLE_SENDER_EMAIL="omp27614@gmail.com"
$env:APP_FRONTEND_URL="http://localhost:5173"

./mvnw.cmd spring-boot:run
```

### Step 4.2: Retrieve the Authorization URL
In your browser or terminal, query the OAuth URL endpoint:
```bash
curl http://localhost:8080/api/v1/auth/google/oauth-url
```
Response:
```json
{
  "status": "success",
  "data": {
    "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fv1%2Fauth%2Fgoogle%2Fcallback&response_type=code&scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fgmail.send&access_type=offline&prompt=consent"
  }
}
```

### Step 4.3: Authorize in Browser
1. Copy the `authorizationUrl` and open it in your browser.
2. Sign in with `omp27614@gmail.com`.
3. If Google displays an "App isn't verified" screen (normal for projects in Testing mode), click **Advanced > Go to PayCore (unsafe)**.
4. Check the box to grant permission: **Send email on your behalf**.
5. Click **Continue**.

### Step 4.4: Exchange Code for Refresh Token
Google redirects to `http://localhost:8080/api/v1/auth/google/callback?code=4/0A...`.
The backend's callback controller automatically intercepts the code, contacts Google's token endpoint, and displays the JSON output:

```json
{
  "status": "success",
  "message": "Google OAuth authorization successful. Save the refresh token in your environment.",
  "data": {
    "refreshToken": "1//04xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "instructions": "Set this value as the GOOGLE_REFRESH_TOKEN environment variable in PayCore Backend and restart the service."
  }
}
```

### Step 4.5: Persist `GOOGLE_REFRESH_TOKEN`
Copy the returned `refreshToken` and set it in your environment:
```powershell
$env:GOOGLE_REFRESH_TOKEN="1//04xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```
Or in your `.env` file:
```env
GOOGLE_REFRESH_TOKEN=1//04xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```
Restart the PayCore backend.

---

## 5. Verifying Operational Status

To confirm that email delivery is active and operational:

```bash
curl http://localhost:8080/api/v1/auth/email-status
```

Expected Response:
```json
{
  "status": "success",
  "data": {
    "configured": true,
    "provider": "GMAIL_REST_API",
    "senderEmail": "omp27614@gmail.com",
    "message": "Gmail API is configured and ready for live email dispatching."
  }
}
```

If unconfigured or misconfigured, the endpoint reports:
```json
{
  "status": "success",
  "data": {
    "configured": false,
    "provider": "GMAIL_REST_API",
    "senderEmail": "omp27614@gmail.com",
    "message": "Gmail API is not configured. Missing credentials: [refresh-token]. Real email delivery is disabled."
  }
}
```
In this state, any user attempt to send an email will return HTTP `503 Service Unavailable` with `EMAIL_DELIVERY_UNAVAILABLE`.
