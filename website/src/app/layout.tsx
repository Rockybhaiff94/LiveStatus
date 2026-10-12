import type { Metadata } from "next";
import { Inter } from "next/font/google";
import { SpeedInsights } from "@vercel/speed-insights/next";
import "./globals.css";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "MCast.fun — Minecraft Live Status Plugin",
  description:
    "Show a LIVE indicator on your Minecraft player nametag when streaming on YouTube. A self-contained Paper plugin with YouTube OAuth integration.",
  keywords: [
    "Minecraft",
    "live",
    "streaming",
    "YouTube",
    "Paper plugin",
    "nametag",
    "MCast",
  ],
  openGraph: {
    title: "MCast.fun — Minecraft Live Status Plugin",
    description:
      "Show a LIVE indicator on Minecraft nametags when streaming on YouTube.",
    type: "website",
    url: "https://mcast.fun",
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="dark" suppressHydrationWarning>
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `
              if (typeof window !== 'undefined') {
                window.addEventListener('error', function(e) {
                  if (e.filename && (e.filename.indexOf('chrome-extension://') !== -1 || e.filename.indexOf('moz-extension://') !== -1)) {
                    e.stopImmediatePropagation();
                  }
                }, true);
                window.addEventListener('unhandledrejection', function(e) {
                  var stack = (e.reason && e.reason.stack) || '';
                  if (stack.indexOf('chrome-extension://') !== -1 || stack.indexOf('moz-extension://') !== -1) {
                    e.stopImmediatePropagation();
                    e.preventDefault();
                  }
                }, true);
              }
            `,
          }}
        />
      </head>
      <body
        className={`${inter.className} min-h-screen bg-bg-primary text-text-primary`}
        suppressHydrationWarning
      >
        {children}
        <SpeedInsights />
      </body>
    </html>
  );
}
