import { describe, expect, it } from "vitest";
import { IMAGE_MAX_DIMENSION } from "@cbu-find/contracts";
import { fitWithin } from "../app/api-client";

describe("fitWithin", () => {
  it("leaves images that already fit unchanged", () => {
    expect(fitWithin(800, 600)).toEqual({ width: 800, height: 600 });
    expect(fitWithin(IMAGE_MAX_DIMENSION, 900)).toEqual({ width: IMAGE_MAX_DIMENSION, height: 900 });
  });

  it("scales the longest edge down and preserves aspect ratio", () => {
    expect(fitWithin(3200, 1600)).toEqual({ width: 1600, height: 800 });
    expect(fitWithin(1600, 3200)).toEqual({ width: 800, height: 1600 });
  });

  it("never enlarges a small or zero-sized image", () => {
    expect(fitWithin(64, 32)).toEqual({ width: 64, height: 32 });
    expect(fitWithin(0, 0)).toEqual({ width: 0, height: 0 });
  });
});
