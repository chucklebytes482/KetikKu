const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user: cannot read or write sync profiles or backups", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("backups")
      .get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      autoSync: true,
      driveFolderName: "KetikKu",
      lastSyncedAtMillis: 1700000000000,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: valid UserSyncProfile create & partial update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      autoSync: true,
      driveFolderName: "KetikKu",
      lastSyncedAtMillis: 1700000000000,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      autoSync: false,
      driveFolderId: "folder_abc_123",
      lastSyncedAtMillis: 1700000005000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      autoSync: true,
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: valid BackupSnapshot create, update, list & delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const backupsRef = aliceDb.collection("users").doc(ALICE_UID).collection("backups");

  await assertSucceeds(
    backupsRef.doc("latest").set({
      userId: ALICE_UID,
      deviceName: "Pixel 9",
      driveFileId: "drive_file_1",
      settingsJson: "{\"themeId\":\"SLATE_CYAN\",\"numberRow\":true}",
      dictionaryCount: 4,
      syncSource: "DRIVE_AND_CLOUD",
      syncedAtMillis: 1700000000000,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(backupsRef.get());

  await assertSucceeds(
    backupsRef.doc("latest").update({
      dictionaryCount: 6,
      syncedAtMillis: 1700000010000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid syncSource enum
  await assertFails(
    backupsRef.doc("bad_enum").set({
      userId: ALICE_UID,
      deviceName: "Pixel 9",
      settingsJson: "{}",
      dictionaryCount: 0,
      syncSource: "UNKNOWN",
      syncedAtMillis: 1700000000000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject extra @DocumentId `id` field
  await assertFails(
    backupsRef.doc("extra_id").set({
      id: "extra_id",
      userId: ALICE_UID,
      deviceName: "Pixel 9",
      settingsJson: "{}",
      dictionaryCount: 0,
      syncSource: "CLOUD_ONLY",
      syncedAtMillis: 1700000000000,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(backupsRef.doc("latest").delete());
});

test("Cross-user isolation: Bob cannot read, list, or write Alice's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const now = new Date(Date.now() - 5000);
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      autoSync: true,
      driveFolderName: "KetikKu",
      lastSyncedAtMillis: 1700000000000,
      createdAt: now,
      updatedAt: now,
    });
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("backups")
      .doc("latest")
      .set({
        userId: ALICE_UID,
        deviceName: "Pixel 9",
        settingsJson: "{}",
        dictionaryCount: 1,
        syncSource: "CLOUD_ONLY",
        syncedAtMillis: 1700000000000,
        createdAt: now,
        updatedAt: now,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ autoSync: false })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("backups").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("backups")
      .doc("latest")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("backups")
      .doc("latest")
      .delete()
  );
});
