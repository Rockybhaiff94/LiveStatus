import {
  BookOpen,
  KeyRound,
  Link2,
  Terminal,
  AlertTriangle,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

interface DocCard {
  icon: LucideIcon;
  title: string;
  description: string;
  href: string;
}

const DOCS: DocCard[] = [
  {
    icon: BookOpen,
    title: "Installation Guide",
    description: "Step-by-step server setup and configuration walkthrough.",
    href: "https://github.com/Rockybhaiff94/MCast/blob/main/docs/SETUP.md",
  },
  {
    icon: KeyRound,
    title: "Centralized OAuth",
    description:
      "All Google OAuth credentials are managed centrally by MCast — zero setup on your server.",
    href: "https://github.com/Rockybhaiff94/MCast/blob/main/docs/OAUTH.md",
  },
  {
    icon: Link2,
    title: "YouTube Account Linking",
    description:
      "How players link their YouTube channel and authorize the plugin.",
    href: "https://github.com/Rockybhaiff94/MCast/blob/main/docs/OAUTH.md",
  },
  {
    icon: Terminal,
    title: "Commands & Permissions",
    description:
      "Full reference for admin, streamer, and player commands.",
    href: "https://github.com/Rockybhaiff94/MCast/blob/main/docs/ADMIN.md",
  },
  {
    icon: AlertTriangle,
    title: "Troubleshooting",
    description:
      "Common issues, API quotas, and compatibility with other plugins.",
    href: "https://github.com/Rockybhaiff94/MCast/blob/main/docs/COMPATIBILITY.md",
  },
];

export default function Documentation() {
  return (
    <section id="docs" className="py-24 bg-bg-secondary scroll-mt-20">
      <div className="mx-auto max-w-7xl px-6">
        <div className="text-center mb-14">
          <h2 className="text-3xl sm:text-4xl font-bold">
            <span className="text-accent">Documentation</span>
          </h2>
          <p className="mt-4 text-text-secondary max-w-lg mx-auto">
            Everything you need to install, configure, and manage the plugin.
          </p>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4 max-w-4xl mx-auto">
          {DOCS.map(({ icon: Icon, title, description, href }) => (
            <a
              key={title}
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className="group flex items-start gap-4 rounded-xl border border-border bg-bg-secondary p-5 hover:border-accent/40 hover:bg-bg-card transition-all duration-300"
            >
              <div className="mt-0.5 inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-accent/10 text-accent group-hover:bg-accent/20 transition-colors duration-300">
                <Icon size={20} />
              </div>
              <div>
                <h3 className="text-sm font-semibold mb-1 group-hover:text-accent transition-colors">
                  {title}
                </h3>
                <p className="text-xs text-text-secondary leading-relaxed">
                  {description}
                </p>
              </div>
            </a>
          ))}
        </div>
      </div>
    </section>
  );
}
