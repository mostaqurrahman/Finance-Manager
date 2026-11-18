# Security Architecture

## 1. Authentication
- Powered by Firebase Authentication with Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`).
- No anonymous or hardcoded authentication paths.
- Sign out terminates credentials via `clearCredentialState` before calling `Firebase.auth.signOut()`.

## 2. Multi-User Isolation & Zero-Trust
- All user financial collections reside under `/users/{userId}/...`.
- Security rules strictly mandate:
  `request.auth != null && request.auth.uid == userId`
- Single document operations validate document ID parameters with `isValidId()`.
- Validations enforce strict bounds on names, numeric amounts (preventing negative or infinite values), and server timestamps.

## 3. Server Timestamps & Immutability
- Client writes `FieldValue.serverTimestamp()`.
- Document `createdAt` is immutable upon update (`incoming().createdAt == existing().createdAt`).
- Update operations are gated using `.affectedKeys().hasOnly([...])`.

## 4. App Check & Gemini API Security
- Firebase AI Logic uses App Check attestation for API call authentication.
- Launch intent debug token extraction wired into `MainActivity.onCreate()` using synchronous `.commit()` storage.
