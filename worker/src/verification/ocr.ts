import { googleAccessToken } from "../google";

export interface OcrExtraction {
  text: string;
  provider: string;
  confidence: number;
}

export interface OcrProvider {
  readonly name: string;
  extract(bytes: Uint8Array<ArrayBuffer>, contentType: string): Promise<OcrExtraction>;
}

/**
 * Used until a real OCR provider is configured. It returns no text rather than
 * inventing fields, so the request is routed to a human moderator instead of
 * being silently approved. OCR output is never treated as proof of membership.
 */
const disabledProvider: OcrProvider = {
  name: "not-configured",
  async extract() {
    return { text: "", provider: "not-configured", confidence: 0 };
  },
};

/** Google Cloud Vision DOCUMENT_TEXT_DETECTION. Requires OCR_PROVIDER=vision. */
function visionProvider(env: Env): OcrProvider {
  return {
  name: "google-vision",
  async extract(bytes, contentType) {
    const token = await googleAccessToken(env);
    const response = await fetch("https://vision.googleapis.com/v1/images:annotate", {
      method: "POST",
      headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
      body: JSON.stringify({
        requests: [{
          image: { content: base64(bytes) },
          features: [{ type: "DOCUMENT_TEXT_DETECTION" }],
          imageContext: { languageHints: ["en"] },
        }],
      }),
    });
    if (!response.ok) {
      console.error(JSON.stringify({ event: "ocr.failed", provider: "google-vision", status: response.status, contentType }));
      return { text: "", provider: "google-vision", confidence: 0 };
    }
    const payload = await response.json<{ responses?: Array<{ fullTextAnnotation?: { text?: string }; textAnnotations?: Array<{ description?: string }>; error?: { message?: string } }> }>();
    const first = payload.responses?.[0];
    if (first?.error) {
      console.error(JSON.stringify({ event: "ocr.provider_error", provider: "google-vision", message: first.error.message }));
      return { text: "", provider: "google-vision", confidence: 0 };
    }
    const text = first?.fullTextAnnotation?.text ?? first?.textAnnotations?.[0]?.description ?? "";
    return { text, provider: "google-vision", confidence: text.trim() ? 0.9 : 0 };
  },
  };
}

export function ocrProviderFor(env: Env): OcrProvider {
  // OCR_PROVIDER is a plain string binding; wrangler narrows it to the local default.
  return String(env.OCR_PROVIDER) === "vision" ? visionProvider(env) : disabledProvider;
}

function base64(bytes: Uint8Array<ArrayBuffer>): string {
  let binary = "";
  const chunk = 0x8000;
  for (let index = 0; index < bytes.length; index += chunk) binary += String.fromCharCode(...bytes.subarray(index, index + chunk));
  return btoa(binary);
}
