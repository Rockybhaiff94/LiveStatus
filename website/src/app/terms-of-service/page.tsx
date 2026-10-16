import type { Metadata } from "next";
import Link from "next/link";
import Header from "@/components/Header";
import Footer from "@/components/Footer";
import { FileText, Shield, ExternalLink, CheckCircle2, Scale, Mail } from "lucide-react";

export const metadata: Metadata = {
  title: "Terms of Service — MCast.fun",
  description:
    "Official Terms of Service for MCast LiveStatus. Details usage conditions, YouTube API integration terms, streamer responsibilities, and service disclaimers.",
  alternates: {
    canonical: "https://mcast.fun/terms-of-service",
  },
  robots: {
    index: true,
    follow: true,
  },
  openGraph: {
    title: "Terms of Service — MCast.fun",
    description:
      "Official Terms of Service for MCast LiveStatus. Understand streamer responsibilities, YouTube API terms, and acceptable usage.",
    url: "https://mcast.fun/terms-of-service",
    type: "website",
  },
};

export default function TermsOfServicePage() {
  const lastUpdated = "October 16, 2026";

  return (
    <div className="min-h-screen bg-bg-primary text-text-primary flex flex-col selection:bg-accent selection:text-black">
      <Header />

      <main className="flex-1 py-12 md:py-16 px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-4xl space-y-10">
          {/* Top Breadcrumb & Heading */}
          <div className="border-b border-border pb-8">
            <div className="flex flex-wrap items-center justify-between gap-4 mb-4">
              <Link
                href="/"
                className="text-xs font-semibold text-accent hover:text-accent-hover inline-flex items-center gap-1.5 transition-colors"
              >
                &larr; Back to Home
              </Link>
              <div className="flex items-center gap-2">
                <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-bg-secondary px-3 py-1 text-xs text-text-secondary">
                  <FileText size={12} className="text-accent" />
                  Legal Agreement
                </span>
                <Link
                  href="/privacy-policy"
                  className="inline-flex items-center gap-1.5 rounded-full border border-border-accent bg-accent/10 px-3 py-1 text-xs text-accent hover:bg-accent/20 transition-colors"
                >
                  View Privacy Policy &rarr;
                </Link>
              </div>
            </div>

            <h1 className="text-3xl sm:text-4xl md:text-5xl font-extrabold tracking-tight text-white">
              Terms of Service
            </h1>
            <p className="mt-3 text-sm text-text-secondary">
              Effective Date: {lastUpdated} • Last Updated: {lastUpdated}
            </p>
            <p className="mt-4 text-sm sm:text-base text-text-secondary leading-relaxed">
              These Terms of Service (&quot;Terms&quot;) constitute a legally binding agreement between you (&quot;User&quot;, &quot;Streamer&quot;, &quot;Admin&quot;, or &quot;you&quot;) and <strong className="text-white">MCast</strong> (&quot;MCast&quot;, &quot;we&quot;, &quot;our&quot;, or &quot;us&quot;) governing your access to and use of <code className="text-accent bg-bg-secondary px-1.5 py-0.5 rounded text-xs">https://mcast.fun</code>, our central APIs at <code className="text-accent bg-bg-secondary px-1.5 py-0.5 rounded text-xs">https://api.mcast.fun</code>, and the MCast LiveStatus Minecraft plugin software.
            </p>
          </div>

          {/* Key Principles Card */}
          <div className="rounded-2xl border border-border bg-bg-secondary p-6 sm:p-8 relative overflow-hidden shadow-xl">
            <div className="absolute -top-12 -right-12 w-44 h-44 bg-accent/10 rounded-full blur-3xl pointer-events-none" />
            <h2 className="text-lg font-bold text-accent flex items-center gap-2">
              <CheckCircle2 size={18} className="text-accent" />
              Quick Summary of Key Terms
            </h2>
            <ul className="mt-4 space-y-2.5 text-xs sm:text-sm text-text-secondary">
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">•</span>
                <span><strong className="text-white">YouTube Terms Apply:</strong> Because MCast integrates with YouTube API Services, you must comply with the YouTube Terms of Service and Community Guidelines.</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">•</span>
                <span><strong className="text-white">Channel Ownership:</strong> You may only connect YouTube channels that you personally own or have verified legal authority to represent.</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">•</span>
                <span><strong className="text-white">Fair Gameplay &amp; Content:</strong> You agree not to broadcast illegal, abusive, infringing, or malicious content through in-game nametags or chat announcements.</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">•</span>
                <span><strong className="text-white">Voluntary &amp; Revocable:</strong> You can disconnect your channel at any moment directly in Minecraft (<code className="text-accent">/link youtube unlink</code>) or via Google Security settings.</span>
              </li>
            </ul>
          </div>

          {/* 1. Acceptance of Terms */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">1. Acceptance of Terms</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              By accessing our website (<code className="text-accent">https://mcast.fun</code>), linking your YouTube account, downloading or installing the MCast LiveStatus plugin on a Minecraft server, or utilizing the MCast backend APIs, you confirm that you have read, understood, and agree to be bound by these Terms and our{" "}
              <Link href="/privacy-policy" className="text-accent underline font-semibold hover:text-accent-hover">
                Privacy Policy
              </Link>.
            </p>
            <p className="text-sm text-text-secondary leading-relaxed">
              If you do not agree to these Terms or the Privacy Policy, you must refrain from using MCast services and immediately remove the plugin software and disconnect any linked accounts.
            </p>
          </section>

          {/* 2. Description of MCast Services */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">2. Description of MCast Services</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              MCast provides a centralized live status synchronization platform designed for Minecraft servers running PaperMC 1.21+. The platform includes:
            </p>
            <ul className="list-disc list-inside space-y-2 text-sm text-text-secondary pl-2">
              <li>
                <strong className="text-white">In-Game Nametag Badges:</strong> Displaying an unobtrusive, non-destructive <span className="text-red-400 font-bold bg-red-950/60 px-1 py-0.5 rounded border border-red-500/30 text-xs">LIVE</span> indicator on the nametag of authorized players while they broadcast Minecraft gameplay on YouTube.
              </li>
              <li>
                <strong className="text-white">Automated Fair Chat Broadcasts:</strong> Delivering configurable Adventure MiniMessage in-game announcements when a linked streamer goes live, managed by fair interval rotations.
              </li>
              <li>
                <strong className="text-white">Centralized OAuth Service:</strong> A secure bridge facilitating read-only Google OAuth 2.0 authorization, eliminating the need for server owners to configure individual Google Cloud developer consoles or manage sensitive credentials.
              </li>
              <li>
                <strong className="text-white">Streamer Dashboard:</strong> Web interface at <code className="text-accent">https://mcast.fun/dashboard</code> allowing creators to view their linked channel status, verified game topics, and stream health.
              </li>
            </ul>
          </section>

          {/* 3. Third-Party Services and YouTube Terms of Service */}
          <section className="space-y-4 rounded-xl border border-accent/40 bg-bg-secondary p-6 sm:p-8">
            <div className="flex items-center gap-2">
              <Shield className="text-accent" size={20} />
              <h2 className="text-xl font-bold text-white">
                3. Third-Party Services &amp; YouTube Terms Agreement
              </h2>
            </div>
            <p className="text-sm text-text-secondary leading-relaxed">
              MCast relies upon YouTube API Services provided by Google LLC. By using MCast to link your YouTube channel or display live broadcast status, you acknowledge and agree that you are also bound by:
            </p>
            <div className="space-y-3 mt-2 text-sm">
              <div className="rounded-lg border border-border bg-bg-primary/50 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div>
                  <h3 className="font-bold text-white">YouTube Terms of Service</h3>
                  <p className="text-xs text-text-secondary">Governs all broadcasts, video content, and channel interactions on YouTube.</p>
                </div>
                <a
                  href="https://www.youtube.com/t/terms"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs font-semibold text-accent hover:underline inline-flex items-center gap-1 self-start sm:self-center"
                >
                  youtube.com/t/terms <ExternalLink size={12} />
                </a>
              </div>

              <div className="rounded-lg border border-border bg-bg-primary/50 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div>
                  <h3 className="font-bold text-white">Google Privacy Policy</h3>
                  <p className="text-xs text-text-secondary">Explains how Google processes personal data when using Google accounts.</p>
                </div>
                <a
                  href="https://policies.google.com/privacy"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs font-semibold text-accent hover:underline inline-flex items-center gap-1 self-start sm:self-center"
                >
                  policies.google.com/privacy <ExternalLink size={12} />
                </a>
              </div>

              <div className="rounded-lg border border-border bg-bg-primary/50 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div>
                  <h3 className="font-bold text-white">Google API Services User Data Policy</h3>
                  <p className="text-xs text-text-secondary">Strict limits on application access and handling of Google API data.</p>
                </div>
                <a
                  href="https://developers.google.com/terms/api-services-user-data-policy"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs font-semibold text-accent hover:underline inline-flex items-center gap-1 self-start sm:self-center"
                >
                  developers.google.com <ExternalLink size={12} />
                </a>
              </div>
            </div>
            <p className="text-xs text-text-secondary leading-relaxed mt-2">
              You may review and revoke MCast&apos;s access to your YouTube data at any time via your{" "}
              <a
                href="https://myaccount.google.com/permissions"
                target="_blank"
                rel="noopener noreferrer"
                className="text-accent underline"
              >
                Google Security Settings
              </a>.
            </p>
          </section>

          {/* 4. Eligibility and Streamer Responsibilities */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">4. Eligibility &amp; Streamer Responsibilities</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              To link a YouTube channel and utilize MCast features:
            </p>
            <ul className="list-disc list-inside space-y-2 text-sm text-text-secondary pl-2">
              <li>
                <strong className="text-white">Age Requirement:</strong> You must be at least 13 years old (or the minimum legal age required in your country to consent to online services). If you are under the age of majority, you confirm that your parent or legal guardian has reviewed and agreed to these Terms.
              </li>
              <li>
                <strong className="text-white">Authorized Channel Control:</strong> You confirm that you are the lawful owner or authorized administrative manager of the YouTube channel you connect. Attempting to link channels owned by other creators is strictly prohibited.
              </li>
              <li>
                <strong className="text-white">Truthful Information:</strong> You agree to provide accurate information during linking and ensure your channel title, avatar, and broadcast metadata do not infringe on any third party&apos;s rights.
              </li>
              <li>
                <strong className="text-white">Compliance with Community Rules:</strong> You agree to adhere to the rules, etiquette, and guidelines established by the specific Minecraft server networks on which you play.
              </li>
            </ul>
          </section>

          {/* 5. Acceptable-Use Rules and Prohibited Activities */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">5. Acceptable-Use Rules &amp; Prohibited Activities</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              You agree not to misuse MCast services. Specifically, you must NOT:
            </p>
            <div className="grid gap-3 sm:grid-cols-2 text-xs sm:text-sm mt-2">
              <div className="rounded-lg border border-border bg-bg-primary/50 p-4 space-y-1">
                <h3 className="font-semibold text-red-400">Harmful or Illegal Broadcasts</h3>
                <p className="text-text-secondary leading-relaxed">
                  Promote illegal acts, hate speech, violent extremism, harassment, defamation, sexually explicit material, or malicious content through stream titles or chat broadcasts.
                </p>
              </div>

              <div className="rounded-lg border border-border bg-bg-primary/50 p-4 space-y-1">
                <h3 className="font-semibold text-red-400">Security Circumvention &amp; Tampering</h3>
                <p className="text-text-secondary leading-relaxed">
                  Attempt to probe, scan, decrypt, reverse engineer, intercept, or tamper with OAuth tokens, cryptographic session nonces, server secrets, or encryption keys.
                </p>
              </div>

              <div className="rounded-lg border border-border bg-bg-primary/50 p-4 space-y-1">
                <h3 className="font-semibold text-red-400">API Abuse &amp; Denial of Service</h3>
                <p className="text-text-secondary leading-relaxed">
                  Flood, spam, launch DDoS attacks, or deliberately overload MCast endpoints, or artificially manipulate polling requests to consume excessive Google API quotas.
                </p>
              </div>

              <div className="rounded-lg border border-border bg-bg-primary/50 p-4 space-y-1">
                <h3 className="font-semibold text-red-400">False Impersonation</h3>
                <p className="text-text-secondary leading-relaxed">
                  Impersonate another streamer, server administrator, or entity, or falsify server identity tokens to access another player&apos;s linked broadcast profile.
                </p>
              </div>
            </div>
          </section>

          {/* 6. Server Administrators' Terms */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">6. Minecraft Server Administrators&apos; Responsibilities</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              Minecraft server operators who install and run the MCast plugin agree to:
            </p>
            <ul className="list-disc list-inside space-y-2 text-sm text-text-secondary pl-2">
              <li>Deploy the official, unmodified plugin JAR compiled from the official source repository.</li>
              <li>Securely safeguard their assigned <code className="text-white">server_secret</code> and not expose it in public configuration files, version control, or public forums.</li>
              <li>Respect player privacy and honor streamer requests to unlink accounts or disable chat announcements promptly.</li>
              <li>Acknowledge that MCast is not liable for moderation disputes, in-game bans, player grievances, or content broadcast by server members.</li>
            </ul>
          </section>

          {/* 7. User Authorization and Permission Management */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">7. User Authorization &amp; Permission Management</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              When you link your YouTube channel through MCast, you grant us permission to:
            </p>
            <ul className="list-disc list-inside space-y-2 text-sm text-text-secondary pl-2">
              <li>Access your public channel profile (channel ID, title, handle, and avatar URL).</li>
              <li>Periodically query active live broadcasts on your channel using the <code className="text-accent">youtube.readonly</code> scope.</li>
              <li>Transmit live broadcast status and stream title to authorized Minecraft servers where your player profile is registered.</li>
            </ul>
            <p className="text-sm text-text-secondary leading-relaxed">
              You retain the absolute right to revoke these permissions at any time. Revocation takes effect immediately without penalty.
            </p>
          </section>

          {/* 8. Intellectual Property and Content Ownership */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">8. Intellectual Property &amp; Content Ownership</h2>
            <div className="space-y-3 text-sm text-text-secondary leading-relaxed">
              <p>
                <strong className="text-white">Your Content:</strong> You retain full and exclusive copyright and intellectual property rights in and to your YouTube live streams, videos, audio, channel branding, and logos. MCast claims no ownership over your creative content.
              </p>
              <p>
                <strong className="text-white">MCast Platform:</strong> The MCast name, web design, software code, plugin architecture, database schemas, and documentation are the intellectual property of the MCast project and contributors, protected by applicable copyright and open-source licenses.
              </p>
              <div className="border border-border/80 rounded-lg p-4 bg-bg-primary/50 text-xs text-text-secondary mt-2">
                <p className="font-semibold text-white">Non-Affiliation Notice:</p>
                <p className="mt-1">
                  Minecraft is a registered trademark of <strong className="text-white">Mojang Studios / Microsoft Corporation</strong>.
                  YouTube is a trademark of <strong className="text-white">Google LLC</strong>.
                  MCast is an independent community open-source project and is not affiliated with, sponsored by, or endorsed by Mojang Studios, Microsoft, or Google.
                </p>
              </div>
            </div>
          </section>

          {/* 9. Service Availability, Changes, and Disclaimer of Warranties */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <div className="flex items-center gap-2">
              <Scale className="text-accent" size={20} />
              <h2 className="text-xl font-bold text-accent">9. Service Availability &amp; Disclaimer of Warranties</h2>
            </div>
            <p className="text-xs sm:text-sm text-text-secondary leading-relaxed uppercase tracking-wider font-semibold">
              PLEASE READ THIS SECTION CAREFULLY AS IT LIMITS OUR LIABILITY.
            </p>
            <div className="space-y-3 text-xs sm:text-sm text-text-secondary leading-relaxed">
              <p>
                MCast SERVICES, INCLUDING THE WEBSITE, BACKEND APIS, AND MINECRAFT PLUGIN, ARE PROVIDED ON AN <strong className="text-white">&quot;AS IS&quot;</strong> AND <strong className="text-white">&quot;AS AVAILABLE&quot;</strong> BASIS WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS, IMPLIED, STATUTORY, OR OTHERWISE.
              </p>
              <p>
                WE EXPRESSLY DISCLAIM ALL WARRANTIES, INCLUDING BUT NOT LIMITED TO IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE, AND NON-INFRINGEMENT.
              </p>
              <p>
                WE DO NOT GUARANTEE THAT:
              </p>
              <ul className="list-disc list-inside space-y-1 pl-2">
                <li>MCast will operate uninterrupted, secure, or error-free at all times;</li>
                <li>Live stream status detection will be instantaneously synchronized with YouTube Studio;</li>
                <li>Upstream YouTube Data API v3 services or quota limits will never experience downtime, delays, or rate limiting; or</li>
                <li>Minecraft server network conditions or third-party scoreboard plugins will never conflict with nametag suffixes.</li>
              </ul>
            </div>
          </section>

          {/* 10. Limitation of Liability */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">10. Limitation of Liability</h2>
            <p className="text-xs sm:text-sm text-text-secondary leading-relaxed">
              TO THE FULLEST EXTENT PERMITTED BY APPLICABLE LAW, IN NO EVENT SHALL MCAST, ITS MAINTAINERS, CONTRIBUTORS, OPERATORS, OR AFFILIATES BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES, INCLUDING BUT NOT LIMITED TO LOSS OF PROFITS, DATA, USE, GOODWILL, OR OTHER INTANGIBLE LOSSES RESULTING FROM:
            </p>
            <ul className="list-disc list-inside space-y-1.5 text-xs sm:text-sm text-text-secondary pl-2">
              <li>YOUR ACCESS TO OR INABILITY TO ACCESS OR USE THE SERVICE;</li>
              <li>ANY DELAY, FAILURE, OR DISRUPTION IN LIVE BROADCAST DETECTION;</li>
              <li>ANY MODERATION ACTION, SUSPENSION, OR BAN ENFORCED BY MINECRAFT SERVER OWNERS;</li>
              <li>ANY UNAUTHORIZED ACCESS, USE, OR ALTERATION OF YOUR TRANSMISSIONS OR STORED DATA; OR</li>
              <li>ANY THIRD-PARTY ACTIONS, POLICIES, OR OUTAGES IMPOSED BY GOOGLE, YOUTUBE, OR MOJANG.</li>
            </ul>
          </section>

          {/* 11. Account Suspension and Termination */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">11. Account Suspension &amp; Termination</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              We reserve the right, without prior notice or liability, to suspend, terminate, or revoke access to MCast services, server registrations, or linked account associations if:
            </p>
            <ul className="list-disc list-inside space-y-1.5 text-sm text-text-secondary pl-2">
              <li>You violate these Terms of Service or our Privacy Policy;</li>
              <li>Your linked channel is suspended or terminated by YouTube;</li>
              <li>You engage in security tampering, denial-of-service attempts, or automated scraping;</li>
              <li>A Minecraft server owner requests removal of an unauthorized link; or</li>
              <li>We are required to do so by applicable law, regulation, or Google API policy.</li>
            </ul>
            <p className="text-sm text-text-secondary leading-relaxed">
              Upon termination, all associated session records, authorization links, and active badges will be immediately revoked.
            </p>
          </section>

          {/* 12. Account Disconnection and Data Deletion */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">12. Account Disconnection &amp; Data Deletion</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              You may terminate these Terms at any time with respect to your personal account by:
            </p>
            <ol className="list-decimal list-inside space-y-1.5 text-sm text-text-secondary pl-2">
              <li>Running <code className="text-accent">/link youtube unlink</code> on your Minecraft server; or</li>
              <li>Revoking permissions on <a href="https://myaccount.google.com/permissions" target="_blank" rel="noopener noreferrer" className="text-accent underline">Google Security Settings</a>; and</li>
              <li>Optionally requesting permanent database erasure by emailing <a href="mailto:contact@mcast.fun" className="text-accent underline">contact@mcast.fun</a>.</li>
            </ol>
            <p className="text-sm text-text-secondary leading-relaxed">
              All data deletion requests are processed in accordance with Section 12 of our Privacy Policy.
            </p>
          </section>

          {/* 13. Governing Law and Jurisdiction */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">13. Governing Law &amp; Dispute Resolution</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              As an open-source, community-driven software project, these Terms shall be governed by and construed in accordance with generally recognized principles of contract law and applicable jurisdictional standards, without giving effect to any principles of conflicts of law.
            </p>
            <p className="text-sm text-text-secondary leading-relaxed">
              <strong className="text-white">Informal Resolution First:</strong> Before initiating any formal legal dispute, claim, or arbitration, you agree to contact the project maintainers directly at <a href="mailto:contact@mcast.fun" className="text-accent underline">contact@mcast.fun</a> to attempt an informal, good-faith resolution of any grievance or dispute.
            </p>
          </section>

          {/* 14. Changes to These Terms */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">14. Modifications to Terms</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              We reserve the right to modify or replace these Terms at our sole discretion. Any changes will be posted on this page with an updated &quot;Last Updated&quot; date.
            </p>
            <p className="text-sm text-text-secondary leading-relaxed">
              Your continued access to or use of MCast after any revisions become effective constitutes your acceptance of the revised Terms. If you do not agree to the new terms, you must discontinue using our services.
            </p>
          </section>

          {/* 15. Contact Information */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <div className="flex items-center gap-2">
              <Mail className="text-accent" size={20} />
              <h2 className="text-xl font-bold text-accent">15. Contact Information</h2>
            </div>
            <p className="text-sm text-text-secondary leading-relaxed">
              For questions, notices, or inquiries regarding these Terms of Service, please contact:
            </p>
            <div className="mt-3 p-4 rounded-lg border border-border bg-bg-primary/50 text-sm space-y-1.5 text-text-secondary">
              <p><strong className="text-white">Project:</strong> MCast (LiveStatus)</p>
              <p><strong className="text-white">Legal &amp; Terms Inquiries:</strong> <a href="mailto:contact@mcast.fun" className="text-accent underline">contact@mcast.fun</a></p>
              <p><strong className="text-white">Website:</strong> <a href="https://mcast.fun" className="text-accent underline">https://mcast.fun</a></p>
              <p><strong className="text-white">Open Source Codebase:</strong> <a href="https://github.com/Rockybhaiff94/MCast" target="_blank" rel="noopener noreferrer" className="text-accent underline">GitHub Repository</a></p>
            </div>
          </section>
        </div>
      </main>

      <Footer />
    </div>
  );
}
