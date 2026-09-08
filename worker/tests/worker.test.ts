import { exports } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import { normalizeError, AppError } from "../src/errors";
import { assertOwnedMedia, initialMessageState, messageAttachmentPreview, moderationMessageContext } from "../src/domain";
import { readAppCheckToken, readAuthorizationToken } from "../src/auth";
import { cloudinaryDestroySignatureBase } from "../src/queues";
import { FirestoreRest } from "../src/firestore";
import { messageSchema } from "../src/schemas";

describe("CBU Find Worker", () => {
  it("writes numeric message lifecycle timestamps for Android compatibility", () => {
    expect(initialMessageState()).toEqual({ editedAt: 0, deleted: false, deletedAt: 0 });
  });

  it("accepts legacy null attachments for ordinary text replies", () => {
    expect(messageSchema.parse({ text: "Hello", attachment: null })).toEqual({ text: "Hello", attachment: undefined });
    expect(() => messageSchema.parse({ text: "", attachment: null })).toThrow();
  });

  it("answers health checks in the Workers runtime", async () => {
    const response = await exports.default.fetch("https://example.test/health");
    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({ ok: true, data: { service: "cbu-find-api" } });
  });

  it("returns the stable error envelope for thrown route failures", async () => {
    const response = await exports.default.fetch("https://example.test/v1/diagnostics");
    expect(response.status).toBe(401);
    await expect(response.json()).resolves.toMatchObject({
      ok: false,
      error: { code: "AUTH_REQUIRED", retryable: false, referenceId: expect.any(String) },
    });
  });

  it("keeps actionable error metadata", () => {
    const error = normalizeError(new AppError("BLOCKED", "Action unavailable.", 403, false));
    expect(error).toMatchObject({ code: "BLOCKED", status: 403, retryable: false });
  });

  it("turns quota failures into retryable capacity errors", () => {
    const error = normalizeError(new Error("RESOURCE_EXHAUSTED quota"));
    expect(error).toMatchObject({ code: "CAPACITY_EXCEEDED", status: 503, retryable: true });
  });

  it("accepts only signed-upload assets owned by the acting account", () => {
    expect(() => assertOwnedMedia([{ secureUrl: "https://res.cloudinary.com/cbu-cloud/image/upload/example.jpg", publicId: "cbu_find/reports/user-1/example", resourceType: "image" }], "user-1", "reports", "cbu-cloud")).not.toThrow();
    expect(() => assertOwnedMedia([{ secureUrl: "https://res.cloudinary.com/cbu-cloud/image/upload/example.jpg", publicId: "cbu_find/reports/other-user/example", resourceType: "image" }], "user-1", "reports", "cbu-cloud")).toThrowError(AppError);
  });

  it("parses Firebase authentication and raw App Check headers", () => {
    const request = new Request("https://example.test/v1/diagnostics", { headers: { authorization: "Bearer firebase-id-token", "x-firebase-appcheck": "firebase-app-check-token" } });
    expect(readAuthorizationToken(request)).toBe("firebase-id-token");
    expect(readAppCheckToken(request)).toBe("firebase-app-check-token");
  });

  it("temporarily accepts the legacy Bearer App Check header", () => {
    const request = new Request("https://example.test/v1/diagnostics", { headers: { "x-firebase-appcheck": "Bearer legacy-token" } });
    expect(readAppCheckToken(request)).toBe("legacy-token");
  });

  it("limits moderator message evidence to two messages on either side", () => {
    const messages = Array.from({ length: 9 }, (_, index) => ({ id: `message-${index}`, senderId: `user-${index % 2}`, text: `body-${index}`, createdAt: index }));
    expect(moderationMessageContext(messages, "message-4").map((message) => message.id)).toEqual([
      "message-2", "message-3", "message-4", "message-5", "message-6",
    ]);
  });

  it("keeps attachment previews stable when text is edited away", () => {
    expect(messageAttachmentPreview("IMAGE")).toBe("Sent a photo");
    expect(messageAttachmentPreview("VIDEO")).toBe("Sent a video");
    expect(messageAttachmentPreview("FILE")).toBe("Sent an attachment");
  });

  it("signs every Cloudinary deletion parameter in sorted order", () => {
    expect(cloudinaryDestroySignatureBase("cbu_find/reports/user-1/photo", 1_725_000_000, "secret")).toBe(
      "invalidate=true&public_id=cbu_find/reports/user-1/photo&timestamp=1725000000secret",
    );
  });

  it("uses Firestore resource names rather than endpoint URLs in commit writes", () => {
    const db = new FirestoreRest({ FIREBASE_PROJECT_ID: "cbu-test" } as unknown as Env);
    expect(db.setWrite("users/user-1", { name: "Test" })).toMatchObject({
      update: { name: "projects/cbu-test/databases/(default)/documents/users/user-1" },
    });
    expect(db.deleteWrite("users/user-1")).toEqual({
      delete: "projects/cbu-test/databases/(default)/documents/users/user-1",
    });
  });
});
