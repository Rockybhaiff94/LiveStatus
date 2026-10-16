import type { Metadata } from "next";
import Link from "next/link";
import Header from "@/components/Header";
import Footer from "@/components/Footer";
import { Shield, Key, ExternalLink, RefreshCw, Trash2, Mail, Lock, CheckCircle2, AlertTriangle } from "lucide-react";

export const metadata: Metadata = {
  title: "Privacy Policy — MCast.fun",
  description:
    "Official Privacy Policy for MCast LiveStatus. Details data collection, AES-256-GCM token encryption, YouTube Data API usage, and Google API Services User Data Policy compliance.",
  alternates: {
    canonical: "https://mcast.fun/privacy-policy",
  },
  robots: {
    index: true,
    follow: true,
  },
  openGraph: {
    title: "Privacy Policy — MCast.fun",
    description:
      "Official Privacy Policy for MCast LiveStatus. Learn how we protect creator data and comply with Google API Services User Data Policy.",
    url: "https://mcast.fun/privacy-policy",
    type: "website",
  },
};

export default function PrivacyPolicyPage() {
  const lastUpdated = "October 16, 2026";

  return (
    <div className="min-h-screen bg-bg-primary text-text-primary flex flex-col selection:bg-accent selection:text-black">
      <Header />

      <main className="flex-1 py-12 md:py-16 px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-4xl space-y-10">
          {/* Top Breadcrumb & Page Heading */}
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
                  <Shield size={12} className="text-accent" />
                  Official Legal Document
                </span>
                <Link
                  href="/terms-of-service"
                  className="inline-flex items-center gap-1.5 rounded-full border border-border-accent bg-accent/10 px-3 py-1 text-xs text-accent hover:bg-accent/20 transition-colors"
                >
                  View Terms of Service &rarr;
                </Link>
              </div>
            </div>

            <h1 className="text-3xl sm:text-4xl md:text-5xl font-extrabold tracking-tight text-white">
              Privacy Policy
            </h1>
            <p className="mt-3 text-sm text-text-secondary">
              Effective Date: {lastUpdated} • Last Updated: {lastUpdated}
            </p>
            <p className="mt-4 text-sm sm:text-base text-text-secondary leading-relaxed">
              This Privacy Policy explains how <strong className="text-white">MCast</strong> (&quot;MCast&quot;, &quot;we&quot;, &quot;our&quot;, or &quot;us&quot;) collects, uses, encrypts, and handles your information when you visit <code className="text-accent bg-bg-secondary px-1.5 py-0.5 rounded text-xs">https://mcast.fun</code>, interact with the MCast centralized backend services, or use the MCast LiveStatus Minecraft plugin.
            </p>
          </div>

          {/* Quick Summary Card */}
          <div className="rounded-2xl border border-accent/30 bg-bg-secondary p-6 sm:p-8 relative overflow-hidden shadow-xl">
            <div className="absolute -top-12 -right-12 w-44 h-44 bg-accent/10 rounded-full blur-3xl pointer-events-none" />
            <h2 className="text-lg font-bold text-accent flex items-center gap-2">
              <CheckCircle2 size={18} className="text-accent" />
              Privacy Highlights &amp; Our Commitments
            </h2>
            <ul className="mt-4 space-y-2.5 text-xs sm:text-sm text-text-secondary">
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">•</span>
                <span><strong className="text-white">Read-Only YouTube Access:</strong> We strictly request read-only access (<code className="text-accent">youtube.readonly</code>) solely to detect active Minecraft live broadcasts.</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">•</span>
                <span><strong className="text-white">AES-256-GCM Encryption:</strong> Google OAuth refresh tokens are encrypted at rest with authenticated 256-bit encryption before storage.</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">•</span>
                <span><strong className="text-white">Zero Token Exposure to Server Admins:</strong> Minecraft server administrators never receive or access your Google tokens or client credentials.</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">•</span>
                <span><strong className="text-white">No Ads or Data Resale:</strong> We never sell, rent, or transfer your personal or YouTube data to advertising networks or data brokers.</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">•</span>
                <span><strong className="text-white">Complete User Control:</strong> You can disconnect your YouTube channel at any time in Minecraft (<code className="text-accent">/link youtube unlink</code>) or via Google Security settings.</span>
              </li>
            </ul>
          </div>

          {/* 1. Overview and Description of MCast */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">1. Overview &amp; Description of MCast</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              MCast is a centralized platform connecting Minecraft servers with YouTube Live broadcasting. The ecosystem consists of:
            </p>
            <ul className="list-disc list-inside space-y-2 text-sm text-text-secondary pl-2">
              <li><strong className="text-white">The Web Portal:</strong> Hosted at <code className="text-accent">https://mcast.fun</code>, offering creator linking flows and public documentation.</li>
              <li><strong className="text-white">The Central Backend API:</strong> Hosted at <code className="text-accent">https://api.mcast.fun</code>, managing secure Google OAuth 2.0 token exchanges, cryptographic CSRF checks, and batch live-stream status checks.</li>
              <li><strong className="text-white">The Minecraft Plugin:</strong> A Paper 1.21+ server plugin (LiveStatus) that renders a non-destructive <span className="text-red-400 font-bold bg-red-950/60 px-1 py-0.5 rounded border border-red-500/30 text-xs">LIVE</span> indicator on player nametags and sends in-game chat broadcasts when an authorized streamer goes live.</li>
            </ul>
            <p className="text-sm text-text-secondary leading-relaxed">
              Our architecture centralizes OAuth credentials so individual server owners never need to set up Google Cloud projects, manage API keys, or handle sensitive authentication tokens.
            </p>
          </section>

          {/* 2. Information We Collect */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">2. Information We Collect</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              We practice data minimization and collect only information strictly necessary to provide live stream synchronization:
            </p>

            <div className="space-y-4 mt-2">
              <div className="border border-border/80 rounded-lg p-4 bg-bg-primary/50">
                <h3 className="text-sm font-semibold text-white">A. Minecraft Account Data</h3>
                <p className="text-xs text-text-secondary mt-1 leading-relaxed">
                  When a server links your streamer profile, we store your Minecraft Universally Unique Identifier (UUID) and in-game username (e.g. <code className="text-white">PokeHowlet</code>) to associate your game character with your YouTube channel.
                </p>
              </div>

              <div className="border border-border/80 rounded-lg p-4 bg-bg-primary/50">
                <h3 className="text-sm font-semibold text-white">B. Public YouTube Channel Metadata</h3>
                <p className="text-xs text-text-secondary mt-1 leading-relaxed">
                  Upon completing the Google OAuth flow, we query the YouTube Data API for your public channel profile:
                </p>
                <ul className="list-disc list-inside space-y-1 text-xs text-text-secondary mt-2 pl-2">
                  <li>YouTube Channel ID (e.g. <code className="text-white">UC...</code>)</li>
                  <li>Channel Display Title</li>
                  <li>Custom URL or YouTube Handle (e.g. <code className="text-white">@YourChannel</code>)</li>
                  <li>Public channel avatar image URL (for display on your linking success and dashboard cards)</li>
                </ul>
              </div>

              <div className="border border-border/80 rounded-lg p-4 bg-bg-primary/50">
                <h3 className="text-sm font-semibold text-white">C. Google OAuth Credentials (Encrypted)</h3>
                <p className="text-xs text-text-secondary mt-1 leading-relaxed">
                  We securely store an encrypted OAuth 2.0 refresh token received from Google during channel linking. This allows our backend to refresh short-lived access tokens to query your live broadcast status automatically without requiring you to re-authenticate repeatedly.
                </p>
              </div>

              <div className="border border-border/80 rounded-lg p-4 bg-bg-primary/50">
                <h3 className="text-sm font-semibold text-white">D. Stream Broadcast Status &amp; Cache Data</h3>
                <p className="text-xs text-text-secondary mt-1 leading-relaxed">
                  While your channel is linked, our polling service checks whether you have an active live broadcast. We temporarily cache:
                </p>
                <ul className="list-disc list-inside space-y-1 text-xs text-text-secondary mt-2 pl-2">
                  <li>Broadcast status (<code className="text-accent">LIVE_MINECRAFT</code>, <code className="text-accent">LIVE_NOT_MINECRAFT</code>, <code className="text-accent">OFFLINE</code>, or <code className="text-accent">UNKNOWN</code>)</li>
                  <li>Active YouTube Video ID and watch URL</li>
                  <li>Broadcast title (used for in-game chat announcements)</li>
                  <li>Timestamp of last status check (cache TTL: 30–60 seconds)</li>
                </ul>
              </div>

              <div className="border border-border/80 rounded-lg p-4 bg-bg-primary/50">
                <h3 className="text-sm font-semibold text-white">E. Single-Use Link Session Codes</h3>
                <p className="text-xs text-text-secondary mt-1 leading-relaxed">
                  When you initiate <code className="text-accent">/link youtube</code> in-game, a cryptographically random, short-lived session code is created with a strict 5-minute time-to-live (TTL). Once consumed or expired, the session is invalidated.
                </p>
              </div>

              <div className="border border-border/80 rounded-lg p-4 bg-bg-primary/50">
                <h3 className="text-sm font-semibold text-white">F. Server Registration Metadata</h3>
                <p className="text-xs text-text-secondary mt-1 leading-relaxed">
                  Participating Minecraft servers register with a random server ID, server name, plugin version, and salted secret hash (<code className="text-white">HMAC-SHA256</code>) used solely for server-to-backend authentication.
                </p>
              </div>
            </div>

            <div className="rounded-lg border border-red-500/30 bg-red-950/20 p-4 mt-4">
              <h3 className="text-sm font-bold text-red-400 flex items-center gap-1.5">
                <AlertTriangle size={16} /> What MCast NEVER Collects or Stores
              </h3>
              <p className="text-xs text-text-secondary mt-1 leading-relaxed">
                We <strong className="text-white">never</strong> collect, view, or store:
              </p>
              <ul className="list-disc list-inside space-y-1 text-xs text-text-secondary mt-1 pl-2">
                <li>Your Google password or login credentials</li>
                <li>Your Gmail emails, contacts, calendar, or Google Drive files</li>
                <li>Your personal search or YouTube watch history</li>
                <li>Your private, unlisted, or members-only video files</li>
                <li>Your payment methods, credit card details, or banking information</li>
              </ul>
            </div>
          </section>

          {/* 3. Google OAuth & YouTube Account Connections */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">3. Google OAuth Authentication &amp; YouTube Account Connections</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              MCast uses standard Google OAuth 2.0 authorization code flows:
            </p>
            <ol className="list-decimal list-inside space-y-2 text-sm text-text-secondary pl-2">
              <li>
                <strong className="text-white">Initiation in Minecraft:</strong> An authorized player types <code className="text-accent">/link youtube</code>. The plugin requests a temporary link session from <code className="text-accent">https://api.mcast.fun</code>.
              </li>
              <li>
                <strong className="text-white">Cryptographic State Binding:</strong> When the player visits the linking URL (<code className="text-accent">https://mcast.fun/link/&lt;code&gt;</code>) and clicks &quot;Connect with Google &amp; YouTube&quot;, our backend signs a tamper-proof <code className="text-white">HMAC-SHA256</code> state token binding the browser session to that specific Minecraft player UUID.
              </li>
              <li>
                <strong className="text-white">Google Consent Screen:</strong> The player is redirected to Google&apos;s official OAuth consent screen (<code className="text-white">accounts.google.com</code>) where they inspect the requested read-only permissions and grant access.
              </li>
              <li>
                <strong className="text-white">Direct Server-Side Exchange:</strong> Google redirects back to our central backend callback (<code className="text-accent">https://api.mcast.fun/v1/oauth/callback</code>). Our backend validates the signed state token, exchanges the authorization code directly with Google for tokens, encrypts the refresh token, and maps the YouTube channel to the Minecraft UUID.
              </li>
              <li>
                <strong className="text-white">Confirmation:</strong> The player is redirected to the confirmation page on <code className="text-accent">mcast.fun</code>, and their in-game server confirms the link in chat.
              </li>
            </ol>
            <p className="text-sm text-text-secondary leading-relaxed bg-bg-primary/50 border border-border p-4 rounded-lg">
              <strong className="text-white">Security Isolation:</strong> At no point during or after this flow does the Minecraft Paper server or its administrators receive, touch, or process your Google tokens. All OAuth exchanges and token storage occur strictly on our hardened central backend.
            </p>
          </section>

          {/* 4. Google & YouTube API Data Requested */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">4. Google &amp; YouTube API Data Requested</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              We request strictly the minimum OAuth scope required to operate our service:
            </p>

            <div className="border border-border rounded-lg overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-bg-primary border-b border-border text-white">
                  <tr>
                    <th className="p-3">Scope URI</th>
                    <th className="p-3">Permission Level</th>
                    <th className="p-3">Exact Purpose</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border text-text-secondary">
                  <tr>
                    <td className="p-3 font-mono text-accent text-xs">
                      https://www.googleapis.com/auth/youtube.readonly
                    </td>
                    <td className="p-3 font-semibold text-white">Read-Only</td>
                    <td className="p-3">
                      Allows MCast to view your YouTube channel profile and query whether your channel has an active live stream. We cannot modify, delete, upload, or manage any videos or comments.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <p className="text-sm text-text-secondary leading-relaxed mt-2">
              Our backend communicates with the following specific YouTube Data API v3 endpoints:
            </p>
            <ul className="list-disc list-inside space-y-1.5 text-xs sm:text-sm text-text-secondary pl-2">
              <li>
                <code className="text-white font-mono text-xs">GET /youtube/v3/channels?part=snippet&amp;mine=true</code>: Used only once upon initial linking to retrieve your channel ID, title, handle, and avatar.
              </li>
              <li>
                <code className="text-white font-mono text-xs">GET /youtube/v3/liveBroadcasts?part=snippet,status</code>: Used during scheduled polling to check if your broadcast status is actively streaming.
              </li>
              <li>
                <code className="text-white font-mono text-xs">GET /youtube/v3/videos?part=snippet,topicDetails</code>: Used to inspect the video category (<code className="text-white">categoryId = 20</code>, Gaming) and topic metadata to ensure the stream is Minecraft gameplay before enabling the badge.
              </li>
            </ul>
          </section>

          {/* 5. Google API Services User Data Policy Compliance */}
          <section className="space-y-4 rounded-xl border border-accent/40 bg-bg-secondary p-6 sm:p-8 relative">
            <div className="flex items-center gap-2">
              <Shield className="text-accent" size={22} />
              <h2 className="text-xl font-bold text-white">
                5. Google API Services User Data Policy Compliance
              </h2>
            </div>

            <div className="rounded-lg border border-accent/30 bg-accent/5 p-4 text-sm text-white leading-relaxed">
              <strong>Mandatory Compliance Disclosure:</strong>
              <p className="mt-1 text-text-secondary text-xs sm:text-sm">
                MCast&apos;s use and transfer of information received from Google APIs to any other app will adhere to the{" "}
                <a
                  href="https://developers.google.com/terms/api-services-user-data-policy"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-accent underline font-semibold hover:text-accent-hover inline-flex items-center gap-1"
                >
                  Google API Services User Data Policy
                  <ExternalLink size={12} />
                </a>
                , including the <strong>Limited Use</strong> requirements.
              </p>
            </div>

            <p className="text-sm text-text-secondary leading-relaxed">
              In accordance with Google&apos;s Limited Use requirements:
            </p>

            <div className="grid gap-3 sm:grid-cols-2 text-xs sm:text-sm">
              <div className="rounded-lg border border-border bg-bg-primary/50 p-4 space-y-1.5">
                <h3 className="font-bold text-white flex items-center gap-1.5">
                  <CheckCircle2 size={16} className="text-accent" /> Specific Feature Use Only
                </h3>
                <p className="text-text-secondary text-xs leading-relaxed">
                  We use Google user data solely to provide and improve user-facing features—specifically displaying your in-game live nametag and chat broadcast on your authorized Minecraft servers.
                </p>
              </div>

              <div className="rounded-lg border border-border bg-bg-primary/50 p-4 space-y-1.5">
                <h3 className="font-bold text-white flex items-center gap-1.5">
                  <CheckCircle2 size={16} className="text-accent" /> No Transfer or Resale
                </h3>
                <p className="text-text-secondary text-xs leading-relaxed">
                  We do not transfer or sell user data to third parties, advertising platforms, data brokers, or information resellers under any circumstances.
                </p>
              </div>

              <div className="rounded-lg border border-border bg-bg-primary/50 p-4 space-y-1.5">
                <h3 className="font-bold text-white flex items-center gap-1.5">
                  <CheckCircle2 size={16} className="text-accent" /> No Advertising Use
                </h3>
                <p className="text-text-secondary text-xs leading-relaxed">
                  We do not use or transfer user data for serving personalized, retargeted, or interest-based advertisements.
                </p>
              </div>

              <div className="rounded-lg border border-border bg-bg-primary/50 p-4 space-y-1.5">
                <h3 className="font-bold text-white flex items-center gap-1.5">
                  <CheckCircle2 size={16} className="text-accent" /> No AI/ML Model Training
                </h3>
                <p className="text-text-secondary text-xs leading-relaxed">
                  We do not use data obtained through Google Workspace or YouTube APIs to train generalized artificial intelligence (AI) or machine learning (ML) models.
                </p>
              </div>
            </div>

            <div className="rounded-lg border border-border bg-bg-primary/50 p-4 text-xs sm:text-sm text-text-secondary leading-relaxed">
              <strong className="text-white">Strict Limits on Human Inspection:</strong>
              <p className="mt-1">
                No human beings at MCast are permitted to read or inspect your Google user data, unless:
              </p>
              <ul className="list-disc list-inside space-y-1 mt-1 pl-2">
                <li>You have given your explicit affirmative consent to inspect a specific issue (e.g. debugging a failed live detection);</li>
                <li>It is necessary for security purposes (such as investigating a bug or system abuse); or</li>
                <li>It is required to comply with applicable laws, regulations, or legal processes.</li>
              </ul>
            </div>
          </section>

          {/* 6. Purpose of Data Access and Processing */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">6. Purposes for Which Data Is Accessed and Processed</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              User data is processed exclusively for the following operational purposes:
            </p>
            <ul className="list-disc list-inside space-y-2 text-sm text-text-secondary pl-2">
              <li>
                <strong className="text-white">Live Broadcast Verification:</strong> Checking whether your channel is streaming live and validating whether the broadcast is categorized as Minecraft gaming.
              </li>
              <li>
                <strong className="text-white">Nametag Synchronization:</strong> Informing your connected Minecraft server to append or remove the non-destructive <code className="text-red-400">LIVE</code> suffix to your player nametag in real time.
              </li>
              <li>
                <strong className="text-white">Automated Chat Announcements:</strong> Triggering fair in-game chat broadcasts on your server when you start streaming, subject to server-configured cooldowns.
              </li>
              <li>
                <strong className="text-white">Streamer Dashboard:</strong> Allowing you to view your connected channel info, link status, and stream diagnostics on <code className="text-accent">https://mcast.fun/dashboard</code>.
              </li>
              <li>
                <strong className="text-white">System Integrity &amp; Abuse Prevention:</strong> Verifying cryptographic session tokens, rate limiting API calls, and preventing unauthorized player impersonation.
              </li>
            </ul>
          </section>

          {/* 7. Token Handling, Storage, Security & Encryption */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <div className="flex items-center gap-2">
              <Lock className="text-accent" size={20} />
              <h2 className="text-xl font-bold text-accent">7. Token Handling, Storage &amp; Encryption at Rest</h2>
            </div>
            <p className="text-sm text-text-secondary leading-relaxed">
              MCast enforces strict cryptographic controls to safeguard tokens throughout their lifecycle:
            </p>

            <div className="space-y-3 mt-2 text-xs sm:text-sm">
              <div className="border border-border/80 rounded-lg p-4 bg-bg-primary/50">
                <h3 className="font-semibold text-white flex items-center gap-1.5">
                  <Key size={14} className="text-accent" /> AES-256-GCM Token Encryption at Rest
                </h3>
                <p className="text-text-secondary mt-1 leading-relaxed">
                  Every Google OAuth refresh token is encrypted at rest using industry-standard <strong className="text-white">AES-256-GCM</strong> (Galois/Counter Mode). For each encrypted token record, our backend generates:
                </p>
                <ul className="list-disc list-inside space-y-1 mt-1.5 pl-2 text-text-secondary">
                  <li>A cryptographically unique 12-byte initialization vector (<code className="text-white">token_iv</code>);</li>
                  <li>A 16-byte authentication tag (<code className="text-white">token_auth_tag</code>) ensuring tamper detection;</li>
                  <li>An isolated 256-bit encryption key held strictly in secure production environment variables.</li>
                </ul>
              </div>

              <div className="border border-border/80 rounded-lg p-4 bg-bg-primary/50">
                <h3 className="font-semibold text-white">Volatile In-Memory Access Tokens</h3>
                <p className="text-text-secondary mt-1 leading-relaxed">
                  Short-lived Google access tokens (which expire after 1 hour) are stored exclusively in volatile, in-memory process cache (<code className="text-white">tokenCache</code>). They are never written to disk, persistent databases, or public storage.
                </p>
              </div>

              <div className="border border-border/80 rounded-lg p-4 bg-bg-primary/50">
                <h3 className="font-semibold text-white">Automated Log Sanitization</h3>
                <p className="text-text-secondary mt-1 leading-relaxed">
                  Our logging pipeline implements rigorous string sanitizers (<code className="text-white">sanitizeApiError</code>) that automatically scrub and redact Google access tokens (<code className="text-white">ya29...</code>), refresh tokens, client secrets, and authorization codes before any log entries are written.
                </p>
              </div>

              <div className="border border-border/80 rounded-lg p-4 bg-bg-primary/50">
                <h3 className="font-semibold text-white">Secure Database Infrastructure</h3>
                <p className="text-text-secondary mt-1 leading-relaxed">
                  Our database is hosted with Supabase on Amazon Web Services (AWS) using TLS 1.3 transit encryption, encrypted PostgreSQL volume storage, and strict IP access controls.
                </p>
              </div>
            </div>
          </section>

          {/* 8. Data Retention and Deletion */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">8. Data Retention &amp; Automatic Expiration</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              We retain user data only for the period necessary to deliver our services:
            </p>

            <div className="border border-border rounded-lg overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-bg-primary border-b border-border text-white">
                  <tr>
                    <th className="p-3">Data Element</th>
                    <th className="p-3">Retention Period</th>
                    <th className="p-3">Purge Mechanism</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border text-text-secondary">
                  <tr>
                    <td className="p-3 font-semibold text-white">Link Session Codes</td>
                    <td className="p-3">5 Minutes</td>
                    <td className="p-3">Automatically invalidated upon consumption or after 300 seconds.</td>
                  </tr>
                  <tr>
                    <td className="p-3 font-semibold text-white">OAuth State CSRF Nonce</td>
                    <td className="p-3">10 Minutes</td>
                    <td className="p-3">Cryptographically signed with 10-minute validity timestamp.</td>
                  </tr>
                  <tr>
                    <td className="p-3 font-semibold text-white">Live Stream Cache</td>
                    <td className="p-3">30–60 Seconds</td>
                    <td className="p-3">Short-lived polling cache automatically updated every cycle.</td>
                  </tr>
                  <tr>
                    <td className="p-3 font-semibold text-white">Encrypted Refresh Token</td>
                    <td className="p-3">Duration of active link</td>
                    <td className="p-3">Purged upon unlinking or token revocation.</td>
                  </tr>
                  <tr>
                    <td className="p-3 font-semibold text-white">Minecraft Player UUID Mapping</td>
                    <td className="p-3">Duration of active link</td>
                    <td className="p-3">Deleted immediately when account is unlinked.</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          {/* 9. Third-Party Services and Data Sharing */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">9. Third-Party Services &amp; Subprocessors</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              MCast partners only with essential, trusted cloud and infrastructure providers to operate the service:
            </p>
            <ul className="list-disc list-inside space-y-2 text-sm text-text-secondary pl-2">
              <li>
                <strong className="text-white">Google LLC / YouTube API Services:</strong> Identity authentication, OAuth token exchange, and broadcast status queries. Governed by the{" "}
                <a
                  href="https://policies.google.com/privacy"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-accent underline"
                >
                  Google Privacy Policy
                </a>{" "}
                and{" "}
                <a
                  href="https://www.youtube.com/t/terms"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-accent underline"
                >
                  YouTube Terms of Service
                </a>.
              </li>
              <li>
                <strong className="text-white">Supabase / Amazon Web Services (AWS):</strong> Secure PostgreSQL database hosting with encryption at rest and connection pooling.
              </li>
              <li>
                <strong className="text-white">Render Services, Inc.:</strong> Hosting for the central MCast backend microservice (<code className="text-white">api.mcast.fun</code>).
              </li>
              <li>
                <strong className="text-white">Vercel Inc.:</strong> Hosting and edge delivery for the web portal at <code className="text-white">mcast.fun</code>.
              </li>
            </ul>
            <p className="text-sm text-text-secondary leading-relaxed">
              We do not sell, rent, license, or disclose your information to any third party for marketing, promotional, or data brokering purposes.
            </p>
          </section>

          {/* 10. Cookies, Logging & Analytics */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">10. Cookies, Local Storage &amp; Logging</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              <strong className="text-white">No Tracking Cookies:</strong> The MCast website does not use tracking cookies, retargeting pixels, or third-party behavioral advertising scripts.
            </p>
            <p className="text-sm text-text-secondary leading-relaxed">
              <strong className="text-white">Technical Logging:</strong> Our cloud hosting providers record standard HTTP access logs (including visitor IP address, user-agent string, HTTP status code, and request timestamp) for security monitoring, DDoS mitigation, and operational diagnostics. These logs are retained for standard operational periods and purged automatically.
            </p>
          </section>

          {/* 11. Disconnecting Accounts & Revoking Permissions */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <div className="flex items-center gap-2">
              <RefreshCw className="text-accent" size={20} />
              <h2 className="text-xl font-bold text-accent">
                11. How to Disconnect Accounts &amp; Revoke Permissions
              </h2>
            </div>
            <p className="text-sm text-text-secondary leading-relaxed">
              You maintain total control over your YouTube channel association and can disconnect at any time using any of the following methods:
            </p>

            <div className="space-y-4 mt-2">
              <div className="rounded-lg border border-border bg-bg-primary/50 p-4">
                <h3 className="text-sm font-bold text-white">Method 1: In-Game Disconnect (Instant)</h3>
                <p className="text-xs text-text-secondary mt-1 leading-relaxed">
                  Log into any Minecraft server where your account is linked and run:
                </p>
                <p className="mt-2 font-mono text-accent bg-bg-secondary px-3 py-2 rounded border border-border text-xs inline-block">
                  /link youtube unlink
                </p>
                <p className="text-xs text-text-secondary mt-2">
                  This command immediately deletes the player link record, removes the nametag badge, and halts live status polling for your channel.
                </p>
              </div>

              <div className="rounded-lg border border-border bg-bg-primary/50 p-4">
                <h3 className="text-sm font-bold text-white">Method 2: Google Account Security Settings (Global Revocation)</h3>
                <p className="text-xs text-text-secondary mt-1 leading-relaxed">
                  You can revoke MCast&apos;s OAuth access directly through your Google account dashboard at any time:
                </p>
                <ol className="list-decimal list-inside space-y-1 text-xs text-text-secondary mt-2 pl-2">
                  <li>Navigate to <a href="https://myaccount.google.com/permissions" target="_blank" rel="noopener noreferrer" className="text-accent underline">Google Account Third-Party Apps &amp; Services</a> (<code className="text-accent">https://myaccount.google.com/permissions</code>).</li>
                  <li>Select <strong className="text-white">MCast</strong> (or <strong className="text-white">Minecraft Server YouTube Live Connector</strong>).</li>
                  <li>Click <strong className="text-white">&quot;Delete all connections you have with MCast&quot;</strong>.</li>
                </ol>
                <p className="text-xs text-text-secondary mt-2">
                  Once revoked, Google invalidates the refresh token. When our backend attempts its next status check, Google returns an <code className="text-white font-mono">invalid_grant</code> error. Our system immediately flags the token as revoked, ceases all API requests to Google, and removes the in-game LIVE badge.
                </p>
              </div>
            </div>
          </section>

          {/* 12. Data Deletion Requests */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <div className="flex items-center gap-2">
              <Trash2 className="text-accent" size={20} />
              <h2 className="text-xl font-bold text-accent">12. Data Deletion Requests (Right to Erasure)</h2>
            </div>
            <p className="text-sm text-text-secondary leading-relaxed">
              Under GDPR, CCPA, and standard privacy principles, you have the right to request full and permanent deletion of any data associated with your identity.
            </p>
            <p className="text-sm text-text-secondary leading-relaxed">
              To request complete deletion of your data:
            </p>
            <div className="rounded-lg border border-border bg-bg-primary/50 p-4 space-y-2 text-xs sm:text-sm text-text-secondary">
              <p>
                1. Send an email to <a href="mailto:contact@mcast.fun" className="text-accent underline font-semibold">contact@mcast.fun</a> with the subject line <strong className="text-white">&quot;Data Deletion Request&quot;</strong>.
              </p>
              <p>
                2. Include either your <strong className="text-white">Minecraft Player Username / UUID</strong> or your <strong className="text-white">YouTube Channel ID</strong> (or custom handle) so we can locate your record.
              </p>
              <p>
                3. Upon receipt and verification, our team will permanently purge your encrypted refresh token, channel metadata, player associations, and stream cache entries from our database within <strong className="text-white">30 days</strong>, and send you written confirmation.
              </p>
            </div>
          </section>

          {/* 13. Children's Privacy */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">13. Children&apos;s Privacy</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              MCast does not knowingly collect or solicit personal information from children under the age of 13 (or under 16 in the European Economic Area / UK), in accordance with the Children&apos;s Online Privacy Protection Act (COPPA) and GDPR.
            </p>
            <p className="text-sm text-text-secondary leading-relaxed">
              Our services are intended for Minecraft players and content creators who are at least 13 years old. If we learn that we have collected personal data from a child under the applicable age without verifiable parental consent, we will promptly delete that information from our servers. If you believe a child has provided us with their YouTube account or Minecraft information, please notify us immediately at <a href="mailto:contact@mcast.fun" className="text-accent underline">contact@mcast.fun</a>.
            </p>
          </section>

          {/* 14. Changes to This Policy */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <h2 className="text-xl font-bold text-accent">14. Changes to This Privacy Policy</h2>
            <p className="text-sm text-text-secondary leading-relaxed">
              We may revise this Privacy Policy from time to time to reflect operational, technological, or regulatory developments, or updates to Google API policies.
            </p>
            <p className="text-sm text-text-secondary leading-relaxed">
              When updates are made, we will update the &quot;Last Updated&quot; date at the top of this document. For material changes affecting the collection or processing of your information, we will provide prominent notice on <code className="text-accent">https://mcast.fun</code> or in the MCast software documentation. Your continued use of MCast after the effective date of an updated policy signifies your agreement to the updated terms.
            </p>
          </section>

          {/* 15. Contact Information */}
          <section className="space-y-4 rounded-xl border border-border bg-bg-secondary p-6 sm:p-8">
            <div className="flex items-center gap-2">
              <Mail className="text-accent" size={20} />
              <h2 className="text-xl font-bold text-accent">15. Contact Information</h2>
            </div>
            <p className="text-sm text-text-secondary leading-relaxed">
              If you have any questions, concerns, or requests regarding this Privacy Policy or our security and data handling practices, please contact our team:
            </p>
            <div className="mt-3 p-4 rounded-lg border border-border bg-bg-primary/50 text-sm space-y-1.5 text-text-secondary">
              <p><strong className="text-white">Project:</strong> MCast (LiveStatus)</p>
              <p><strong className="text-white">Privacy Inquiries:</strong> <a href="mailto:contact@mcast.fun" className="text-accent underline">contact@mcast.fun</a></p>
              <p><strong className="text-white">Official Website:</strong> <a href="https://mcast.fun" className="text-accent underline">https://mcast.fun</a></p>
              <p><strong className="text-white">Open Source Codebase:</strong> <a href="https://github.com/Rockybhaiff94/MCast" target="_blank" rel="noopener noreferrer" className="text-accent underline">GitHub Repository</a></p>
            </div>
          </section>
        </div>
      </main>

      <Footer />
    </div>
  );
}
