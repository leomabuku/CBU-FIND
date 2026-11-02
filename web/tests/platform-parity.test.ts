import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { IMAGE_JPEG_QUALITY, IMAGE_MAX_DIMENSION, MAX_MEDIA_BYTES, MAX_REPORT_MEDIA, REPORT_CATEGORIES } from "@cbu-find/contracts";

// CBU Find ships two clients against one backend. Web imports the shared
// constants from @cbu-find/contracts; Kotlin cannot, so these tests read the
// Android sources and fail if either side drifts. Update both together.

const androidRoot = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "app", "src", "main", "java", "com", "campus", "lostandfound");

function androidFile(relative: string): string {
  return readFileSync(join(androidRoot, relative), "utf8").replace(/\r\n/g, "\n");
}

function kotlinConstant(source: string, name: string): number {
  const match = new RegExp(`${name}\\s*=\\s*([0-9\\s*]+)`).exec(source);
  if (!match) throw new Error(`Could not find Kotlin constant ${name}`);
  return match[1].split("*").reduce((total, part) => total * Number(part.trim()), 1);
}

describe("cross-platform parity", () => {
  it("exposes the same report categories on Android and web", () => {
    const itemSource = androidFile("data/model/Item.kt");
    const categoryBlock = /object ItemCategories\s*\{([\s\S]*?)\n\}/.exec(itemSource)?.[1];
    const listBody = categoryBlock && /val all = listOf\(([\s\S]*?)\)/.exec(categoryBlock)?.[1];
    expect(listBody, "ItemCategories.all not found in Item.kt").toBeTruthy();

    const androidCategories = [...listBody!.matchAll(/"([^"]+)"/g)].map((match) => match[1]);
    expect(androidCategories).toEqual([...REPORT_CATEGORIES]);
  });

  it("allows the same number of photos per report", () => {
    expect(kotlinConstant(androidFile("ui/screens/CreateListingScreen.kt"), "MAX_REPORT_PHOTOS")).toBe(MAX_REPORT_MEDIA);
  });

  it("applies the same attachment size ceiling", () => {
    expect(kotlinConstant(androidFile("data/repository/AppRepository.kt"), "CHAT_MEDIA_MAX_BYTES")).toBe(MAX_MEDIA_BYTES);
  });

  it("compresses images with the same dimensions and quality", () => {
    const compressor = androidFile("data/util/ImageCompressor.kt");
    expect(kotlinConstant(compressor, "MAX_DIMENSION")).toBe(IMAGE_MAX_DIMENSION);
    expect(kotlinConstant(compressor, "JPEG_QUALITY")).toBe(IMAGE_JPEG_QUALITY);
  });
});
