import { initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider, signInWithPopup, signOut as fbSignOut } from 'firebase/auth';
import { initializeFirestore, persistentLocalCache, persistentMultipleTabManager } from 'firebase/firestore';

const firebaseConfig = {
  apiKey: "AIzaSyAbBvkmQKLp0-3QC32mRx1hWNYaPwWQUsc",
  authDomain: "gen-lang-client-0991529757.firebaseapp.com",
  projectId: "gen-lang-client-0991529757",
  storageBucket: "gen-lang-client-0991529757.firebasestorage.app",
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const googleProvider = new GoogleAuthProvider();

// Initialize Firestore targeting the shared provisioned database instance with offline persistence
export const db = initializeFirestore(app, {
  localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() })
}, "ai-studio-android-personal-2de35ddf-607a-409a-9c5f-b8c5a5f07f12");

export { signInWithPopup, fbSignOut };
