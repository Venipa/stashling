"use client";
import { RootProvider } from "fumadocs-ui/provider/next";
import type { ReactNode } from "react";
import SearchDialog from "@/components/search";

/** Dark only, like the app's theme. */
export function Provider({ children }: { children: ReactNode }) {
  return (
    <RootProvider
      search={{ SearchDialog }}
      theme={{ forcedTheme: "dark", defaultTheme: "dark", enableSystem: false }}
    >
      {children}
    </RootProvider>
  );
}
