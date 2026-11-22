import { createGetUrl } from "fumadocs-core/source";

export const appName = "Stashling";
export const docsRoute = "/docs";
export const docsImageRoute = "/og/docs";

// fill this with your actual GitHub info, for example:
export const gitConfig = {
  user: "Venipa",
  repo: "stashling",
  branch: "master",
};

const getImageUrl = createGetUrl(docsImageRoute);

export function getPageImageUrl(page: { slugs: string[]; locale?: string }) {
  const segments = [...page.slugs, "image.png"];

  return { segments, url: getImageUrl(segments, page.locale) };
}

/** Sub-path on GitHub Pages (e.g. /stashling); empty locally. */
export const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? "";
/** Public files and static routes outside next/link need the base path by hand. */
export const asset = (path: string) => `${basePath}${path}`;

export const repoUrl = `https://github.com/${gitConfig.user}/${gitConfig.repo}`;
export const releasesUrl = `${repoUrl}/releases/latest`;
