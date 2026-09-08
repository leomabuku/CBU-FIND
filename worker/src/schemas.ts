import { z } from "zod";

const trimmed = (max: number) => z.string().trim().max(max);
export const mediaAssetSchema = z.object({
  secureUrl: z.string().url().max(2000),
  publicId: trimmed(500).optional(),
  resourceType: z.enum(["image", "video", "raw", "auto"]).optional(),
  format: trimmed(32).optional(),
  bytes: z.number().int().nonnegative().max(20 * 1024 * 1024).optional(),
  width: z.number().int().positive().optional(),
  height: z.number().int().positive().optional(),
  originalName: trimmed(255).optional(),
});

export const profileSchema = z.object({
  name: trimmed(100).min(2),
  studentId: trimmed(60).default(""),
  programme: trimmed(120).default(""),
  yearOfStudy: trimmed(30).default(""),
  phone: trimmed(30).default(""),
  photoUrl: z.union([z.literal(""), z.string().url().max(2000)]).default(""),
  photoAsset: mediaAssetSchema.optional(),
});

export const reportSchema = z.object({
  type: z.enum(["LOST", "FOUND"]),
  title: trimmed(120).min(3),
  description: trimmed(4000).min(10),
  category: trimmed(100).min(2),
  location: trimmed(160).min(2),
  date: z.number().int().positive(),
  contactInfo: trimmed(300).default(""),
  media: z.array(mediaAssetSchema).max(6).default([]),
});

export const claimSchema = z.object({
  itemId: trimmed(500).min(1),
  kind: z.enum(["FOUND_IT", "THIS_IS_MINE"]),
  note: trimmed(1000).default(""),
});

export const messageSchema = z.object({
  text: trimmed(4000).default(""),
  // Older web builds serialized an empty attachment as null. Treat that as
  // omitted so ordinary text replies remain compatible across deployments.
  attachment: mediaAssetSchema.extend({ type: z.enum(["IMAGE", "VIDEO", "FILE"]), name: trimmed(255).default("") }).nullish().transform((value) => value ?? undefined),
}).refine((value) => value.text.length > 0 || value.attachment, { message: "Write a message or add an attachment." });

export const messageEditSchema = z.object({
  text: trimmed(4000),
});

export const reportAbuseSchema = z.object({
  targetType: z.enum(["REPORT", "MESSAGE", "USER"]),
  targetId: trimmed(500).min(1),
  conversationId: trimmed(500).optional(),
  reason: z.enum(["SCAM", "HARASSMENT", "INAPPROPRIATE", "SPAM", "DANGEROUS", "OTHER"]),
  details: trimmed(1500).default(""),
});

export const notificationPreferencesSchema = z.object({
  claims: z.boolean(), claimDecisions: z.boolean(), messages: z.boolean(), reportUpdates: z.boolean(), moderation: z.boolean(), showMessagePreview: z.boolean(),
});
