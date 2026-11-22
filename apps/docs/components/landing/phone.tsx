import type { ReactNode } from "react";
import { asset } from "@/lib/shared";

/** A dev-app capture in a phone frame. Outer radius 36, bezel 6 -> screen radius 30 (concentric). */
export function Phone({
  src,
  alt = "",
  className = "",
  children,
}: {
  src: string;
  alt?: string;
  className?: string;
  children?: ReactNode;
}) {
  return (
    <div
      className={`relative shrink-0 rounded-[36px] bg-black p-1.5 shadow-2xl shadow-black/60 ring-1 ring-white/10 ${className}`}
    >
      {/* biome-ignore lint/performance/noImgElement: static export, images are pre-sized webp; next/image would add nothing */}
      <img
        src={asset(`/screens/${src}.webp`)}
        alt={alt}
        width={600}
        height={1243}
        loading="lazy"
        className="block h-auto w-full rounded-[30px]"
      />
      {children}
    </div>
  );
}

/** Glass callout chip over a phone. */
export function Chip({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={`glass absolute flex items-center gap-2 whitespace-nowrap rounded-full px-3 py-1.5 text-xs font-medium text-white ${className}`}
    >
      <i className="size-2 rounded-full bg-fd-primary" />
      {children}
    </span>
  );
}
