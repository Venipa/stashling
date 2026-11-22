"use client";
import { BookOpen, House, LayoutGrid } from "lucide-react";
import Link from "next/link";
import { type ReactNode, useEffect, useState } from "react";
import { Logo } from "@/components/logo";
import { releasesUrl, repoUrl } from "@/lib/shared";
import { GithubIcon } from "./icons";

/** Desktop: full-width bar, transparent at the top, glass with a hairline once scrolled. */
export function SiteNav({ stars }: { stars: number | null }) {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const on = () => setScrolled(window.scrollY > 16);
    on();
    window.addEventListener("scroll", on, { passive: true });
    return () => window.removeEventListener("scroll", on);
  }, []);

  return (
    <header
      className={`fixed inset-x-0 top-0 z-40 hidden transition-[background,box-shadow] duration-300 md:block ${scrolled ? "glass" : ""}`}
    >
      <nav className="mx-auto flex h-16 max-w-6xl items-center gap-6 px-6 text-sm text-fd-muted-foreground">
        <Link
          href="/"
          className="mr-auto flex items-center gap-2.5 font-semibold text-fd-foreground"
        >
          <Logo className="size-7" />
          Stashling
        </Link>
        <a href="#features" className="hover:text-fd-foreground">
          Features
        </a>
        <Link href="/docs" className="hover:text-fd-foreground">
          Docs
        </Link>
        <a
          href={repoUrl}
          className="flex items-center gap-1.5 hover:text-fd-foreground"
        >
          <GithubIcon />
          GitHub
          {stars !== null && (
            <span className="rounded-full bg-fd-secondary px-2 py-0.5 text-xs tabular-nums">
              ★ {stars}
            </span>
          )}
        </a>
        <a
          href={releasesUrl}
          className="rounded-full bg-fd-primary px-4 py-2 font-semibold text-fd-primary-foreground"
        >
          Download APK
        </a>
      </nav>
    </header>
  );
}

function Tab({
  href,
  label,
  icon,
  active,
}: {
  href: string;
  label: string;
  icon: ReactNode;
  active?: boolean;
}) {
  return (
    <a
      href={href}
      aria-current={active ? "page" : undefined}
      // Dock 28 outer, 6 pad -> tab 22 (concentric).
      className={`flex flex-1 flex-col items-center gap-0.5 rounded-[22px] py-2 text-[11px] font-medium transition-colors ${active ? "bg-(--pc) text-(--onpc)" : "text-fd-muted-foreground"}`}
    >
      {icon}
      {label}
    </a>
  );
}

/** Phones: the app's bottom glass dock, active tab follows the scroll; logo as a small glass island on top. */
export function MobileDock() {
  const [onFeatures, setOnFeatures] = useState(false);
  useEffect(() => {
    const el = document.getElementById("features");
    if (!el) return;
    const io = new IntersectionObserver(
      ([e]) => setOnFeatures(e.isIntersecting),
      { rootMargin: "-50% 0px -50% 0px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, []);

  return (
    <div className="md:hidden">
      <Link
        href="/"
        className="glass fixed left-3 top-3 z-40 flex h-11 items-center gap-2 rounded-full pl-2 pr-4 text-sm font-semibold"
      >
        <Logo className="size-7" />
        Stashling
      </Link>
      <nav className="glass fixed inset-x-3 bottom-3 z-40 flex gap-1 rounded-[28px] p-1.5">
        <Tab
          href="#top"
          label="Home"
          icon={<House className="size-5" />}
          active={!onFeatures}
        />
        <Tab
          href="#features"
          label="Features"
          icon={<LayoutGrid className="size-5" />}
          active={onFeatures}
        />
        <Tab href="/docs" label="Docs" icon={<BookOpen className="size-5" />} />
        <Tab
          href={repoUrl}
          label="GitHub"
          icon={<GithubIcon className="size-5" />}
        />
      </nav>
    </div>
  );
}
