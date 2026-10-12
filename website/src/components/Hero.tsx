import Link from "next/link";
import { Download, FileText } from "lucide-react";
import NametagPreview from "./NametagPreview";

export default function Hero() {
  return (
    <section className="relative min-h-screen flex items-center overflow-hidden">
      {/* Background glow */}
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 w-[600px] h-[600px] rounded-full bg-accent/5 blur-[120px]" />
      </div>

      <div className="relative mx-auto max-w-7xl px-6 pt-28 pb-20 w-full">
        <div className="grid lg:grid-cols-2 gap-16 items-center">
          {/* Copy */}
          <div className="animate-fade-in-up">
            <div className="inline-flex items-center gap-2 rounded-full border border-border bg-bg-secondary px-4 py-1.5 text-xs text-text-secondary mb-6">
              <span className="inline-block h-2 w-2 rounded-full bg-accent animate-pulse-dot" />
              Open-source Minecraft plugin
            </div>

            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold leading-[1.1] tracking-tight">
              Your Minecraft Stream.{" "}
              <span className="text-accent">Always Live.</span>
            </h1>

            <p className="mt-6 text-lg text-text-secondary leading-relaxed max-w-xl">
              Let your Minecraft community know when you&apos;re live on
              YouTube. MCast brings live streaming status directly to player
              nametags on your Minecraft server.
            </p>

            <div className="mt-8 flex flex-wrap gap-4">
              <Link
                href="#download"
                className="inline-flex items-center gap-2 rounded-lg bg-accent px-7 py-3.5 text-sm font-semibold text-black hover:bg-accent-hover transition-colors duration-200 shadow-lg shadow-accent/20"
              >
                <Download size={18} />
                Download Plugin
              </Link>
              <Link
                href="#docs"
                className="inline-flex items-center gap-2 rounded-lg border border-border px-7 py-3.5 text-sm font-semibold text-text-primary hover:border-accent hover:text-accent transition-colors duration-200"
              >
                <FileText size={18} />
                View Documentation
              </Link>
            </div>
          </div>

          {/* Visual */}
          <div className="flex justify-center lg:justify-end animate-fade-in-up" style={{ animationDelay: ".15s" }}>
            <NametagPreview />
          </div>
        </div>
      </div>
    </section>
  );
}
