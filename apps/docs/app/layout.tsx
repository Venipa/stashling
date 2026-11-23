import type { Metadata } from "next";
import { Inter } from "next/font/google";
import { Provider } from "@/components/provider";
import {
  appName,
  gitConfig,
  ogImage,
  siteDescription,
  siteKeywords,
  siteTitle,
  siteUrl,
} from "@/lib/shared";
import "./global.css";

const inter = Inter({
  subsets: ["latin"],
});

export const metadata: Metadata = {
  // The Pages workflow sets the public URL so canonical links and OG images resolve.
  metadataBase: new URL(`${siteUrl}/`),
  title: { default: siteTitle, template: `%s · ${appName}` },
  description: siteDescription,
  applicationName: appName,
  keywords: siteKeywords,
  authors: [
    { name: gitConfig.user, url: `https://github.com/${gitConfig.user}` },
  ],
  creator: gitConfig.user,
  category: "adult",
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      "max-image-preview": "large",
      "max-snippet": -1,
    },
  },
  openGraph: {
    type: "website",
    siteName: appName,
    locale: "en_US",
    title: siteTitle,
    description: siteDescription,
    images: [{ url: ogImage, width: 1200, height: 630, alt: siteTitle }],
  },
  twitter: {
    card: "summary_large_image",
    title: siteTitle,
    description: siteDescription,
    images: [ogImage],
  },
  // Adult label: SafeSearch reads either value of "rating"; the RTA one is also read by parental-control filters.
  other: { rating: ["adult", "RTA-5042-1996-1400-1577-RTA"] },
};

export default function Layout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={`dark ${inter.className}`}
      suppressHydrationWarning
    >
      <body className="flex flex-col min-h-screen">
        <Provider>{children}</Provider>
      </body>
    </html>
  );
}
