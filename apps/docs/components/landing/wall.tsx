import { asset } from "@/lib/shared";

const screens = [
  "home",
  "browse",
  "performer",
  "scene-2",
  "settings",
  "home",
  "performer",
  "browse",
];

/** Tilted, dimmed wall of captures drifting behind centred text. Decorative only. */
export function Wall() {
  // Static layout: column c starts c screens further along; ids are fixed, so they make stable keys.
  const columns = Array.from({ length: 6 }, (_, c) => ({
    id: `col-${c}`,
    tiles: [...screens.slice(c), ...screens.slice(0, c)]
      .slice(0, 5)
      .map((s, i) => ({ id: `${c}-${i}`, s })),
  }));
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 overflow-hidden"
    >
      <div className="absolute -inset-x-[20%] -inset-y-1/2 grid -rotate-12 grid-cols-3 gap-4 opacity-35 md:grid-cols-6">
        {columns.map((col, c) => (
          <div
            key={col.id}
            className={`flex flex-col gap-4 ${c % 2 ? "drift-down" : "drift-up"} ${c >= 3 ? "max-md:hidden" : ""}`}
          >
            {col.tiles.map((t) => (
              <div
                key={t.id}
                className="rounded-[28px] bg-black p-1 ring-1 ring-white/10"
              >
                {/* biome-ignore lint/performance/noImgElement: static export, images are pre-sized webp; next/image would add nothing */}
                <img
                  src={asset(`/screens/${t.s}.webp`)}
                  alt=""
                  width={600}
                  height={1243}
                  loading="lazy"
                  className="block h-auto w-full rounded-[24px]"
                />
              </div>
            ))}
          </div>
        ))}
      </div>
      <div className="absolute inset-0 bg-[radial-gradient(60%_55%_at_50%_50%,var(--color-fd-background)_35%,transparent)]" />
      <div className="absolute inset-x-0 bottom-0 h-1/3 bg-gradient-to-t from-fd-background" />
    </div>
  );
}
