# PayCore — Known Technical Limitations & Operational Scope

## 1. Simulated Payment Provider Scope

PayCore integrates with an enterprise-modeled **Simulated Payment Provider** (`SimulatedPaymentProviderAdapter`). 
- **Non-Financial Execution:** Simulated payments and refunds do **not** initiate real financial debits or credits against banking rails, interchange networks (Visa, Mastercard, American Express), or automated clearing houses (ACH, SEPA, UPI).
- **Independent Provider Store:** The simulated provider maintains an independent in-memory ledger store to enable true two-sided settlement reconciliation. However, this is an internal architectural simulation, not a physical bank feed.

---

## 2. Regulatory & Compliance Scope

### 2.1 No PCI DSS Certification
- PayCore has **not** been audited or certified under the **Payment Card Industry Data Security Standard (PCI DSS)**.
- **Strict Invariant:** Do not enter real credit card Primary Account Numbers (PAN), CVV/CVC codes, PINs, or magnetic stripe data into PayCore.
- Production payment systems handling raw card data require level-1 PCI DSS certified card tokenization vaults (e.g. Stripe Elements, Adyen Drop-in, or secure hardware security modules).

### 2.2 Bank Settlement Reconciliation
- Stage 7 reconciliation compares internal PayCore records against simulated downstream provider statements.
- It does **not** perform end-to-end multi-currency nostro/vostro interbank ledger settlement, interchange fee reconciliation, chargeback interchange filings, or central bank clearing.

---

## 3. Email Delivery & Google OAuth Restrictions

1. **Gmail API Quotas:**
   - Personal `@gmail.com` accounts authorized via the Gmail REST API are subject to Google's daily sending limits (typically 500 emails per 24 hours for free accounts; up to 2,000 for Google Workspace).
   - The Gmail API is optimized for operational transactional messages, not high-volume bulk marketing blasts.
2. **OAuth Testing Mode & Token Expiration:**
   - While the OAuth consent screen status in Google Cloud Console is set to **Testing**, refresh tokens issued to test users expire automatically after **7 days**.
   - To make refresh tokens permanent, the project owner can submit the app for verification or switch the project status to **In Production** in the Google Cloud Console.
3. **Fail-Fast Error Handling:**
   - If Google OAuth credentials are not supplied, the application strictly reports HTTP 503 `EMAIL_DELIVERY_UNAVAILABLE`. Real email delivery requires manual Google Cloud setup as documented in `docs/GoogleOAuthSetup.md`.

---

## 4. Free-Tier Cloud Hosting Limitations

- **Stateful Infrastructure:** Enterprise Kafka brokers, Redis clusters, and MySQL databases require persistent block storage volumes. Free-tier cloud platforms (e.g., Render, Railway free tiers) frequently sleep inactive containers and wipe ephemeral file storage upon reboot.
- **Recommended Setup:** For production reliability, deploy PayCore on Docker Compose on a dedicated VPS (e.g., Hetzner, DigitalOcean Droplet, AWS EC2, GCP Compute Engine) or utilize managed database offerings (AWS RDS, PlanetScale, Upstash Redis, Confluent Cloud).

---

## 5. Scope Reserved for Future Stages

The following advanced enterprise capabilities are explicitly reserved for future iterations:
- **Card Tokenization Vault & 3DS Mobile SDKs:** Native iOS and Android payment sheet integrations.
- **Recurring Subscriptions & Billing Engine:** Automated meter calculations, recurring invoice generation, and dunning workflows.
- **Multi-Currency Real-Time FX Conversion:** Dynamic conversion rates integrated with central bank exchange rate feeds.
- **Multi-Gateway Routing Engine:** Dynamic least-cost routing and automated failover across multiple live providers (e.g., Stripe, Adyen, Braintree).
