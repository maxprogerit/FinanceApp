package com.smartfinance.dashboard.config;

import com.smartfinance.dashboard.model.*;
import com.smartfinance.dashboard.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds a realistic demo account (user1@gmail.com / 1111) and an
 * admin account (admin@smartfinance.com / admin123456) on first startup.
 * Runs after DataSeeder (@Order(2)) so built-in storage types exist.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private final UserRepository         userRepository;
    private final TransactionRepository  transactionRepository;
    private final InvestmentRepository   investmentRepository;
    private final BudgetRepository       budgetRepository;
    private final FinancialGoalRepository goalRepository;
    private final DebtRepository         debtRepository;
    private final PasswordEncoder        passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seedAdmin();
        seedDemoUser();
    }

    // ── Admin user ────────────────────────────────────────────────────────────

    private void seedAdmin() {
        if (userRepository.findByEmail("admin@smartfinance.com").isPresent()) return;

        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@smartfinance.com");
        admin.setPassword(passwordEncoder.encode("admin123456"));
        admin.setRole("ADMIN");
        admin.setEmailVerified(true);
        admin.setBaseCurrency("EUR");
        admin.setTheme("light");
        admin.setBio("Platform administrator.");
        userRepository.save(admin);
        log.info("[DemoDataSeeder] Admin account created: admin@smartfinance.com");
    }

    // ── Demo user ────────────────────────────────────────────────────────────

    private void seedDemoUser() {
        if (userRepository.findByEmail("user1@gmail.com").isPresent()) return;

        User user = new User();
        user.setUsername("user1");
        user.setEmail("user1@gmail.com");
        user.setPassword(passwordEncoder.encode("1111"));
        user.setRole("USER");
        user.setEmailVerified(true);
        user.setBaseCurrency("USD");
        user.setTheme("light");
        user.setBio("Software engineer & financial enthusiast. Tracking my path to financial freedom.");
        user.setAvatarUrl("https://api.dicebear.com/7.x/initials/svg?seed=user1&backgroundColor=6366f1");
        userRepository.save(user);

        seedTransactions(user);
        seedInvestments(user);
        seedBudgets(user);
        seedGoals(user);
        seedDebts(user);

        log.info("[DemoDataSeeder] Demo user seeded: user1@gmail.com (password: 1111)");
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    private void seedTransactions(User user) {
        LocalDate today = LocalDate.now();

        // Helper to add a transaction
        for (int m = 5; m >= 0; m--) {
            LocalDate month = today.minusMonths(m);
            int yr  = month.getYear();
            int mo  = month.getMonthValue();

            // ── Income ──────────────────────────────────────────────────────

            // Salary — 1st of month
            tx(user, "INCOME", 5200.00, "USD", "Salary",
                    "Monthly salary", dt(yr, mo, 1), "Bank Account","Salary", false, null);

            // Freelance — mid-month (not every month)
            if (m % 2 == 0) {
                tx(user, "INCOME", 950.00 + (m * 120), "USD", "Freelance",
                        "Web development project", dt(yr, mo, 15),
                        "Bank Account", "Freelance", false, null);
            }
            if (m == 1) {
                tx(user, "INCOME", 1400.00, "USD", "Freelance",
                        "Mobile app consulting", dt(yr, mo, 20),
                        "Bank Account", "Freelance", false, null);
            }

            // Investment dividend (quarterly — months 0, 3)
            if (m == 0 || m == 3) {
                tx(user, "INCOME", 145.00, "USD", "Investments",
                        "Stock dividend payment", dt(yr, mo, 25),
                        "Bank Account", "Investments", false, null);
            }

            // ── Fixed monthly expenses ───────────────────────────────────

            // Rent — 1st
            tx(user, "EXPENSE", 1200.00, "USD", "Housing",
                    "Monthly rent", dt(yr, mo, 2),
                    "Bank Account", null, true, "MONTHLY");

            // Electricity
            int elec = 85 + (m * 7 % 40);
            tx(user, "EXPENSE", elec, "USD", "Bills & Utilities",
                    "Electricity bill", dt(yr, mo, 5),
                    "Bank Card", null, true, "MONTHLY");

            // Internet
            tx(user, "EXPENSE", 59.99, "USD", "Bills & Utilities",
                    "Internet subscription", dt(yr, mo, 6),
                    "Bank Card", null, true, "MONTHLY");

            // Netflix
            tx(user, "EXPENSE", 15.99, "USD", "Subscriptions",
                    "Netflix subscription", dt(yr, mo, 8),
                    "Bank Card", null, true, "MONTHLY");

            // Spotify
            tx(user, "EXPENSE", 9.99, "USD", "Subscriptions",
                    "Spotify Premium", dt(yr, mo, 8),
                    "Bank Card", null, true, "MONTHLY");

            // Gym membership
            tx(user, "EXPENSE", 35.00, "USD", "Health & Fitness",
                    "Gym membership", dt(yr, mo, 10),
                    "Bank Card", null, true, "MONTHLY");

            // Phone bill
            tx(user, "EXPENSE", 45.00, "USD", "Bills & Utilities",
                    "Phone bill", dt(yr, mo, 12),
                    "Bank Card", null, true, "MONTHLY");

            // ── Groceries (2-3 per month) ────────────────────────────────

            tx(user, "EXPENSE", 87.50 + (m * 5), "USD", "Groceries",
                    "Weekly grocery run", dt(yr, mo, 4),
                    "Bank Card", null, false, null);
            tx(user, "EXPENSE", 72.30 + (m * 3), "USD", "Groceries",
                    "Supermarket", dt(yr, mo, 11),
                    "Bank Card", null, false, null);
            tx(user, "EXPENSE", 95.80 + (m * 4), "USD", "Groceries",
                    "Weekly grocery run", dt(yr, mo, 18),
                    "Bank Card", null, false, null);

            // ── Dining & Restaurants (3 per month) ──────────────────────

            tx(user, "EXPENSE", 42.00 + m, "USD", "Food & Dining",
                    "Dinner with friends", dt(yr, mo, 6),
                    "Bank Card", null, false, null);
            tx(user, "EXPENSE", 28.50, "USD", "Food & Dining",
                    "Lunch at office", dt(yr, mo, 13),
                    "Bank Card", null, false, null);
            tx(user, "EXPENSE", 65.00 + (m * 2), "USD", "Food & Dining",
                    "Restaurant — date night", dt(yr, mo, 22),
                    "Cash", null, false, null);

            // ── Coffee (3 per month) ─────────────────────────────────────

            tx(user, "EXPENSE", 6.50, "USD", "Coffee & Snacks",
                    "Morning coffee", dt(yr, mo, 3),
                    "Cash", null, false, null);
            tx(user, "EXPENSE", 7.20, "USD", "Coffee & Snacks",
                    "Coffee & pastry", dt(yr, mo, 9),
                    "Cash", null, false, null);
            tx(user, "EXPENSE", 5.80, "USD", "Coffee & Snacks",
                    "Afternoon coffee", dt(yr, mo, 16),
                    "Cash", null, false, null);

            // ── Transportation (2 per month) ─────────────────────────────

            tx(user, "EXPENSE", 55.00 + (m * 3), "USD", "Transportation",
                    "Monthly transit pass", dt(yr, mo, 1),
                    "Bank Card", null, true, "MONTHLY");
            tx(user, "EXPENSE", 38.00 + (m * 2), "USD", "Transportation",
                    "Uber rides", dt(yr, mo, 20),
                    "Bank Card", null, false, null);

            // ── Optional / irregular ─────────────────────────────────────

            // Shopping (alternating months)
            if (m % 3 == 0) {
                tx(user, "EXPENSE", 129.99, "USD", "Shopping",
                        "Clothing — seasonal", dt(yr, mo, 14),
                        "Bank Card", null, false, null);
            }
            if (m == 5) {
                tx(user, "EXPENSE", 249.00, "USD", "Shopping",
                        "New running shoes", dt(yr, mo, 17),
                        "Bank Card", null, false, null);
            }

            // Healthcare (twice total)
            if (m == 4) {
                tx(user, "EXPENSE", 80.00, "USD", "Healthcare",
                        "Doctor visit co-pay", dt(yr, mo, 9),
                        "Bank Card", null, false, null);
            }
            if (m == 1) {
                tx(user, "EXPENSE", 35.00, "USD", "Healthcare",
                        "Pharmacy", dt(yr, mo, 22),
                        "Cash", null, false, null);
            }

            // Education / Books
            if (m == 5) {
                tx(user, "EXPENSE", 49.99, "USD", "Education",
                        "Online course — React Advanced", dt(yr, mo, 12),
                        "Bank Card", null, false, null);
            }
            if (m == 2) {
                tx(user, "EXPENSE", 28.00, "USD", "Education",
                        "Programming books", dt(yr, mo, 7),
                        "Bank Card", null, false, null);
            }

            // Savings transfer (to savings account)
            tx(user, "EXPENSE", 500.00, "USD", "Savings",
                    "Monthly savings transfer", dt(yr, mo, 28),
                    "Bank Account", null, true, "MONTHLY");
        }

        // A few EUR-denominated transactions to show multi-currency
        LocalDate twoMonthsAgo   = today.minusMonths(2);
        LocalDate threeMonthsAgo = today.minusMonths(3);
        tx(user, "EXPENSE", 120.00, "EUR", "Travel",
                "Airbnb — Berlin weekend", dt(twoMonthsAgo.getYear(), twoMonthsAgo.getMonthValue(), 15),
                "Bank Card", null, false, null);
        tx(user, "EXPENSE", 85.00, "EUR", "Food & Dining",
                "Restaurant — Berlin", dt(twoMonthsAgo.getYear(), twoMonthsAgo.getMonthValue(), 16),
                "Cash", null, false, null);
        tx(user, "INCOME", 300.00, "EUR", "Freelance",
                "EU client payment", dt(threeMonthsAgo.getYear(), threeMonthsAgo.getMonthValue(), 20),
                "Bank Account", "Freelance", false, null);
    }

    // ── Investments ───────────────────────────────────────────────────────────

    private void seedInvestments(User user) {
        LocalDate today = LocalDate.now();

        inv(user, "STOCK", "AAPL", "Apple Inc.", 10,     150.00, 185.50, "USD", today.minusMonths(8));
        inv(user, "STOCK", "MSFT", "Microsoft Corp.", 5, 280.00, 378.85, "USD", today.minusMonths(10));
        inv(user, "STOCK", "GOOGL","Alphabet Inc.", 3,   130.00, 168.20, "USD", today.minusMonths(6));
        inv(user, "ETF",   "SPY",  "S&P 500 ETF", 8,    420.00, 521.00, "USD", today.minusMonths(12));
        inv(user, "CRYPTO","BTC",  "Bitcoin", 0.25,      42000.00, 67500.00, "USD", today.minusMonths(14));
        inv(user, "CRYPTO","ETH",  "Ethereum", 2.0,      2200.00, 3450.00,  "USD", today.minusMonths(12));
        inv(user, "STOCK", "NVDA", "NVIDIA Corp.", 4,    450.00, 880.00, "USD", today.minusMonths(5));
        inv(user, "MUTUAL_FUND","VTSAX","Vanguard Total Stock", 15, 210.00, 242.00, "USD", today.minusMonths(18));
    }

    // ── Budgets ───────────────────────────────────────────────────────────────

    private void seedBudgets(User user) {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end   = start.plusMonths(1).minusDays(1);

        budget(user, "Food & Dining",     800.00, 482.50, "MONTHLY", start, end, "USD");
        budget(user, "Groceries",         400.00, 255.60, "MONTHLY", start, end, "USD");
        budget(user, "Transportation",    200.00,  93.00, "MONTHLY", start, end, "USD");
        budget(user, "Subscriptions",      80.00,  25.98, "MONTHLY", start, end, "USD");
        budget(user, "Bills & Utilities", 250.00, 189.99, "MONTHLY", start, end, "USD");
        budget(user, "Shopping",          300.00, 129.99, "MONTHLY", start, end, "USD");
        budget(user, "Health & Fitness",  100.00,  35.00, "MONTHLY", start, end, "USD");
        budget(user, "Coffee & Snacks",    60.00,  19.50, "MONTHLY", start, end, "USD");
    }

    // ── Financial goals ───────────────────────────────────────────────────────

    private void seedGoals(User user) {
        LocalDate today = LocalDate.now();

        goal(user, "Emergency Fund",   "6 months of expenses as safety net",
                30000, 12500, "USD",
                today.plusYears(2), "EMERGENCY_FUND", "IN_PROGRESS");
        goal(user, "Dream Vacation",   "Trip to Japan in spring",
                5000, 1200, "USD",
                today.plusMonths(10), "VACATION", "IN_PROGRESS");
        goal(user, "New Car",          "Upgrade from current sedan",
                25000, 6800, "USD",
                today.plusYears(2).plusMonths(6), "CAR", "IN_PROGRESS");
        goal(user, "Investment Portfolio","Reach $100k invested assets",
                100000, 38500, "USD",
                today.plusYears(3), "RETIREMENT", "IN_PROGRESS");
        goal(user, "Home Office Upgrade","New desk, monitor and chair",
                2500, 2500, "USD",
                today.minusMonths(1), "EDUCATION", "COMPLETED");
    }

    // ── Debts ─────────────────────────────────────────────────────────────────

    private void seedDebts(User user) {
        LocalDate today = LocalDate.now();

        debt(user, "Alex — Loan",       500.00, "USD",
                today.plusMonths(2), "I_OWE",   "Borrowed for car repair",  "ACTIVE");
        debt(user, "Sarah — Split bill",  85.00, "USD",
                null, "I_OWE",   "Concert tickets split",    "SETTLED");
        debt(user, "Mike — Birthday",   200.00, "USD",
                today.plusMonths(1), "THEY_OWE","Lent for birthday present", "ACTIVE");
        debt(user, "Tom — Dinner",       35.00, "USD",
                null, "THEY_OWE","Team dinner split",        "SETTLED");
        debt(user, "Nike loan",         320.00, "USD",
                today.plusMonths(3), "I_OWE",   "Borrowed for new laptop accessories", "ACTIVE");
        debt(user, "James — Rent share",600.00, "USD",
                today.minusDays(5), "THEY_OWE","Monthly rent share",        "ACTIVE");
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private void tx(User user, String type, double amount, String currency,
                    String category, String desc, LocalDateTime date,
                    String storage, String source, boolean recurring, String freq) {
        Transaction t = new Transaction();
        t.setUser(user);
        t.setType(type);
        t.setAmount(BigDecimal.valueOf(amount));
        t.setCurrency(currency);
        t.setCategory(category);
        t.setDescription(desc);
        t.setTransactionDate(date);
        t.setStorageType(storage);
        t.setIncomeSource(source);
        t.setIsRecurring(recurring);
        t.setRecurringFrequency(freq);
        transactionRepository.save(t);
    }

    private void inv(User user, String assetType, String symbol, String name,
                     double qty, double buyPrice, double curPrice,
                     String currency, LocalDate date) {
        Investment i = new Investment();
        i.setUser(user);
        i.setAssetType(assetType);
        i.setSymbol(symbol);
        i.setAssetName(name);
        i.setQuantity(BigDecimal.valueOf(qty));
        i.setPurchasePrice(BigDecimal.valueOf(buyPrice));
        i.setCurrentPrice(BigDecimal.valueOf(curPrice));
        i.setCurrency(currency);
        i.setPurchaseDate(date.atStartOfDay());
        investmentRepository.save(i);
    }

    private void budget(User user, String category, double limit, double spent,
                        String period, LocalDate start, LocalDate end, String currency) {
        Budget b = new Budget();
        b.setUser(user);
        b.setCategory(category);
        b.setLimitAmount(BigDecimal.valueOf(limit));
        b.setSpentAmount(BigDecimal.valueOf(spent));
        b.setPeriod(period);
        b.setStartDate(start);
        b.setEndDate(end);
        b.setCurrency(currency);
        budgetRepository.save(b);
    }

    private void goal(User user, String name, String desc,
                      double target, double current, String currency,
                      LocalDate targetDate, String category, String status) {
        FinancialGoal g = new FinancialGoal();
        g.setUser(user);
        g.setGoalName(name);
        g.setDescription(desc);
        g.setTargetAmount(BigDecimal.valueOf(target));
        g.setCurrentAmount(BigDecimal.valueOf(current));
        g.setCurrency(currency);
        g.setTargetDate(targetDate);
        g.setStartDate(LocalDate.now().minusMonths(3));
        g.setCategory(category);
        g.setStatus(status);
        goalRepository.save(g);
    }

    private void debt(User user, String name, double amount, String currency,
                      LocalDate dueDate, String direction, String desc, String status) {
        Debt d = new Debt();
        d.setUser(user);
        d.setName(name);
        d.setAmount(BigDecimal.valueOf(amount));
        d.setCurrency(currency);
        d.setDueDate(dueDate);
        d.setDirection(direction);
        d.setDescription(desc);
        d.setStatus(status);
        debtRepository.save(d);
    }

    private LocalDateTime dt(int year, int month, int day) {
        return LocalDate.of(year, month, Math.min(day, LocalDate.of(year, month, 1).lengthOfMonth()))
                .atTime(9 + (day % 12), (day * 7) % 60);
    }
}
