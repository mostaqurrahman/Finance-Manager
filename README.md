# হিসাব খাতা (Personal Finance Manager)

A multi-user, multi-device, offline-first personal finance management application built for Android with Jetpack Compose, Firebase Cloud Firestore, Firebase Authentication, and Gemini AI (Firebase AI Logic).

## Key Features

1. **Multi-User Isolation**: Anyone can register and sign in securely with their own Google account via Jetpack Credential Manager. All financial data is scoped strictly to `users/{userId}`.
2. **Multi-Device & Cloud Sync**: Data seamlessly synchronizes in real time across devices using Cloud Firestore with persistent on-device caching.
3. **Bangladeshi Taka (BDT ৳)**: Currency formatting in South Asian grouping format (e.g., `৳1,00,000`).
4. **Bangali Natural Voice Entry (Gemini 3.8 Flash)**: Speak or type in Bengali (e.g., *"আজকে বাজার করার জন্য বিকাশ থেকে ১২০০ টাকা খরচ করেছি, এটা Family Expense হিসেবে রাখো"*), and Gemini AI extracts structured transaction fields (Type, Amount, Account, Category, Person, Description). Never saved without explicit user review and confirmation.
5. **Single-Truth Financial Engine**:
   - Accounts: Cash, Bank, Mobile Banking (bKash, Nagad, Rocket, Upay), Savings, DPS / FDR.
   - Genuine Income & Expense calculations (excluding internal transfers and principal repayments).
   - Linked refunds reducing net expenses.
   - Loan Given (Receivable Asset) & Loan Taken (Payable Liability) with partial/full repayment tracking.
   - Monthly category budgets with customizable alert thresholds (70%, 85%, 100%).
   - Savings goals with progress indicators.
6. **Bilingual Experience**: Built-in toggle between বাংলা (Bengali, default) and English.
7. **Offline-First**: Uses Firestore Persistent Cache so transactions and accounts work even without active network connectivity.
