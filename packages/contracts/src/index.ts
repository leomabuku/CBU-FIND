export const ITEM_STATUSES = ["ACTIVE", "MATCHED", "RESOLVED", "REMOVED"] as const;
export const CLAIM_STATUSES = ["PENDING", "ACCEPTED", "REJECTED", "CANCELLED"] as const;
export const USER_STATUSES = ["ACTIVE", "SUSPENDED", "DELETING", "DELETED"] as const;
export const MODERATION_CASE_STATUSES = ["OPEN", "DISMISSED", "ACTIONED"] as const;
export const USER_ROLES = ["USER", "MODERATOR", "ADMIN"] as const;

export type ItemStatus = typeof ITEM_STATUSES[number];
export type ClaimStatus = typeof CLAIM_STATUSES[number];
export type UserStatus = typeof USER_STATUSES[number];
export type ModerationCaseStatus = typeof MODERATION_CASE_STATUSES[number];
export type UserRole = typeof USER_ROLES[number];

export type ErrorCode =
  | "AUTH_REQUIRED" | "AUTH_EXPIRED" | "APP_CHECK_REQUIRED" | "PERMISSION_DENIED"
  | "ACCOUNT_SUSPENDED" | "ACCOUNT_DELETING" | "NOT_FOUND" | "ALREADY_EXISTS"
  | "VALIDATION_FAILED" | "BLOCKED" | "CONVERSATION_LOCKED" | "CLAIM_CONFLICT"
  | "RATE_LIMITED" | "CAPACITY_EXCEEDED" | "DEPENDENCY_UNAVAILABLE"
  | "CONFIGURATION_REQUIRED" | "INTERNAL_ERROR";

export interface ApiErrorBody {
  code: ErrorCode;
  message: string;
  retryable: boolean;
  referenceId: string;
  fieldErrors?: Record<string, string>;
}

export type ApiResponse<T> =
  | { ok: true; data: T; error?: never }
  | { ok: false; data?: never; error: ApiErrorBody };

export interface MediaAsset {
  secureUrl: string;
  publicId?: string;
  resourceType?: "image" | "video" | "raw" | "auto";
  format?: string;
  bytes?: number;
  width?: number;
  height?: number;
  originalName?: string;
}

export interface PrivateProfile {
  id: string;
  name: string;
  email: string;
  studentId: string;
  programme: string;
  yearOfStudy: string;
  phone: string;
  photoUrl: string;
  photoAsset?: MediaAsset;
  status: UserStatus;
  createdAt: number;
  updatedAt: number;
}

export interface PublicProfile {
  id: string;
  name: string;
  programme: string;
  photoUrl: string;
  status: "ACTIVE" | "SUSPENDED" | "DELETED";
  updatedAt: number;
}

export interface ItemRecord {
  id: string;
  type: "LOST" | "FOUND";
  title: string;
  description: string;
  category: string;
  location: string;
  date: number;
  status: ItemStatus;
  userId: string;
  media: MediaAsset[];
  imageUrls?: string[];
  imageUri?: string | null;
  matchedClaimId?: string;
  resolvedAt?: number;
  updatedAt: number;
}

export interface ClaimRecord {
  id: string;
  itemId: string;
  itemOwnerId: string;
  claimantId: string;
  kind: "FOUND_IT" | "THIS_IS_MINE";
  note: string;
  status: ClaimStatus;
  conversationId?: string;
  createdAt: number;
  updatedAt: number;
}

export interface MessageRecord {
  id: string;
  senderId: string;
  text: string;
  mediaUrl: string;
  mediaPublicId?: string;
  mediaResourceType?: string;
  mediaType: string;
  mediaName: string;
  mediaSizeBytes: number;
  createdAt: number;
  editedAt?: number;
  deleted?: boolean;
  deletedAt?: number;
}

export interface NotificationJob {
  kind: "CLAIM_CREATED" | "CLAIM_DECIDED" | "MESSAGE" | "REPORT_STATUS" | "MODERATION";
  targetUids: string[];
  title: string;
  body: string;
  data: Record<string, string>;
  hideSensitiveBody?: boolean;
}

export interface DeletionJob {
  uid: string;
  requestedAt: number;
  referenceId: string;
  attempt: number;
}

export const DEFAULT_NOTIFICATION_PREFERENCES = {
  claims: true,
  claimDecisions: true,
  messages: true,
  reportUpdates: true,
  moderation: true,
  showMessagePreview: false,
} as const;

export function deterministicClaimId(itemId: string, uid: string): string {
  return `${itemId}_${uid}`;
}

export function deterministicConversationId(itemId: string, firstUid: string, secondUid: string): string {
  return `${itemId}_${[firstUid, secondUid].sort().join("_")}`;
}
