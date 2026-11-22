"use client";
import { useEffect, useRef, useState } from "react";
import { Chip, Phone } from "./phone";

const steps = [
  {
    screen: "browse",
    title: "Browse all of StashDB",
    body: "Scenes, performers, studios and tags from any stash-box instance, with filters and search history.",
  },
  {
    screen: "performer",
    title: "A page for every performer",
    body: "Details and every scene, favourite and bookmark in one tap. Studios and tags get the same pages.",
  },
  {
    screen: "scene-2",
    title: "Send scenes to Whisparr",
    body: "Pick a preset, add and search, and follow the download without leaving the app.",
  },
  {
    screen: "home",
    title: "Private by default",
    body: "Blur every image, lock the app, hide it from recents. Bookmarks stay on the phone. No Google services, no tracking.",
  },
] as const;

const privacy = steps.length - 1;

function PrivacyChips() {
  return (
    <>
      <Chip className="-left-6 top-[18%]">Blur images: on</Chip>
      <Chip className="-right-6 top-[58%]">Bookmarks stay on your phone</Chip>
    </>
  );
}

/** The phone stays pinned while the steps scroll and crossfades to the active one. Below lg each step shows its own phone. */
export function Features() {
  const [active, setActive] = useState(0);
  const refs = useRef<(HTMLDivElement | null)[]>([]);
  useEffect(() => {
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries)
          if (e.isIntersecting)
            setActive(Number((e.target as HTMLElement).dataset.step));
      },
      { rootMargin: "-45% 0px -45% 0px" },
    );
    for (const el of refs.current) if (el) io.observe(el);
    return () => io.disconnect();
  }, []);

  return (
    <section
      id="features"
      className="mx-auto grid max-w-6xl scroll-mt-16 gap-12 px-6 py-16 lg:grid-cols-[1fr_auto] lg:gap-20"
    >
      <div>
        {steps.map((s, i) => (
          <div
            key={s.title}
            data-step={i}
            ref={(el) => {
              refs.current[i] = el;
            }}
            className="flex flex-col gap-8 py-10 lg:min-h-[70vh] lg:justify-center"
          >
            <div
              className={`rounded-3xl p-2 transition-colors duration-500 lg:p-8 ${active === i ? "lg:bg-fd-card" : ""}`}
            >
              <h2
                className={`text-2xl font-bold tracking-tight transition-colors md:text-3xl ${active === i ? "text-fd-foreground" : "lg:text-fd-muted-foreground"}`}
              >
                {s.title}
              </h2>
              <p className="mt-3 max-w-md text-fd-muted-foreground">{s.body}</p>
            </div>
            <Phone
              src={s.screen}
              alt={`Stashling: ${s.title}`}
              className="mx-auto w-64 lg:hidden"
            >
              {i === privacy && <PrivacyChips />}
            </Phone>
          </div>
        ))}
      </div>
      <div className="hidden lg:block">
        <div className="sticky top-[12vh]">
          <div className="relative w-[300px]">
            {steps.map((s, i) => (
              <Phone
                key={s.screen}
                src={s.screen}
                alt={i === active ? `Stashling: ${s.title}` : ""}
                className={`w-full transition-opacity duration-500 ${i ? "absolute! inset-0" : ""} ${i === active ? "opacity-100" : "opacity-0"}`}
              />
            ))}
            {active === privacy && <PrivacyChips />}
          </div>
          <p className="mt-4 text-center text-xs text-fd-muted-foreground">
            Screenshots taken with Blur images on.
          </p>
        </div>
      </div>
    </section>
  );
}
