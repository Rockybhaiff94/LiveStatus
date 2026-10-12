import { Download, Server, Gamepad2 } from "lucide-react";

const GITHUB_RELEASE_URL =
  "https://github.com/PokeHowl/LiveStatus/releases/latest";

export default function DownloadSection() {
  return (
    <section id="download" className="py-24 scroll-mt-20">
      <div className="mx-auto max-w-7xl px-6">
        <div className="mx-auto max-w-2xl">
          <div className="rounded-2xl border border-border bg-bg-secondary p-8 sm:p-10 glow-border text-center">
            {/* Icon */}
            <div className="mx-auto mb-6 inline-flex h-16 w-16 items-center justify-center rounded-2xl bg-accent/10 text-accent">
              <Download size={32} />
            </div>

            <h2 className="text-2xl sm:text-3xl font-bold mb-3">
              Download <span className="text-accent">LiveStatus</span>
            </h2>
            <p className="text-text-secondary text-sm leading-relaxed max-w-md mx-auto mb-6">
              A single self-contained JAR. Drop it in your Paper server&apos;s
              plugins folder and you&apos;re ready.
            </p>

            {/* Meta */}
            <div className="flex flex-wrap items-center justify-center gap-4 mb-8 text-xs text-text-secondary">
              <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-bg-primary px-3 py-1.5">
                <Server size={12} />
                Paper Server
              </span>
              <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-bg-primary px-3 py-1.5">
                <Gamepad2 size={12} />
                Minecraft 1.20+
              </span>
            </div>

            {/* CTA */}
            <a
              href={GITHUB_RELEASE_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 rounded-lg bg-accent px-8 py-3.5 text-sm font-semibold text-black hover:bg-accent-hover transition-colors duration-200 shadow-lg shadow-accent/20"
            >
              <Download size={18} />
              Download Latest Version
            </a>

            <p className="mt-4 text-[11px] text-text-secondary/60">
              Releases are published on GitHub. If no release is available yet,
              the link will open the releases page.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
