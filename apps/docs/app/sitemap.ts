import type { MetadataRoute } from "next";
import { absolute } from "@/lib/shared";
import { source } from "@/lib/source";

export const dynamic = "force-static";

export default function sitemap(): MetadataRoute.Sitemap {
  return [
    { url: absolute("/"), changeFrequency: "weekly", priority: 1 },
    ...source.getPages().map((page) => ({
      url: absolute(page.url),
      changeFrequency: "monthly" as const,
      priority: 0.7,
    })),
  ];
}
