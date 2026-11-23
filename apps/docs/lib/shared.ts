export const appName = "Stashling";
export const docsRoute = "/docs";

// fill this with your actual GitHub info, for example:
export const gitConfig = {
  user: "Venipa",
  repo: "stashling",
  branch: "master",
};

/** The share image for every page (rendered by scripts/og/render.sh), relative to metadataBase. */
export const ogImage = "og.png";

/** Sub-path on GitHub Pages (e.g. /stashling); empty locally. */
export const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? "";
/** Public files and static routes outside next/link need the base path by hand. */
export const asset = (path: string) => `${basePath}${path}`;

export const repoUrl = `https://github.com/${gitConfig.user}/${gitConfig.repo}`;
export const releasesUrl = `${repoUrl}/releases/latest`;

/** Public site URL incl. the Pages sub-path (set by the Pages workflow); local builds fall back to the dev server. */
export const siteUrl = (
  process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000"
).replace(/\/$/, "");
/** Absolute URL of a site path, in the trailing-slash form the static export serves. */
export const absolute = (path: string) =>
  `${siteUrl}${path === "/" ? "/" : `${path.replace(/\/$/, "")}/`}`;

export const siteTitle = "Stashling – StashDB & Whisparr app for Android";
// Kept under ~160 characters so search results show it whole.
export const siteDescription =
  "Free, open-source Android app for StashDB (stash-box) and Whisparr v3: browse scenes and performers, bookmark on your phone, send to Whisparr. 18+.";
export const siteKeywords = [
  "StashDB app",
  "StashDB Android",
  "stash-box client",
  "Whisparr app",
  "Whisparr Android",
  "Whisparr v3 client",
  "adult media manager",
  "self-hosted",
  "homelab",
  "open source",
  "APK",
];
