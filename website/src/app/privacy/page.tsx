import Link from "next/link";
import Header from "@/components/Header";
import Footer from "@/components/Footer";

export const metadata = {
  title: "Privacy Policy - MCast.fun",
  description: "MCast privacy policy, data protection, and Google API User Data Policy compliance.",
};

export default function PrivacyPolicyPage() {
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
              Privacy Policy
            </h1>
            <p className="mt-2 text-sm text-text-secondary">
              Last Updated: October 2026
            </p>
          </div>

          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-8">
            <h2 className="text-xl font-bold text-accent">1. Overview</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              MCast (&quot;we&quot;, &quot;our&quot;, &quot;us&quot;) provides the MCast LiveStatus Minecraft plugin and web services at <code className="text-accent">https://mcast.fun</code>.
              This Privacy Policy explains how we collect, use, and protect your information when you link your YouTube account to your Minecraft player profile.
            </p>
          </section>

          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-8">
            <h2 className="text-xl font-bold text-accent">2. Information We Collect</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              When you link your YouTube channel through MCast, we collect only the minimum data required to deliver the live nametag and announcement features:
            </p>
            <ul className="list-disc list-inside space-y-2 text-sm text-text-secondary">
              <li><strong className="text-white">Minecraft Player Identity:</strong> Your Minecraft account UUID and in-game username.</li>
              <li><strong className="text-white">Public YouTube Metadata:</strong> Your YouTube channel ID, channel title, and custom handle.</li>
              <li><strong className="text-white">Google OAuth Tokens:</strong> An encrypted OAuth 2.0 refresh token used strictly to query your live broadcast status.</li>
            </ul>
            <p className="text-sm text-text-secondary leading-relaxed mt-2">
              We <strong className="text-white">never</strong> collect, view, or store your Google password, personal emails, search history, private videos, or payment card numbers.
            </p>
          </section>

          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-8">
            <h2 className="text-xl font-bold text-accent">3. Google API Services User Data Policy Compliance</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              MCast&apos;s use and transfer of information received from Google APIs adhere to the{" "}
              <a
                href="https://developers.google.com/terms/api-services-user-data-policy"
                target="_blank"
                rel="noopener noreferrer"
                className="text-accent underline"
              >
                Google API Services User Data Policy
              </a>
              , including the Limited Use requirements.
            </p>
            <p className="text-sm text-text-secondary leading-relaxed">
              Specifically:
            </p>
            <ul className="list-disc list-inside space-y-2 text-sm text-text-secondary">
              <li>We only request the <code className="text-accent">https://www.googleapis.com/auth/youtube.readonly</code> scope.</li>
              <li>Data is accessed solely to identify whether your YouTube channel is actively broadcasting Minecraft live.</li>
              <li>We do not transfer or sell user data to advertising platforms, data brokers, or information resellers.</li>
              <li>Human beings do not read your Google data unless required for security investigations or required by law.</li>
            </ul>
          </section>

          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-8">
            <h2 className="text-xl font-bold text-accent">4. Security & Encryption at Rest</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              All Google OAuth refresh tokens are encrypted at rest using industry-standard <strong className="text-white">AES-256-GCM</strong> encryption.
              Unique initialization vectors (IVs) and authentication tags are generated for every token record.
            </p>
            <p className="text-sm text-text-secondary leading-relaxed">
              Refresh tokens and Google API credentials are <strong className="text-white">never embedded or distributed</strong> inside the downloadable Minecraft plugin JAR,
              and are never shared with Minecraft server administrators.
            </p>
          </section>

          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-8">
            <h2 className="text-xl font-bold text-accent">5. How to Revoke Access and Delete Data</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              You maintain complete control over your linked account:
            </p>
            <ul className="list-disc list-inside space-y-2 text-sm text-text-secondary">
              <li><strong className="text-white">In Minecraft:</strong> Type <code className="text-accent">/link youtube unlink</code> on any server where you are linked.</li>
              <li><strong className="text-white">Via Google:</strong> Revoke MCast&apos;s permissions anytime in your{" "}
                <a
                  href="https://myaccount.google.com/permissions"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-accent underline"
                >
                  Google Account Security Settings
                </a>.
              </li>
              <li><strong className="text-white">Email Request:</strong> Contact us at <a href="mailto:contact@mcast.fun" className="text-accent underline">contact@mcast.fun</a> to request immediate deletion of all stored records.</li>
            </ul>
          </section>

          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-8">
            <h2 className="text-xl font-bold text-accent">6. Contact Us</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              If you have any questions about this Privacy Policy or our security practices, contact us at:{" "}
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
