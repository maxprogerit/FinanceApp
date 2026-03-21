# Smart Finance Dashboard — Project Memory

## Project Location
`c:\Users\Maxim\VS Code projects\FinanceApp`

## Status
Build compiles successfully (`mvn compile`). Server on port **8081** (not 8080).

## Critical Bug Fixes Applied
- **OAuth2 user lookup**: `SecurityUtils.getCurrentUser()` now falls back to `findByEmail` when `findByUsername` fails. For Google OAuth2 users `auth.getName()` returns email, not username — the old code threw "User not found" for every API call.
- **`@JsonIgnore` on entity `user` fields**: Added to Budget, Transaction, Alert, Investment, FinancialGoal, Debt, CategorizationRule, UserSubscription. Prevents lazy Hibernate proxy serialization issues and stops User entity (with password hash) from leaking into API responses.
- **`@JsonIgnore` on User sensitive fields**: `password`, `emailVerificationToken`, `passwordResetToken`, `passwordResetExpiry` are now hidden from JSON serialization.
- **`GET /api/payments/status` bug**: Used to return only `{active, username}` — `plan` and `status` fields were missing, making `settings.js` always show FREE. Fixed — now returns `plan`, `status`, `trialEnd`, `currentPeriodEnd` too.

## Subscription / Feature Gating System (v6)
- **Floating "Upgrade Plan" button**: injected by `app.js` via `injectUpgradeUI()` for FREE users only; fixed bottom-right, opens comparison modal
- **Plan comparison modal**: injected by `injectUpgradeUI()` into DOM on every authenticated page; 2-column Free vs Pro comparison; "Upgrade to Pro →" button calls `POST /api/payments/checkout` with loading state
- **Plan state**: `fetchPlanState()` in `app.js` fetches once per session (sessionStorage cache `_planState`); `isProUser()` checks `plan === 'PRO' && status in (active, trialing)`; cache cleared on `?subscription=success` redirect
- **`featureGate(el, message)`** in `app.js`: overlays any DOM element with a blurred lock overlay + "Upgrade to Pro" button; no-ops for PRO users
- **Feature gates in Settings**: `exportGateTarget` (Export & Reports card, includes forecast) and `csvImportGateTarget` (CSV import card) gated via `applySettingsFeatureGates()` in `settings.js`
- **Settings subscription card**: `#upgradeBtn` + `#manageBtn` (hidden by default, shown for PRO); `handleManage()` → `POST /api/payments/portal` → Billing Portal URL
- **CSV import UI**: card added to Settings → Data Management section (id=`csvImportGateTarget`); `importCSV()` function in `settings.js`; on 403 opens upgrade modal
- **`?subscription=success/cancelled`**: handled in both `app.js` (clears cache) and `settings.js` (shows toast, clears URL param)
- **`StripeService.getSubscription(User)`**: new public method returning `Optional<UserSubscription>`, used by `PaymentController`

## Tech Stack
- **Backend**: Java 17, Spring Boot 3.2.0 (Maven)
- **Package**: `com.smartfinance.dashboard`
- **Security**: Spring Security (BCrypt, form login + JWT Bearer + Google OAuth2)
- **Database**: H2 file-based (dev) / PostgreSQL (prod, via Flyway migrations)
- **Frontend**: Thymeleaf templates + Tailwind CDN + Chart.js CDN
- **PDF**: iTextPDF 5.5.13.3 / **CSV**: Apache Commons CSV
- **JWT**: io.jsonwebtoken jjwt 0.11.5 (`JwtService`, `JwtAuthenticationFilter`)
- **Payments**: Stripe Java SDK 24.3.0 (`StripeService`, `UserSubscription`)
- **Email**: Spring Mail / SendGrid SMTP (`EmailService` — async, disabled in dev)

## Run Command
```bash
cd "c:/Users/Maxim/VS Code projects/FinanceApp"
mvn spring-boot:run
```
Access at: http://localhost:8081

## Key Architecture Notes
- `app.js` is included on every authenticated page — provides toast, apiGet/Post/Put/Delete/Patch, formatCurrency (with currency conversion), currentCurrency, STATIC_RATES, convertAmount, setCurrency
- `app.js` injects a **fixed left sidebar** (240px, dark indigo) + **top header** (60px) on every authenticated page via `injectSidebar()`. It detects `body > nav` and hides it.
- `formatCurrency(amount, storedCurrency='USD')` converts from storedCurrency → currentCurrency using live/static rates
- Currency selector is now in the **sidebar footer** (not the nav). Theme toggle is also in sidebar, wired separately from theme.js.
- `PasswordConfig.java` separate from `SecurityConfig.java` (avoids circular dep)
- `application.properties` has `spring.jackson.serialization.write-dates-as-timestamps=false` (critical for LocalDate/LocalDateTime)
- All pages: body gets `has-sidebar` class → `padding-left: 240px; padding-top: 60px`. Sidebar collapses to 64px on toggle. Mobile: sidebar slides in with backdrop.
- Notification bell (#notificationBtn, #notificationBadge) is in the injected header, NOT in the page HTML (dashboard.js finds it after app.js injects it)

## Key Model Field Names (verified)
- **Budget**: `category`, `limitAmount`, `spentAmount`, `startDate`, `endDate`, `period`, `currency`
- **Investment**: `assetType`, `symbol`, `assetName`, `quantity`, `purchasePrice`, `currentPrice`, `currency`, `purchaseDate`
- **FinancialGoal**: `goalName`, `description`, `targetAmount`, `currentAmount`, `currency`, `targetDate` (LocalDate), `startDate`, `status`
- **Transaction**: `type` (INCOME/EXPENSE), `amount`, `currency`, `category`, `description`, `transactionDate`, `storageType`, `incomeSource`
- **StorageType**: `id`, `name`, `icon`, `builtIn` (table: `storage_types`)
- **IncomeSource**: `id`, `name`, `icon`, `builtIn` (table: `income_sources`)
- **User**: `username`, `email`, `password`, `role`, `baseCurrency`, `theme`, `provider`, `providerId`, `emailVerified`, `emailVerificationToken`, `emailVerificationTokenExpiry`, `passwordResetToken`, `passwordResetExpiry`
- **UserSubscription**: `user` (OneToOne), `stripeCustomerId`, `stripeSubscriptionId`, `status` (trialing/active/past_due/canceled), `plan` (FREE/PRO), `currentPeriodEnd`, `trialEnd`

## API Endpoints (key ones)
- GET/POST/PUT/DELETE `/api/transactions`
- GET/POST/PUT/DELETE `/api/budgets`
- POST `/api/budgets/recalculate` — recomputes spentAmount from actual transactions
- GET `/api/currency/rates` — live rates from frankfurter.app, fallback static, includes RSD
- GET/POST/PUT/DELETE `/api/investments`
- GET `/api/investments/portfolio/summary`
- GET/POST/PUT/DELETE `/api/goals`
- PATCH `/api/goals/{id}/add?amount=X`
- GET `/api/analytics/dashboard`
- GET `/api/analytics/trends?months=6` — monthly income/expenses for N months
- GET `/api/analytics/trends/daily?days=30` — daily income/expenses for last N days (new in v3)
- GET `/api/analytics/insights`
- GET `/api/analytics/storage-distribution` — balance per storage type (income−expenses)
- GET `/api/analytics/income-sources?month=M&year=Y` — income sum per source for period
- GET/POST/PUT/DELETE `/api/storage-types` — manage storage type lookup table
- GET/POST/PUT/DELETE `/api/income-sources` — manage income source lookup table
- GET `/api/forecast/spending`
- GET `/api/export/transactions/csv?startDate=...&endDate=...`
- GET `/api/export/report/pdf?year=&month=`

## API Endpoints (added in v2)
- GET/POST/PUT/DELETE `/api/categorization-rules` — pattern-based auto-categorization rules
- POST `/api/import/csv` — import transactions from CSV (multipart, params: file, currency)
- GET `/api/subscriptions` — detect recurring/subscription transactions
- GET `/api/subscriptions/upcoming?days=7` — subscriptions due within N days
- GET/POST/PUT/DELETE `/api/debts` — debt tracking (I_OWE / THEY_OWE)
- PATCH `/api/debts/{id}/settle` — mark debt as settled
- GET `/api/debts/summary` — totalIOwe, totalTheyOwe, activeCount
- GET `/api/analytics/health-score` — financial health score (0–100, grade A–F, breakdown)
- GET `/api/transactions/tags` — unique tags across all transactions (TreeSet)
- GET `/api/transactions/by-tag?tag=X` — transactions containing given tag

## Pages
All Thymeleaf templates in `src/main/resources/templates/`:
login, register, index, dashboard, transactions, investments, analytics, settings, forgot-password, reset-password

**Removed pages** (merged into Analytics/Settings): budgets, goals, reports, subscriptions, debts

## Navigation Structure (v4 redesign)
Sidebar has 5 items: Dashboard, Transactions, Assets (/investments), Analytics, Settings.
- **Analytics page** — tabs: Overview (charts), Budgets, Goals, Subscriptions, Debts. All managed via `analytics.js`.
- **Settings page** — sections: Account & Preferences, Currency, Data Management, Export & Reports, Subscription.
- Exports (CSV/PDF) and Forecast moved from /reports into Settings page.
- Subscription plan status shown in Settings footer card.

## Dashboard (simplified v4)
- Metric cards: Total Balance, Monthly Income, Monthly Expenses, Net Flow
- Recent Transactions (last 8, from `/api/transactions`, sliced client-side)
- Spending by Category doughnut
- Financial Health Score + Insights
- **Removed**: Income vs Expenses chart, Storage Distribution, Income Sources pie, Budget Status

## Analytics page (analytics.js)
- Tab: Overview — Income vs Expenses smooth line (Day/Week/Month/Year), Storage pie, Income Sources pie (month/year filter), Category breakdown table, Monthly summary table
- Tab: Budgets — full CRUD with progress bars
- Tab: Goals — full CRUD + contribute modal
- Tab: Subscriptions — summary + upcoming list + table (read-only)
- Tab: Debts — full CRUD with I Owe / They Owe tables

## Modal CSS structure (v4)
- `.modal-content` has `padding: 0` — header/body own their padding
- `.modal-header` — `padding: 1.25rem 1.5rem`, border-bottom, flex row
- `.modal-body` — `padding: 1.5rem`
- `.modal-title` / `.modal-close` — title and close button styles
- transactions.html modal uses `<div class="modal-content p-6">` (old pattern, Tailwind padding override)
- investments.html modal uses its own inline Tailwind (not modal-content class)

## Auth System (v5 — production-ready)
- **Frontend URL**: `app.frontend-url=${FRONTEND_URL:http://localhost:8081}` — set `FRONTEND_URL` env var in prod; used by `EmailService` for all email links (no hardcoded localhost)
- **Email verification token**: 24-hour expiry stored as `emailVerificationTokenExpiry` (LocalDateTime) on User. `verifyEmail()` throws `RuntimeException("EXPIRED_TOKEN")` or `RuntimeException("INVALID_TOKEN")` — catches distinct cases in `WebController`.
- **`UserService.generateVerificationToken(User)`**: generates UUID, sets token + expiry, saves user, returns raw token. Call this instead of manually setting token fields.
- **`/resend-verification`**: GET (form) + POST — resends verification email to any unverified address. Silently succeeds to prevent enumeration. Permitted route in SecurityConfig.
- **`POST /forgot-password`**: now returns `forgot-password` view with `sent=true` directly (no redirect to login). Model attr `sent` controls form vs confirmation state.
- **`POST /reset-password`** failure: returns `reset-password` view with `errorType="expired"` (shows "Link Expired or Invalid" state with "Request New Link" button).
- **Login page**: when `error=unverified`, model gets `resendVerification=true` → shows "Resend verification email →" link inline in the error box.
- **Pages**: `verify-email` uses `success`, `errorType` ("expired"/"invalid"); `forgot-password` uses `sent`; `reset-password` uses `success`, `errorType`, `error` (flash for mismatch).
- **`application.properties`** — dev profile (H2, Flyway disabled, mail disabled, placeholder keys)
- **`application-prod.yml`** — prod profile (PostgreSQL via env vars, Flyway enabled, mail enabled, ddl-auto=validate)
- **`deploy/nginx.conf`** — Nginx reverse proxy + rate limiting
- **`deploy/financeapp.service`** — systemd service with auto-restart
- **`deploy/deploy.sh`** — deployment script
- **`src/main/resources/db/migration/V1__init.sql`** — full PostgreSQL schema (all tables)
- **`src/main/resources/db/migration/V2__rate_limiting_placeholder.sql`** — placeholder
- **`.env.example`** — all required env vars documented
- `SecurityUtils` bean at `security/SecurityUtils.java` — call `securityUtils.getCurrentUser()` in controllers
- `AppConfig.java` — enables `@Async` (for EmailService) and `@EnableScheduling`
- All API controllers inject `SecurityUtils`, pass user to all service calls
- `ExportService`, `ForecastingService` — all methods take `User user` as param
- `PasswordConfig.java` holds `PasswordEncoder` bean (separate from SecurityConfig to avoid circular dep)

## User data isolation rule
Every entity has `@ManyToOne(fetch=LAZY) @JoinColumn(name="user_id") private User user`. All service methods take `User user`, all repository queries are user-scoped. Controllers get user via `securityUtils.getCurrentUser()`.

## Paywall (CSV import = premium)
`CsvImportController` calls `stripeService.hasActiveSubscription(user)` — returns 403 if no active/trialing subscription.

## New API Endpoints (v3 prod-ready)
- `POST /api/auth/token` — returns JWT (`{"token":"...","username":"..."}`)
- `POST /api/payments/checkout` — Stripe Checkout URL (premium subscription)
- `POST /api/payments/portal` — Stripe billing portal URL
- `GET /api/payments/status` — current subscription status
- `POST /api/payments/webhook` — Stripe webhook (signature verified)
- `GET/POST /forgot-password`, `GET/POST /reset-password`, `GET /verify-email` — auth pages
- `GET /api/forecast/savings?months=12` — n-month savings forecast (user-scoped)

## New Models (v2)
- **CategorizationRule**: `id, pattern, category, transactionType (nullable), priority (int), createdAt` (table: `categorization_rules`)
- **Debt**: `id, name, amount, currency, dueDate (nullable), direction (I_OWE/THEY_OWE), description, status (ACTIVE/SETTLED), createdAt, settledAt` (table: `debts`)
- **Transaction** now used for tags (comma-separated string field `tags`) + `isRecurring` / `recurringFrequency` for subscription detection

## Notes
- `DataSeeder.java` (base package) seeds default storage types + income sources on first startup (`count() == 0` guard)
- Default storage types (builtIn=true): Cash 💵, Bank Card 💳, Bank Account 🏦, Savings 💰, Crypto ₿
- Default income sources (builtIn=true): Salary 💼, Freelance 🖥️, Reselling 🛍️, Investments 📈
- builtIn types: cannot rename or delete them; custom types can be managed fully
- Storage type/income source are optional fields on Transaction; analytics only counts non-null values
- Dashboard v4: simplified — metric cards, Recent Transactions list (last 8), Spending by Category pie, Health Score, Insights. No income vs expenses chart on dashboard.
- Analytics page v4: tabs for Overview/Budgets/Goals/Subscriptions/Debts, lazy-loads tab data on first click.
- CSRF disabled for `/api/**` and `/h2-console/**`
- H2 console at `/h2-console` (sameOrigin frame policy)
- Budget category dropdown loads dynamically from `/api/categories/active` — must match transaction categories exactly
- `POST /api/budgets/recalculate` fixes budgets with wrong spentAmount (run from Settings page); now converts each transaction's currency to the budget's currency via `CurrencyService`
- **Currency conversion rule**: Transactions store original amount+currency unchanged. `BudgetService.updateBudgetSpending(category, amount, transactionCurrency)` converts to budget's currency before adding to `spentAmount`. DO NOT pass raw transaction amounts without currency — this causes phantom multiplication by exchange rate.
- **Analytics currency rule**: ALL monetary aggregations in `AnalyticsService` convert individual transaction amounts to USD via `convertToUSD()` before summing. The frontend always receives USD values from analytics endpoints and calls `formatCurrency(value)` (default storedCurrency='USD'). Never use raw SQL SUM across mixed-currency transactions — store nothing, convert everything at query time via `CurrencyService.getRatesFromUSD()`.
- `AnalyticsService` dependencies: `BudgetService`, `InvestmentService`, `TransactionRepository`, `CurrencyService` (removed `TransactionService` — not needed after USD-normalization rewrite)
- `.sidebar-currency option { background: #1e1b4b; color: #e0e7ff }` needed to show the native OS dropdown in dark colours (inherited `color: white` on the `<select>` itself is invisible in the popup)
- Supported currencies: USD, EUR, GBP, JPY, CAD, AUD, CHF, CNY, INR, BRL, RSD
- `investmentService.getTotalPortfolioValue()` returns `BigDecimal` (not double) — use `.compareTo(BigDecimal.ZERO)`
- Tags on Transaction: plain comma-separated String field; rendered as badge spans in UI; filterTag input does live client-side filtering
- Fast input: new transaction modal pre-fills type/category/storageType from localStorage (lastTxType/lastTxCategory/lastTxStorageType)
- Subscription detection: groups transactions by normalized description; 2+ occurrences with 25–35 day interval OR isRecurring=true
- Health score: savingsRate (30pts) + budgetAdherence (20pts) + spendingTrend (20pts) + hasInvestments (15pts) + emergencyReserve (15pts)
