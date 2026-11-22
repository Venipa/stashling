import { createFromSource } from "fumadocs-core/search/server";
import { source } from "@/lib/source";

export const revalidate = false;

// Static export: the index is a file the client downloads (components/search.tsx).
export const { staticGET: GET } = createFromSource(source, {
  language: "english",
});
