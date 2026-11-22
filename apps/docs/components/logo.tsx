/** The launcher icon (ic_launcher_foreground.xml): coloured like the app's Material You icon, from the theme tokens. */
export function Logo({ className = "size-6" }: { className?: string }) {
  return (
    <svg viewBox="0 0 256 256" className={className} aria-hidden="true">
      <rect width="256" height="256" rx="72" fill="var(--pc)" />
      <g
        transform="translate(128 128) scale(.82) translate(-128 -128)"
        fill="var(--onpc)"
      >
        <circle cx="86" cy="82" r="24" />
        <circle cx="170" cy="82" r="24" />
        <circle cx="128" cy="130" r="62" />
        <circle
          cx="106"
          cy="118"
          r="9"
          fill="var(--color-fd-primary-foreground)"
        />
        <circle
          cx="150"
          cy="118"
          r="9"
          fill="var(--color-fd-primary-foreground)"
        />
        <rect
          x="104"
          y="170"
          width="56"
          height="44"
          rx="10"
          fill="#F5BF4E"
          transform="rotate(-10 132 192)"
        />
      </g>
    </svg>
  );
}
