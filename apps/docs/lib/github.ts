import { gitConfig } from "./shared";

export interface RepoStats {
  stars: number | null;
  /** Latest release tag, e.g. v1.2.0. */
  version: string | null;
}

async function get<T>(path: string): Promise<T | null> {
  try {
    const res = await fetch(
      `https://api.github.com/repos/${gitConfig.user}/${gitConfig.repo}${path}`,
      {
        headers: {
          Accept: "application/vnd.github+json",
          // CI passes GITHUB_TOKEN for the higher rate limit; anonymous works locally.
          ...(process.env.GITHUB_TOKEN
            ? { Authorization: `Bearer ${process.env.GITHUB_TOKEN}` }
            : {}),
        },
      },
    );
    return res.ok ? ((await res.json()) as T) : null;
  } catch {
    return null;
  }
}

/** Read once at build time; the nightly Pages build refreshes it. A failed call hides the value instead of failing the build. */
export async function getRepoStats(): Promise<RepoStats> {
  const [repo, release] = await Promise.all([
    get<{ stargazers_count: number }>(""),
    get<{ tag_name: string }>("/releases/latest"),
  ]);
  return {
    stars: repo?.stargazers_count ?? null,
    version: release?.tag_name ?? null,
  };
}
