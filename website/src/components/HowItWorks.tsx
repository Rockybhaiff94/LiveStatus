"use client";

import { FolderDown, Link2, Tv } from "lucide-react";
import { useState } from "react";
import type { LucideIcon } from "lucide-react";

interface Step {
  icon: LucideIcon;
  number: string;
  title: string;
  description: string;
  command?: string;
}

const STEPS: Step[] = [
  {
    icon: FolderDown,
    number: "01",
    title: "Install",
    description:
      "Place the LiveStatus JAR in your server's plugins/ folder and restart.",
  },
  {
    icon: Link2,
    number: "02",
    title: "Connect",
    description:
      "An admin permits linking, then the player runs /link youtube and completes Google authorization.",
    command: "/youtube allow-link <player>\n/link youtube",
  },
  {
    icon: Tv,
    number: "03",
    title: "Go Live",
    description:
      "When YouTube detects the player's active Minecraft broadcast, the nametag displays LIVE.",
  },
];

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = () => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  };
  return (
    <button
      onClick={handleCopy}
      className="absolute top-2 right-2 rounded bg-border/60 px-2 py-1 text-[10px] text-text-secondary hover:text-accent hover:bg-border transition-colors"
      aria-label="Copy to clipboard"
    >
      {copied ? "Copied!" : "Copy"}
    </button>
  );
}

function StepCard({ step }: { step: Step }) {
  const Icon = step.icon;
  return (
    <div className="relative rounded-xl border border-border bg-bg-secondary p-6 hover:border-accent/40 transition-colors duration-300">
      {/* Step number */}
      <span className="absolute -top-3 -left-2 text-5xl font-extrabold text-accent/10 select-none">
        {step.number}
      </span>

      <div className="flex items-center gap-3 mb-4">
        <div className="inline-flex h-10 w-10 items-center justify-center rounded-lg bg-accent/10 text-accent">
          <Icon size={20} />
        </div>
        <h3 className="text-lg font-semibold">{step.title}</h3>
      </div>

      <p className="text-sm text-text-secondary leading-relaxed mb-4">
        {step.description}
      </p>

      {step.command && (
        <div className="relative rounded-lg bg-bg-primary border border-border p-3 font-mono text-xs text-text-secondary overflow-x-auto">
          <CopyButton text={step.command} />
          <pre className="whitespace-pre">{step.command}</pre>
        </div>
      )}
    </div>
  );
}

export default function HowItWorks() {
  return (
    <section id="how-it-works" className="py-24 bg-bg-secondary scroll-mt-20">
      <div className="mx-auto max-w-7xl px-6">
        <div className="text-center mb-14">
          <h2 className="text-3xl sm:text-4xl font-bold">
            Three Steps to <span className="text-accent">Go Live</span>
          </h2>
          <p className="mt-4 text-text-secondary max-w-lg mx-auto">
            From download to live indicator in minutes.
          </p>
        </div>

        <div className="grid md:grid-cols-3 gap-6">
          {STEPS.map((s) => (
            <StepCard key={s.number} step={s} />
          ))}
        </div>
      </div>
    </section>
  );
}
