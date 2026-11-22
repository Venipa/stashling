import type { BaseLayoutProps } from "fumadocs-ui/layouts/shared";
import { Logo } from "@/components/logo";
import { appName, releasesUrl, repoUrl } from "./shared";

export function baseOptions(): BaseLayoutProps {
  return {
    nav: {
      title: (
        <>
          <Logo />
          {appName}
        </>
      ),
    },
    githubUrl: repoUrl,
    themeSwitch: { enabled: false },
    links: [
      {
        type: "button",
        text: "Download APK",
        url: releasesUrl,
        external: true,
      },
    ],
  };
}
