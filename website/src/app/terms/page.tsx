import Link from "next/link";
import Header from "@/components/Header";
import Footer from "@/components/Footer";

export const metadata = {
  title: "Terms of Service - MCast.fun",
  description: "Terms and conditions for MCast platform, YouTube API integration, and Minecraft plugin usage.",
};

export default function TermsOfServicePage() {
  return (
    <div className="min-h-screen bg-bg-primary text-text-primary flex flex-col">
      <Header />

      <main className="flex-1 py-16 px-6">
        <div className="mx-auto max-w-4xl space-y-10">
          <div>
            <Link
              href="/"
              className="text-xs text-accent hover:underline mb-4 inline-block"
            >
              &larr; Back to Home
            </Link>
            <h1 className="text-3xl font-extrabold tracking-tight sm:text-4xl text-white">
              Terms of Service
            </h1>
            <p className="mt-2 text-sm text-text-secondary">
              Last Updated: October 2026
            </p>
          </div>

          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-8">
            <h2 className="text-xl font-bold text-accent">1. Acceptance of Terms</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              By accessing or using <code className="text-accent">https://mcast.fun</code> or installing the MCast LiveStatus Minecraft plugin, you agree to be bound by these Terms of Service and our Privacy Policy.
            </p>
          </section>

          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-8">
            <h2 className="text-xl font-bold text-accent">2. YouTube Terms of Service</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              MCast utilizes YouTube API Services to detect and display live broadcasting status. By connecting your YouTube account with MCast, you also agree to be bound by the{" "}
              <a
                href="https://www.youtube.com/t/terms"
                target="_blank"
                rel="noopener noreferrer"
                className="text-accent underline"
              >
                YouTube Terms of Service
              </a>{" "}
              and the{" "}
              <a
                href="https://policies.google.com/privacy"
                target="_blank"
                rel="noopener noreferrer"
                className="text-accent underline"
              >
                Google Privacy Policy
              </a>.
            </p>
          </section>

          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-8">
            <h2 className="text-xl font-bold text-accent">3. Streamer & Player Responsibilities</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              When linking a YouTube channel to a Minecraft account:
            </p>
            <ul className="list-disc list-inside space-y-2 text-sm text-text-secondary">
              <li>You confirm that you own or have authorized control over the linked YouTube channel.</li>
              <li>You agree not to broadcast inappropriate, illegal, or abusive content using MCast nametag or chat announcements.</li>
              <li>You may unlink your account at any time using in-game commands or Google security settings.</li>
            </ul>
          </section>

          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-8">
            <h2 className="text-xl font-bold text-accent">4. Server Administrator Responsibilities</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              Minecraft server owners who deploy the MCast plugin agree:
            </p>
            <ul className="list-disc list-inside space-y-2 text-sm text-text-secondary">
              <li>Not to attempt extraction, reverse engineering, or manipulation of authentication sessions.</li>
              <li>To enforce fair use on their servers and promptly unlink offending accounts.</li>
              <li>That MCast is not responsible for player-generated broadcast content.</li>
            </ul>
          </section>

          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-8">
            <h2 className="text-xl font-bold text-accent">5. Disclaimer of Warranties</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              MCast is provided &quot;as is&quot; without warranties of any kind. We do not guarantee uninterrupted availability of Google or YouTube APIs.
              MCast is an independent project and is not affiliated with Mojang Studios, Microsoft Corporation, or Google LLC.
            </p>
          </section>

          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-8">
            <h2 className="text-xl font-bold text-accent">6. Contact</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              Questions regarding these Terms may be directed to:{" "}
              <a href="mailto:contact@mcast.fun" className="text-accent underline">
                contact@mcast.fun
              </a>
            </p>
          </section>
        </div>
      </main>

      <Footer />
    </div>
  );
}
