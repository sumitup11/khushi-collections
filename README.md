# Khushi Collection (Spring Boot + React + Razorpay)

One Spring Boot app serves both the API (`/api/...`) and the React storefront (`src/main/resources/static/index.html`).

## Run locally (Java 17 + Maven needed)
    export JWT_SECRET="any-long-random-string-at-least-32-characters"
    export RAZORPAY_KEY_ID="rzp_test_xxxx"        # Razorpay Dashboard > Test Mode > API Keys
    export RAZORPAY_KEY_SECRET="xxxx"
    mvn spring-boot:run
Open http://localhost:8080 . Cash on Delivery works even without Razorpay keys.

## Deploy (Render, Docker)
1. Push this folder to a GitHub repo. 2. Render > New > Web Service > pick the repo (it detects the Dockerfile).
3. Add env vars: JWT_SECRET, RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET.
4. For data that survives restarts, use a MySQL database and set DB_URL (jdbc:mysql://HOST:3306/khushi), DB_USER, DB_PASS.
   Without these, the app uses a local H2 file that Render's free tier erases on redeploy.
5. Go live: swap Razorpay test keys for live keys after KYC. Never commit keys to Git.

Prices are always taken from the database on the server, and online payments are confirmed only after the Razorpay signature is verified.

## Install as an app
Open the deployed https link. Android/Chrome/Edge: tap **Install App** inside the app (or browser menu > Install app). iPhone/Safari: Share > Add to Home Screen. On a laptop, Chrome/Edge shows an install icon in the address bar. It then opens in its own window with its own icon.

## IntelliJ IDEA (local, auto-restart on changes)
1. File > Open > select this folder (the one with pom.xml); wait for Maven to import. Use JDK 17.
2. Open App.java > click the green run icon. In Run > Edit Configurations add Environment variables: JWT_SECRET=...;RAZORPAY_KEY_ID=...;RAZORPAY_KEY_SECRET=...
3. Open http://localhost:8080 . Chrome can install it as an app even on localhost.
4. For auto-restart: Settings > Build, Execution, Deployment > Compiler > tick "Build project automatically", then Settings > Advanced Settings > tick "Allow auto-make to start even if developer application is currently running". Spring Boot DevTools restarts the app after you save. Refresh the browser (F5), or install the "LiveReload" Chrome extension to refresh automatically.
