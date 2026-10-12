import { Radio, LinkIcon, Tag, Server } from "lucide-react";
import type { LucideIcon } from "lucide-react";

interface Feature {
  icon: LucideIcon;
  title: string;
  description: string;
}

const FEATURES: Feature[] = [
  {
    icon: Radio,
    title: "Live Status Detection",
    description:
      "Automatically detects when a linked YouTube account is actively streaming Minecraft.",
  },
  {
    icon: LinkIcon,
    title: "Simple YouTube Linking",
    description:
      "Players link their YouTube account through an authorization flow initiated in Minecraft.",
  },
  {
    icon: Tag,
    title: "Automatic Nametag Indicator",
    description:
      "Displays a LIVE suffix beside the player's name while they are streaming.",
  },
  {
    icon: Server,
    title: "Paper Server Support",
    description:
      "Designed for Minecraft servers running Paper, with clear setup instructions.",
  },
];

export default function Features() {
  return (
    <section id="features" className="py-24 scroll-mt-20">
      <div className="mx-auto max-w-7xl px-6">
        <div className="text-center mb-14">
          <h2 className="text-3xl sm:text-4xl font-bold">
            Everything You Need to <span className="text-accent">Go Live</span>
          </h2>
          <p className="mt-4 text-text-secondary max-w-2xl mx-auto">
            A single JAR. No external backends, no client mods, no complicated setup.
          </p>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
          {FEATURES.map(({ icon: Icon, title, description }) => (
            <div
              key={title}
              className="group rounded-xl border border-border bg-bg-secondary p-6 hover:border-accent/40 hover:bg-bg-card transition-all duration-300"
            >
              <div className="mb-4 inline-flex h-11 w-11 items-center justify-center rounded-lg bg-accent/10 text-accent group-hover:bg-accent/20 transition-colors duration-300">
                <Icon size={22} />
              </div>
              <h3 className="text-base font-semibold mb-2">{title}</h3>
              <p className="text-sm text-text-secondary leading-relaxed">
                {description}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
