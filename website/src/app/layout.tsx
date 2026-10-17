import type { Metadata } from "next";
import { Inter } from "next/font/google";
import { SpeedInsights } from "@vercel/speed-insights/next";
import { Analytics } from "@vercel/analytics/next";
import "./globals.css";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "MCast — Bring Your Stream Into Minecraft",
  description:
    "MCast brings your YouTube Live status into Minecraft, helping your community spot when you are streaming right in-game.",
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
    title: "MCast — Bring Your Stream Into Minecraft",
    description:
      "MCast brings your YouTube Live status into Minecraft, helping your community spot when you are streaming right in-game.",
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
      <head suppressHydrationWarning>
        <script
          dangerouslySetInnerHTML={{
            __html: `
              if (typeof window !== 'undefined') {
                // 1. Filter hydration mismatch errors and warnings caused by browser extensions (e.g. Bitwarden bis_skin_checked)
                var origError = console.error;
                console.error = function() {
                  for (var i = 0; i < arguments.length; i++) {
                    var arg = arguments[i];
                    if (typeof arg === 'string' && (arg.indexOf('bis_skin_checked') !== -1 || (arg.indexOf('hydrat') !== -1 && (arg.indexOf('extension') !== -1 || arg.indexOf('skin') !== -1)))) {
                      return;
                    }
                  }
                  origError.apply(console, arguments);
                };

                // 2. Automatically strip bis_skin_checked attributes injected by extensions into the DOM
                try {
                  var clean = function(el) {
                    if (el && el.removeAttribute) el.removeAttribute('bis_skin_checked');
                  };
                  var observer = new MutationObserver(function(mutations) {
                    for (var i = 0; i < mutations.length; i++) {
                      var m = mutations[i];
                      if (m.type === 'attributes' && m.attributeName === 'bis_skin_checked') {
                        clean(m.target);
                      } else if (m.type === 'childList') {
                        for (var j = 0; j < m.addedNodes.length; j++) {
                          var node = m.addedNodes[j];
                          if (node.nodeType === 1) {
                            clean(node);
                            if (node.querySelectorAll) {
                              var els = node.querySelectorAll('[bis_skin_checked]');
                              for (var k = 0; k < els.length; k++) clean(els[k]);
                            }
                          }
                        }
                      }
                    }
                  });
                  observer.observe(document.documentElement, {
                    attributes: true,
                    attributeFilter: ['bis_skin_checked'],
                    childList: true,
                    subtree: true,
                  });
                } catch (e) {}

                // 3. Catch unhandled extension script runtime errors
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
        <Analytics />
      </body>
    </html>
  );
}
