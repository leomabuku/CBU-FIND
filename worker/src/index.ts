import { Hono } from "hono";
import { AppError, errorResponse, normalizeError } from "./errors";
import { verifyRequest } from "./auth";
import { FirestoreRest } from "./firestore";
import { registerDomainRoutes, type AppBindings } from "./domain";
import { processDeletion, processNotification } from "./queues";
import type { DeletionJob, NotificationJob } from "@cbu-find/contracts";

const app = new Hono<AppBindings>();

app.onError((error, c) => {
  const normalized = normalizeError(error);
  const referenceId = c.get("referenceId") || crypto.randomUUID();
  const startedAt = c.get("startedAt");
  console.error(JSON.stringify({
    event: "request.failed",
    referenceId,
    method: c.req.method,
    route: logRoute(c.req.path),
    code: normalized.code,
    status: normalized.status,
    retryable: normalized.retryable,
    durationMs: typeof startedAt === "number" ? Date.now() - startedAt : undefined,
    environment: c.env.ENVIRONMENT,
  }));
  return errorResponse(error, referenceId);
});

function logRoute(path: string): string {
  const parts = path.split("/");
  const identifierParents = new Set(["reports", "claims", "conversations", "blocks", "cases", "roles"]);
  return parts.map((part, index) => index > 0 && identifierParents.has(parts[index - 1]) ? ":id" : part).join("/");
}

app.use("*", async (c, next) => {
  const referenceId = crypto.randomUUID();
  c.set("referenceId", referenceId);
  const origin = c.req.header("origin") ?? "";
  const allowed = c.env.CORS_ORIGINS.split(",").map((value) => value.trim());
  if (origin && allowed.includes(origin)) {
    c.header("access-control-allow-origin", origin);
    c.header("vary", "Origin");
    c.header("access-control-allow-headers", "Authorization, Content-Type, X-Firebase-AppCheck");
    c.header("access-control-allow-methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
  }
  c.header("x-reference-id", referenceId);
  c.header("cache-control", "no-store");
  if (c.req.method === "OPTIONS") return c.body(null, 204);
  const startedAt = Date.now();
  c.set("startedAt", startedAt);
  await next();
  if (c.res.status < 400) {
    console.log(JSON.stringify({ event: "request.complete", referenceId, method: c.req.method, route: logRoute(c.req.path), status: c.res.status, durationMs: Date.now() - startedAt, environment: c.env.ENVIRONMENT }));
  }
});

app.get("/health", (c) => c.json({ ok: true, data: { service: "cbu-find-api", environment: c.env.ENVIRONMENT, time: new Date().toISOString() } }));

app.use("/v1/*", async (c, next) => {
  const auth = await verifyRequest(c.req.raw, c.env);
  const limited = await c.env.GENERAL_RATE_LIMITER.limit({ key: `${auth.uid}:${c.req.path}` });
  if (!limited.success) throw new AppError("RATE_LIMITED", "You are doing that too quickly. Wait a moment and try again.", 429, true);
  const db = new FirestoreRest(c.env);
  c.set("uid", auth.uid); c.set("email", auth.email); c.set("db", db);
  if (c.req.path !== "/v1/profile/bootstrap") {
    const user = await db.get(`users/${auth.uid}`);
    if (!user) throw new AppError("AUTH_REQUIRED", "Your profile is not ready. Sign out, sign in, and retry.", 401);
    if (user.status === "SUSPENDED" && c.req.path !== "/v1/account-deletion") throw new AppError("ACCOUNT_SUSPENDED", "This account is suspended. Contact a CBU Find administrator if you think this is a mistake.", 403);
    if (user.status === "DELETING" && c.req.path !== "/v1/account-deletion") throw new AppError("ACCOUNT_DELETING", "Account deletion is in progress. New changes are disabled.", 409);
  }
  await next();
});

registerDomainRoutes(app);
app.notFound((c) => errorResponse(new AppError("NOT_FOUND", "That API endpoint does not exist.", 404), c.get("referenceId") || crypto.randomUUID()));

const worker: ExportedHandler<Env> = {
  fetch: app.fetch,
  async queue(batch, env): Promise<void> {
    for (const message of batch.messages) {
      try {
        const body = message.body as NotificationJob | DeletionJob;
        if ("targetUids" in body) await processNotification(env, body);
        else await processDeletion(env, body);
        message.ack();
      } catch (error) {
        console.error(JSON.stringify({ event: "queue.failed", messageId: message.id, queue: batch.queue, code: error instanceof Error ? error.name : "UNKNOWN" }));
        message.retry();
      }
    }
  },
};

export default worker;
export { app };
