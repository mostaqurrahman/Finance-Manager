const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read accounts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("accounts").get());
});

test("Cross-user isolation: Bob cannot read Alice's account", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("accounts").doc("acc_1").set({
      userId: ALICE_UID,
      name: "Cash",
      type: "CASH",
      currentBalance: 5000,
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_1").get());
});

test("Owner access: Alice can create her own account", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_alice").set({
      userId: ALICE_UID,
      name: "bKash Personal",
      type: "MOBILE_BANKING",
      provider: "bKash",
      openingBalance: 1000,
      currentBalance: 1000,
      createdAt: new Date(),
    })
  );
});

test("Schema validation: rejects invalid account type", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_bad").set({
      userId: ALICE_UID,
      name: "Invalid",
      type: "BITCOIN_WALLET", // Not in enum
      currentBalance: 1000,
      createdAt: new Date(),
    })
  );
});

test("Transaction: Alice can create her transaction and Bob cannot view it", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      type: "EXPENSE",
      amount: 450,
      accountId: "acc_alice",
      accountName: "bKash Personal",
      categoryName: "Food & Grocery",
      date: "2026-11-10",
      createdAt: new Date(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").get());
});

test("Notifications: Alice can create and mark read daily_digest notification, Bob cannot access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const notifRef = aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("daily_digest_2026-11-14");

  await assertSucceeds(
    notifRef.set({
      id: "daily_digest_2026-11-14",
      title: "Daily Finance Update (2026-11-14)",
      titleBn: "দৈনিক ফাইন্যান্স আপডেট (2026-11-14)",
      message: "Today: 2 entries | Expense: 500",
      messageBn: "আজকে ২টি লেনদেন হয়েছে",
      type: "daily_digest",
      isRead: false,
      link: "/",
      createdAt: new Date().toISOString(),
    })
  );

  await assertSucceeds(notifRef.update({ isRead: true }));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("notifications").doc("daily_digest_2026-11-14").get()
  );
});

test("Notifications: rejects invalid notification type", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_bad").set({
      id: "notif_bad",
      title: "Bad",
      message: "Bad type",
      type: "unknown_type",
      isRead: false,
      createdAt: new Date().toISOString(),
    })
  );
});

test("Settings: Alice can save daily reminder preferences, rejects invalid time format", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const settingsRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("general");

  await assertSucceeds(
    settingsRef.set({
      language: "bn",
      currency: "BDT",
      timezone: "Asia/Dhaka",
      dailyReminderEnabled: true,
      dailyReminderTime: "21:00",
      lastDailyDigestDate: "2026-11-14",
    })
  );

  await assertFails(
    settingsRef.set({
      language: "bn",
      currency: "BDT",
      timezone: "Asia/Dhaka",
      dailyReminderEnabled: true,
      dailyReminderTime: "9pm",
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("settings").doc("general").get()
  );
});

