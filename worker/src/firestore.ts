import { AppError } from "./errors";
import { googleAccessToken } from "./google";

type FireValue = Record<string, unknown>;
type FireDocument = { name: string; fields?: Record<string, FireValue>; createTime?: string; updateTime?: string };
export type PlainDocument = Record<string, unknown> & { id: string; _updateTime?: string };

export class FirestoreRest {
  private readonly root: string;
  private readonly documentRoot: string;
  constructor(private readonly env: Env) {
    this.documentRoot = `projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents`;
    this.root = `https://firestore.googleapis.com/v1/${this.documentRoot}`;
  }

  async get(path: string, transaction?: string): Promise<PlainDocument | null> {
    const suffix = transaction ? `?transaction=${encodeURIComponent(transaction)}` : "";
    const response = await this.request(`${this.root}/${path}${suffix}`);
    if (response.status === 404) return null;
    return this.decodeResponse(response);
  }

  async create(collection: string, id: string, data: Record<string, unknown>): Promise<PlainDocument> {
    const url = `${this.root}/${collection}?documentId=${encodeURIComponent(id)}`;
    return this.decodeResponse(await this.request(url, { method: "POST", body: JSON.stringify({ fields: encodeMap(data) }) }));
  }

  async patch(path: string, data: Record<string, unknown>, fieldPaths = Object.keys(data)): Promise<PlainDocument> {
    const masks = fieldPaths.map((field) => `updateMask.fieldPaths=${encodeURIComponent(field)}`).join("&");
    return this.decodeResponse(await this.request(`${this.root}/${path}?${masks}`, { method: "PATCH", body: JSON.stringify({ fields: encodeMap(data) }) }));
  }

  async remove(path: string): Promise<void> {
    const response = await this.request(`${this.root}/${path}`, { method: "DELETE" });
    if (!response.ok && response.status !== 404) await this.throwResponse(response);
  }

  async query(collectionId: string, filters: Array<{ field: string; op?: string; value: unknown }>, options: { parent?: string; allDescendants?: boolean; orderBy?: string; descending?: boolean; limit?: number; transaction?: string } = {}): Promise<PlainDocument[]> {
    const structuredQuery: Record<string, unknown> = {
      from: [{ collectionId, allDescendants: options.allDescendants ?? false }],
      ...(filters.length ? { where: filters.length === 1 ? fieldFilter(filters[0]) : { compositeFilter: { op: "AND", filters: filters.map(fieldFilter) } } } : {}),
      ...(options.orderBy ? { orderBy: [{ field: { fieldPath: options.orderBy }, direction: options.descending ? "DESCENDING" : "ASCENDING" }] } : {}),
      ...(options.limit ? { limit: options.limit } : {}),
    };
    const response = await this.request(`${this.root}${options.parent ? `/${options.parent}` : ""}:runQuery`, { method: "POST", body: JSON.stringify({ structuredQuery, ...(options.transaction ? { transaction: options.transaction } : {}) }) });
    if (!response.ok) await this.throwResponse(response);
    const rows = await response.json<Array<{ document?: FireDocument }>>();
    return rows.flatMap((row) => row.document ? [decodeDocument(row.document)] : []);
  }

  async beginTransaction(): Promise<string> {
    const response = await this.request(`${this.root}:beginTransaction`, { method: "POST", body: JSON.stringify({ options: { readWrite: {} } }) });
    const data = await response.json<{ transaction?: string }>();
    if (!response.ok || !data.transaction) throw new AppError("DEPENDENCY_UNAVAILABLE", "The data service could not start this change. Please retry.", 503, true);
    return data.transaction;
  }

  async commit(writes: Array<Record<string, unknown>>, transaction?: string): Promise<void> {
    const response = await this.request(`${this.root}:commit`, { method: "POST", body: JSON.stringify({ writes, ...(transaction ? { transaction } : {}) }) });
    if (!response.ok) await this.throwResponse(response);
  }

  setWrite(path: string, data: Record<string, unknown>, fieldPaths?: string[]): Record<string, unknown> {
    return {
      update: { name: `${this.documentRoot}/${path}`, fields: encodeMap(data) },
      ...(fieldPaths ? { updateMask: { fieldPaths } } : {}),
    };
  }

  deleteWrite(path: string): Record<string, unknown> { return { delete: `${this.documentRoot}/${path}` }; }

  private async request(url: string, init: RequestInit = {}): Promise<Response> {
    const token = await googleAccessToken(this.env);
    try {
      return await fetch(url, { ...init, headers: { authorization: `Bearer ${token}`, "content-type": "application/json", ...init.headers } });
    } catch {
      throw new AppError("DEPENDENCY_UNAVAILABLE", "CBU Find cannot reach its data service. Check your connection and retry.", 503, true);
    }
  }

  private async decodeResponse(response: Response): Promise<PlainDocument> {
    if (!response.ok) await this.throwResponse(response);
    return decodeDocument(await response.json<FireDocument>());
  }

  private async throwResponse(response: Response): Promise<never> {
    const text = await response.text();
    if (response.status === 404) throw new AppError("NOT_FOUND", "That record is no longer available.", 404);
    if (response.status === 409 || response.status === 412) throw new AppError("CLAIM_CONFLICT", "Someone updated this record first. Refresh and try again.", 409, true);
    if (response.status === 429 || /quota|resource_exhausted/i.test(text)) throw new AppError("CAPACITY_EXCEEDED", "CBU Find has reached today’s free-service capacity. Please try again later.", 503, true);
    throw new AppError("DEPENDENCY_UNAVAILABLE", "The data service could not complete that request. Please retry.", 503, true);
  }
}

export function encodeMap(data: Record<string, unknown>): Record<string, FireValue> {
  return Object.fromEntries(Object.entries(data).filter(([, value]) => value !== undefined).map(([key, value]) => [key, encodeValue(value)]));
}

function encodeValue(value: unknown): FireValue {
  if (value === null) return { nullValue: null };
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number") return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(encodeValue) } };
  if (value instanceof Date) return { timestampValue: value.toISOString() };
  if (typeof value === "object") return { mapValue: { fields: encodeMap(value as Record<string, unknown>) } };
  throw new AppError("VALIDATION_FAILED", "The request contains an unsupported value.", 400);
}

function decodeDocument(document: FireDocument): PlainDocument {
  const marker = "/documents/";
  return { ...decodeMap(document.fields ?? {}), id: document.name.split("/").pop() ?? "", _path: document.name.includes(marker) ? document.name.split(marker)[1] : "", _updateTime: document.updateTime };
}

function decodeMap(fields: Record<string, FireValue>): Record<string, unknown> {
  return Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, decodeValue(value)]));
}

function decodeValue(value: FireValue): unknown {
  if ("nullValue" in value) return null;
  if ("stringValue" in value) return value.stringValue;
  if ("booleanValue" in value) return value.booleanValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return value.doubleValue;
  if ("timestampValue" in value) return Date.parse(String(value.timestampValue));
  if ("arrayValue" in value) return ((value.arrayValue as { values?: FireValue[] }).values ?? []).map(decodeValue);
  if ("mapValue" in value) return decodeMap((value.mapValue as { fields?: Record<string, FireValue> }).fields ?? {});
  return undefined;
}

function fieldFilter(filter: { field: string; op?: string; value: unknown }): Record<string, unknown> {
  return { fieldFilter: { field: { fieldPath: filter.field }, op: filter.op ?? "EQUAL", value: encodeValue(filter.value) } };
}
