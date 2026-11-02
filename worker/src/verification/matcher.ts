import type { RecordMatchOutcome, VerificationFields } from "@cbu-find/contracts";

export interface MatchResult {
  outcome: RecordMatchOutcome;
  confidence: number;
  provider: string;
}

export interface RecordMatcher {
  readonly name: string;
  match(fields: VerificationFields): Promise<MatchResult>;
}

/**
 * Default until Copperbelt University authorises record access. It reports
 * UNAVAILABLE, which routes every request to human review. This is not a stub
 * that pretends to verify: no membership decision can come from it.
 */
const unavailableMatcher: RecordMatcher = {
  name: "not-configured",
  async match() {
    return { outcome: "UNAVAILABLE", confidence: 0, provider: "not-configured" };
  },
};

/**
 * Adapter for an authorised CBU record API. Expected response:
 *   { outcome: "MATCHED" | "NO_MATCH" | "AMBIGUOUS", confidence: 0..1 }
 * Any transport or schema failure is reported as UNAVAILABLE, never NO_MATCH,
 * so a record-system outage can never reject a student.
 */
function httpMatcher(url: string, secret: string): RecordMatcher {
  return {
    name: "cbu-record-api",
    async match(fields) {
      let response: Response;
      try {
        response = await fetch(url, {
          method: "POST",
          headers: { "content-type": "application/json", ...(secret ? { authorization: `Bearer ${secret}` } : {}) },
          body: JSON.stringify({ studentNumber: fields.studentNumber, fullName: fields.fullName, programme: fields.programme }),
          signal: AbortSignal.timeout(8_000),
        });
      } catch {
        return { outcome: "UNAVAILABLE", confidence: 0, provider: "cbu-record-api" };
      }
      if (!response.ok) return { outcome: "UNAVAILABLE", confidence: 0, provider: "cbu-record-api" };
      const payload = await response.json<{ outcome?: RecordMatchOutcome; confidence?: number }>().catch(() => null);
      const outcome = payload?.outcome;
      if (outcome !== "MATCHED" && outcome !== "NO_MATCH" && outcome !== "AMBIGUOUS") {
        return { outcome: "UNAVAILABLE", confidence: 0, provider: "cbu-record-api" };
      }
      const confidence = typeof payload?.confidence === "number" && payload.confidence >= 0 && payload.confidence <= 1 ? payload.confidence : outcome === "MATCHED" ? 0.95 : 0;
      return { outcome, confidence, provider: "cbu-record-api" };
    },
  };
}

export function matcherFor(env: Env): RecordMatcher {
  const url = env.CBU_RECORD_API_URL?.trim();
  if (!url) return unavailableMatcher;
  return httpMatcher(url, env.CBU_RECORD_API_SECRET ?? "");
}

/** Auto-approval requires a configured record source, a strong match, and readable fields. */
export function canAutoApprove(result: MatchResult, studentNumberConfidence: number): boolean {
  return result.provider !== "not-configured"
    && result.outcome === "MATCHED"
    && result.confidence >= 0.9
    && studentNumberConfidence >= 0.8;
}
