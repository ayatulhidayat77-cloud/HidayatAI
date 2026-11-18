const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-hidayata-4b7be439-f25e-4431-b37b-0747e2b4796a";
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

function getDb(context) {
  return context.firestore(DATABASE_ID);
}

test("Unauthenticated user: cannot read or write schedules or notes", async () => {
  const unauthDb = getDb(testEnv.unauthenticatedContext());
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("schedules").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("schedules").doc("sched_1").set({
      userId: ALICE_UID,
      title: "Deep Work",
      description: "Focus on core tasks",
      category: "WORK",
      priority: "HIGH",
      timeSlot: "08:00 - 10:00",
      dateStr: "2026-11-18",
      completed: false,
      aiGenerated: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notes").get());
});

test("Authenticated owner: valid ScheduleItem create, list, partial update, and delete succeed", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const created = new Date(Date.now() - 10000);

  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("schedules").doc("sched_1");

  // Exact Kotlin repository create payload (no @DocumentId `id` field)
  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      title: "Sesi Fokus Pagi & Perencanaan AI",
      description: "Menyelesaikan laporan utama dengan bantuan Hidayat AI",
      category: "WORK",
      priority: "HIGH",
      timeSlot: "08:00 - 09:30",
      dateStr: "2026-11-18",
      completed: false,
      aiGenerated: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List query matching Kotlin repository observeSchedules
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("schedules").get()
  );

  // Partial update: toggle completed status
  await assertSucceeds(
    docRef.update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Partial update: edit details
  await assertSucceeds(
    docRef.update({
      title: "Sesi Fokus Pagi Diperbarui",
      description: "Prioritas utama selesai",
      category: "STUDY",
      priority: "MEDIUM",
      timeSlot: "08:30 - 10:00",
      dateStr: "2026-11-18",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject mutating immutable createdAt or aiGenerated
  await assertFails(
    docRef.update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertFails(
    docRef.update({
      aiGenerated: false,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject extra/ghost fields (@DocumentId `id` or unknown key)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("schedules").doc("sched_bad").set({
      id: "sched_bad",
      userId: ALICE_UID,
      title: "Invalid ID field",
      description: "Desc",
      category: "WORK",
      priority: "HIGH",
      timeSlot: "09:00 - 10:00",
      dateStr: "2026-11-18",
      completed: false,
      aiGenerated: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(docRef.delete());
});

test("Authenticated owner: valid ProductivityNote create, list, update, and delete succeed", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const created = new Date(Date.now() - 10000);
  const noteRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1");

  await assertSucceeds(
    noteRef.set({
      userId: ALICE_UID,
      title: "Analisis Dokumen Kerja",
      content: "Ringkasan hasil analisis gambar dokumen oleh Gemini 4 Argon.",
      tag: "VISION_ANALYSIS",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").get()
  );

  await assertSucceeds(
    noteRef.update({
      title: "Analisis Dokumen Kerja (Revisi)",
      content: "Ringkasan diperbarui.",
      tag: "AI_INSIGHT",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid tag enum
  await assertFails(
    noteRef.update({
      tag: "INVALID_TAG",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(noteRef.delete());
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice's schedules or notes", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("schedules").doc("alice_sched").set({
      userId: ALICE_UID,
      title: "Alice Private Schedule",
      description: "Secret work",
      category: "PERSONAL",
      priority: "HIGH",
      timeSlot: "10:00 - 11:00",
      dateStr: "2026-11-18",
      completed: false,
      aiGenerated: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("schedules").doc("alice_sched").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("schedules").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("schedules").doc("alice_sched").update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("schedules").doc("alice_sched").delete()
  );
});
