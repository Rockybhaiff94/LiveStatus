import Link from "next/link";
import { GitFork, FileText, Mail } from "lucide-react";

export default function Footer() {
  return (
    <footer className="border-t border-border bg-bg-secondary">
      <div className="mx-auto max-w-7xl px-6 py-12">
        <div className="grid gap-10 md:grid-cols-3">
          {/* Brand */}
          <div>
            <Link href="/" className="text-xl font-bold">
              <span className="text-accent">M</span>Cast
              <span className="text-text-secondary text-sm font-normal">
                .fun
              </span>
            </Link>
            <p className="mt-3 text-sm text-text-secondary leading-relaxed max-w-xs">
              A self-contained Minecraft Paper plugin that adds a LIVE indicator
              to player nametags when streaming on YouTube.
            </p>
          </div>

          {/* Links */}
          <div>
            <h3 className="text-sm font-semibold mb-3">Resources</h3>
            <ul className="space-y-2 text-sm text-text-secondary">
              <li>
                <Link
                  href="#docs"
                  className="hover:text-accent transition-colors"
                >
                  Documentation
                </Link>
              </li>
              <li>
                <a
                  href="https://github.com/Rockybhaiff94/MCast"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 hover:text-accent transition-colors"
                >
                  <GitFork size={14} />
                  GitHub
                </a>
              </li>
              <li>
                <a
                  href="mailto:contact@mcast.fun"
                  className="inline-flex items-center gap-1.5 hover:text-accent transition-colors"
                >
                  <Mail size={14} />
                  Contact
                </a>
              </li>
            </ul>
          </div>

          {/* Legal */}
          <div>
            <h3 className="text-sm font-semibold mb-3">Legal</h3>
            <ul className="space-y-1.5 text-xs text-text-secondary mb-3">
              <li>
                <Link href="/privacy-policy" className="hover:text-accent transition-colors">
                  Privacy Policy
                </Link>
              </li>
              <li>
                <Link href="/terms-of-service" className="hover:text-accent transition-colors">
                  Terms of Service
                </Link>
              </li>
            </ul>
            <p className="text-xs text-text-secondary leading-relaxed">
              MCast is an independent community project and is not affiliated
              with Mojang Studios, Microsoft Corporation, or Google LLC.
            </p>
            <p className="text-xs text-text-secondary mt-1">
              Minecraft is a trademark of Mojang Studios. YouTube is a trademark
              of Google LLC.
            </p>
          </div>
        </div>

        <div className="mt-10 pt-6 border-t border-border flex flex-col md:flex-row items-center justify-between gap-4">
          <p className="text-xs text-text-secondary">
            &copy; 2026 MCast.fun. All rights reserved.
          </p>
          <div className="flex items-center gap-4">
            <a
              href="https://github.com/Rockybhaiff94/MCast"
              target="_blank"
              rel="noopener noreferrer"
              className="text-text-secondary hover:text-accent transition-colors"
              aria-label="GitHub"
            >
              <GitFork size={18} />
            </a>
            <a
              href="#docs"
              className="text-text-secondary hover:text-accent transition-colors"
              aria-label="Docs"
            >
              <FileText size={18} />
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}
