import Link from "next/link";
import { Features } from "@/components/landing/features";
import { MobileDock, SiteNav } from "@/components/landing/nav";
import { Wall } from "@/components/landing/wall";
import { Logo } from "@/components/logo";
import { getRepoStats } from "@/lib/github";
import { releasesUrl, repoUrl } from "@/lib/shared";

function Actions() {
  return (
    <div className="flex flex-wrap justify-center gap-3">
      <a
        href={releasesUrl}
        className="rounded-full bg-fd-primary px-6 py-3 font-semibold text-fd-primary-foreground"
      >
        Download APK
      </a>
      <Link
        href="/docs"
        className="rounded-full px-6 py-3 font-semibold ring-1 ring-fd-border ring-inset hover:bg-fd-accent"
      >
        Read the docs
      </Link>
    </div>
  );
}

export default async function HomePage() {
  const { stars, version } = await getRepoStats();
  const meta = [
    version,
    "Android 12+",
    "GPL-3.0",
    stars !== null ? `★ ${stars} on GitHub` : null,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <>
      <SiteNav stars={stars} />
      <main id="top" className="flex-1">
        {/* Hero: screen wall behind centred text. */}
        <section className="relative flex min-h-[92svh] items-center justify-center overflow-hidden px-6 py-32">
          <Wall />
          <div className="relative flex max-w-2xl flex-col items-center gap-6 text-center">
            <h1 className="text-5xl font-bold leading-[1.02] tracking-tight md:text-7xl">
              Your StashDB,
              <br />
              <span className="text-fd-primary">in your pocket.</span>
            </h1>
            <p className="max-w-lg text-lg text-fd-muted-foreground">
              Browse scenes and performers, keep bookmarks on the phone, send it
              all to Whisparr. No Google services, no tracking.
            </p>
            <Actions />
            <p className="text-sm text-fd-muted-foreground">{meta}</p>
          </div>
        </section>

        <Features />

        {/* Closing band. */}
        <section className="relative flex items-center justify-center overflow-hidden px-6 py-28">
          <Wall />
          <div className="relative flex flex-col items-center gap-5 text-center">
            <Logo className="size-20" />
            <h2 className="text-4xl font-bold tracking-tight">
              Get <span className="text-fd-primary">Stashling</span>
            </h2>
            <p className="text-fd-muted-foreground">
              Free and open source, for Android 12 and up.
            </p>
            <Actions />
          </div>
        </section>
      </main>
      <footer className="border-t border-fd-border px-6 pt-6 pb-28 text-sm text-fd-muted-foreground md:pb-6">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3">
          <span>GPL-3.0 · No Google Play services, no tracking</span>
          <span className="flex gap-4">
            <Link href="/docs" className="hover:text-fd-foreground">
              Docs
            </Link>
            <a href={repoUrl} className="hover:text-fd-foreground">
              GitHub
            </a>
            <a href={releasesUrl} className="hover:text-fd-foreground">
              Releases
            </a>
          </span>
        </div>
      </footer>
      <MobileDock />
    </>
  );
}
