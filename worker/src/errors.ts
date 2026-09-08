import type { ApiResponse, ErrorCode } from "@cbu-find/contracts";
import { ZodError } from "zod";

export class AppError extends Error {
  constructor(
    readonly code: ErrorCode,
    message: string,
    readonly status = 400,
    readonly retryable = false,
    readonly fieldErrors?: Record<string, string>,
  ) {
    super(message);
    this.name = "AppError";
  }
}

export function errorResponse(error: unknown, referenceId: string): Response {
  const appError = normalizeError(error);
  const body: ApiResponse<never> = {
    ok: false,
    error: {
      code: appError.code,
      message: appError.message,
      retryable: appError.retryable,
      referenceId,
      ...(appError.fieldErrors ? { fieldErrors: appError.fieldErrors } : {}),
    },
  };
  return Response.json(body, { status: appError.status });
}

export function normalizeError(error: unknown): AppError {
  if (error instanceof AppError) return error;
  if (error instanceof ZodError) {
    const fieldErrors = Object.fromEntries(error.issues.map((issue) => [issue.path.join(".") || "request", issue.message]));
    return new AppError("VALIDATION_FAILED", "Some information in this request is missing or invalid. Review the details and try again.", 400, false, fieldErrors);
  }
  if (error instanceof Error && /quota|resource_exhausted|daily limit/i.test(error.message)) {
    return new AppError("CAPACITY_EXCEEDED", "CBU Find has reached today’s free-service capacity. Please try again later.", 503, true);
  }
  return new AppError("INTERNAL_ERROR", "Something went wrong while completing that request. Please try again.", 500, true);
}

export function assertFound<T>(value: T | null | undefined, message: string): T {
  if (value == null) throw new AppError("NOT_FOUND", message, 404);
  return value;
}
