# Personal Finance Manager — Web & Android Cross-Platform Architecture

The **Personal Finance Manager** now features a complete cross-platform ecosystem:
1. **Android App**: Native Jetpack Compose app with Material 3, Gemini AI voice transaction parsing, Credential Manager Google Sign-In, and offline-first Firestore sync.
2. **Web App (`/web`)**: Modern React 18 + TypeScript + Tailwind CSS Single-Page Application (SPA) / Progressive Web App (PWA) with Google Sign-In, real-time Firestore listeners, BDT (৳) currency formatting, Bengali voice & natural language entry, inter-account transfers, loan tracking, budget alerts, and JSON export.

---

## 🔄 Cross-Device Real-Time Sync

Both Android and Web communicate directly with the same provisioned Firestore database instance:
- **Database ID**: `ai-studio-android-personal-2de35ddf-607a-409a-9c5f-b8c5a5f07f12`
- **Security Rule**: Zero-trust authenticated ownership (`request.auth.uid == userId`)
- **Shared Data Paths**:
  - `users/{uid}/accounts` (Cash, Bank, Mobile Banking, Credit Card, Savings)
  - `users/{uid}/transactions` (Income, Expense, Transfer, Loan Repayment, Refund)
  - `users/{uid}/loans` (Lent / Borrowed with interest & repayment tracking)
  - `users/{uid}/budgets` (Monthly category limits with real-time percentage indicators)
  - `users/{uid}/savingsGoals` (Target amounts and deadlines)
  - `users/{uid}/categories` & `users/{uid}/people`

*Any transaction, account creation, or loan logged on your phone reflects instantly on your web browser, and vice versa.*

---

## 💻 Web App Tech Stack
- **Framework**: React 18 + Vite + TypeScript
- **Styling**: Tailwind CSS with dark slate / navy aesthetic matching the Android app design
- **Icons**: Lucide React
- **Backend SDK**: Firebase JS SDK v11 (Auth + Firestore with indexedDB multi-tab offline cache)
- **Voice Recognition**: Web Speech API (`webkitSpeechRecognition` / `SpeechRecognition`) configured for Bengali (`bn-BD`) with auto-fallback to English (`en-US`)

---

## 🚀 Running the Web App

### Running inside this workspace:
The Vite dev server is pre-configured and actively listening on port `3000`, proxied automatically to the live preview endpoint:
```bash
npm --prefix web run dev
```

### Building for production:
```bash
npm --prefix web run build
```
The optimized bundle is output to `/web/dist/` (ready to deploy to Firebase Hosting, Vercel, Netlify, or any static host).
