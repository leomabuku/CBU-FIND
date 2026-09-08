import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { afterAll, afterEach, beforeAll, describe, it } from "vitest";
import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, doc, getDoc, getDocs, orderBy, query, setDoc, where } from "firebase/firestore";

let environment: RulesTestEnvironment;
const owner = "owner-user", claimant = "claimant-user", stranger = "stranger-user", moderator = "moderator-user";

beforeAll(async () => {
  // Keep the rules test environment on the same project ID as .firebaserc.
  // Firebase's single-project emulator mode otherwise opens duplicate client
  // connections and can time out while shutting down on Windows.
  environment = await initializeTestEnvironment({ projectId: "cbu-lost-and-found", firestore: { rules: await readFile(resolve(process.cwd(), "../../firestore.rules"), "utf8") } });
});
afterEach(async () => environment.clearFirestore());
afterAll(async () => environment.cleanup());

async function seed() {
  await environment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await Promise.all([
      setDoc(doc(db, "users", owner), { email: "private@example.test", studentId: "PRIVATE", phone: "+260000", status: "ACTIVE" }),
      setDoc(doc(db, "publicProfiles", owner), { name: "Owner", programme: "Engineering", photoUrl: "", status: "ACTIVE" }),
      setDoc(doc(db, "items", "item-1"), { userId: owner, status: "MATCHED", title: "Keys", date: 2 }),
      setDoc(doc(db, "items", "item-active"), { userId: owner, status: "ACTIVE", title: "Bag", date: 3 }),
      setDoc(doc(db, "items", "item-removed"), { userId: owner, status: "REMOVED", title: "Hidden", date: 1 }),
      setDoc(doc(db, "reportContacts", "item-1"), { itemId: "item-1", ownerId: owner, contactInfo: "+260111" }),
      setDoc(doc(db, "claims", `item-1_${claimant}`), { itemId: "item-1", itemOwnerId: owner, claimantId: claimant, status: "ACCEPTED", updatedAt: 1 }),
      setDoc(doc(db, "conversations", "conversation-1"), { participantIds: [owner, claimant], updatedAt: 1 }),
      setDoc(doc(db, "conversations", "conversation-1", "messages", "message-1"), { senderId: owner, text: "private", createdAt: 1 }),
      setDoc(doc(db, "roles", moderator), { role: "MODERATOR" }),
      setDoc(doc(db, "moderationCases", "case-1"), { reporterId: claimant, status: "OPEN", updatedAt: 1, context: [{ id: "message-1", text: "limited context" }] }),
      setDoc(doc(db, "deviceTokens", "owner-token"), { uid: owner, token: "private-device-token", enabled: true }),
      setDoc(doc(db, "deletionJobs", owner), { uid: owner, status: "QUEUED", referenceId: "private-reference" }),
    ]);
  });
}

describe("private profiles and contacts", () => {
  it("allows only the profile owner or an admin to read private user data", async () => {
    await seed();
    await assertSucceeds(getDoc(doc(environment.authenticatedContext(owner).firestore(), "users", owner)));
    await assertFails(getDoc(doc(environment.authenticatedContext(stranger).firestore(), "users", owner)));
    await assertSucceeds(getDoc(doc(environment.authenticatedContext(stranger).firestore(), "publicProfiles", owner)));
  });
  it("reveals protected contact only to the owner and accepted claimant", async () => {
    await seed();
    await assertSucceeds(getDoc(doc(environment.authenticatedContext(owner).firestore(), "reportContacts", "item-1")));
    await assertSucceeds(getDoc(doc(environment.authenticatedContext(claimant).firestore(), "reportContacts", "item-1")));
    await assertFails(getDoc(doc(environment.authenticatedContext(stranger).firestore(), "reportContacts", "item-1")));
    await assertFails(getDoc(doc(environment.authenticatedContext(moderator).firestore(), "reportContacts", "item-1")));
  });
});

describe("trusted mutations and conversations", () => {
  it("allows a feed constrained to visible statuses and rejects an unconstrained feed", async () => {
    await seed();
    const db = environment.authenticatedContext(stranger).firestore();
    await assertSucceeds(getDocs(query(collection(db, "items"), where("status", "in", ["ACTIVE", "MATCHED", "RESOLVED"]), orderBy("date", "desc"))));
    await assertFails(getDocs(query(collection(db, "items"), orderBy("date", "desc"))));
  });

  it("denies direct client writes that must pass through the Worker", async () => {
    await seed();
    const db = environment.authenticatedContext(owner).firestore();
    await assertFails(setDoc(doc(db, "items", "client-write"), { userId: owner, status: "ACTIVE" }));
    await assertFails(setDoc(doc(db, "claims", "client-claim"), { claimantId: owner }));
    await assertFails(setDoc(doc(db, "conversations", "conversation-1", "messages", "client-message"), { senderId: owner, text: "bypass" }));
  });
  it("allows only participants to read a conversation and its messages", async () => {
    await seed();
    await assertSucceeds(getDoc(doc(environment.authenticatedContext(claimant).firestore(), "conversations", "conversation-1", "messages", "message-1")));
    await assertFails(getDoc(doc(environment.authenticatedContext(stranger).firestore(), "conversations", "conversation-1", "messages", "message-1")));
  });
});

describe("moderator privacy", () => {
  it("allows moderators to query cases but not arbitrary conversations", async () => {
    await seed();
    const moderatorDb = environment.authenticatedContext(moderator).firestore();
    await assertSucceeds(getDocs(query(collection(moderatorDb, "moderationCases"), where("status", "==", "OPEN"))));
    await assertFails(getDoc(doc(moderatorDb, "conversations", "conversation-1", "messages", "message-1")));
    await assertFails(getDoc(doc(moderatorDb, "claims", `item-1_${claimant}`)));
  });

  it("denies unrelated users access to claims, cases, roles, tokens, and deletion jobs", async () => {
    await seed();
    const strangerDb = environment.authenticatedContext(stranger).firestore();
    await assertFails(getDoc(doc(strangerDb, "claims", `item-1_${claimant}`)));
    await assertFails(getDoc(doc(strangerDb, "moderationCases", "case-1")));
    await assertFails(getDoc(doc(strangerDb, "roles", moderator)));
    await assertFails(getDoc(doc(strangerDb, "deviceTokens", "owner-token")));
    await assertFails(getDoc(doc(strangerDb, "deletionJobs", owner)));
    await assertSucceeds(getDoc(doc(environment.authenticatedContext(owner).firestore(), "deletionJobs", owner)));
  });
});
