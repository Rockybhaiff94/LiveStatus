'use client';

import React, { useEffect, useState, useCallback, Suspense } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import ConnectedChannelCard, { ChannelInfo, LiveStatusInfo } from '@/components/ConnectedChannelCard';

interface SessionData {
  valid: boolean;
  code?: string;
  server_name?: string;
  player_uuid?: string;
  player_name?: string;
  status?: string;
  connected?: boolean;
  expires_at?: number;
  seconds_remaining?: number;
  channel?: ChannelInfo;
  live_status?: LiveStatusInfo;
  error?: string;
}

function PlayerLinkContent() {
  const params = useParams();
  const code = params?.code as string;

  const [session, setSession] = useState<SessionData | null>(null);
  const [loading, setLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [timeLeft, setTimeLeft] = useState<number>(300);

  const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'https://api.mcast.fun';

  const verifyCode = useCallback(async (isManualRefresh = false) => {
    if (!code) return;
    if (isManualRefresh) setIsRefreshing(true);

    try {
      const res = await fetch(`${apiUrl}/v1/link-sessions/verify?code=${encodeURIComponent(code)}`, {
        cache: 'no-store',
      });
      const data = await res.json();
      setSession(data);
      if (data.seconds_remaining) {
        setTimeLeft(data.seconds_remaining);
      }
    } catch {
      setSession({
        valid: false,
        error: 'Unable to reach the MCast verification service. Please try again shortly.',
      });
    } finally {
      setLoading(false);
      setIsRefreshing(false);
    }
  }, [code, apiUrl]);

  useEffect(() => {
    verifyCode();
  }, [verifyCode]);

  // Countdown timer for pending sessions
  useEffect(() => {
    if (!session?.valid || session.status === 'completed' || timeLeft <= 0) return;

    const timer = setInterval(() => {
      setTimeLeft(prev => {
        if (prev <= 1) {
          clearInterval(timer);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [session, timeLeft]);

  // If already connected, auto-refresh live status every 30s
  useEffect(() => {
    if (session?.status !== 'completed') return;

    const interval = setInterval(() => {
      verifyCode(false);
    }, 30_000);

    return () => clearInterval(interval);
  }, [session?.status, verifyCode]);

  const minutes = Math.floor(timeLeft / 60);
  const seconds = timeLeft % 60;
  const formattedTime = `${minutes}:${seconds < 10 ? '0' : ''}${seconds}`;

  if (loading) {
    return (
      <div className="w-full max-w-lg bg-[#111111] border border-[#262626] rounded-2xl p-6 sm:p-8 shadow-2xl text-center py-12">
        <div className="w-12 h-12 border-4 border-[#262626] border-t-[#FF7A00] rounded-full animate-spin mx-auto mb-4"></div>
        <p className="text-[#A3A3A3] text-sm">Verifying linking session...</p>
      </div>
    );
  }

  // If session is already completed, render Connected Channel Dashboard
  if (session?.status === 'completed' && session.channel) {
    return (
      <div className="w-full max-w-xl flex flex-col items-center">
        <div className="w-full bg-[#111111] border border-[#262626] rounded-2xl p-6 sm:p-8 text-center mb-6 shadow-2xl relative overflow-hidden">
          <div className="w-14 h-14 rounded-full bg-green-950/40 border border-green-500/30 text-green-400 flex items-center justify-center text-2xl mx-auto mb-3">
            ✓
          </div>
          <h1 className="text-2xl font-bold text-white mb-1">YouTube Channel Connected</h1>
          <p className="text-sm text-[#A3A3A3]">
            This session has already been authorized. Live status is synchronizing below.
          </p>
        </div>

        <ConnectedChannelCard
          channel={session.channel}
          liveStatus={session.live_status || null}
          playerName={session.player_name}
          serverName={session.server_name}
          onRefresh={() => verifyCode(true)}
          isRefreshing={isRefreshing}
        />
      </div>
    );
  }

  // If session expired or invalid
  if (!session?.valid || (session.status !== 'completed' && timeLeft <= 0)) {
    return (
      <div className="w-full max-w-lg bg-[#111111] border border-[#262626] rounded-2xl p-6 sm:p-8 shadow-2xl text-center py-6 relative overflow-hidden">
        <div className="w-16 h-16 bg-red-950/40 border border-red-500/30 text-red-400 rounded-full flex items-center justify-center text-2xl mx-auto mb-4">
          ✕
        </div>
        <h1 className="text-xl font-bold mb-2 text-white">Linking Session Expired or Invalid</h1>
        <p className="text-sm text-[#A3A3A3] mb-6 leading-relaxed">
          {session?.error || 'This one-time linking link has expired or has already been used.'}
        </p>
        <div className="bg-[#080808] border border-[#262626] rounded-xl p-4 text-xs text-[#A3A3A3] mb-6 text-left space-y-1">
          <p className="font-semibold text-white">How to get a fresh link:</p>
          <p>1. Open Minecraft and connect to your server.</p>
          <p>2. Type <code className="text-[#FF7A00] bg-[#1a1a1a] px-1.5 py-0.5 rounded">/link youtube</code> in chat.</p>
          <p>3. Click the new link generated in in-game chat.</p>
        </div>
        <Link
          href="/"
          className="inline-block bg-[#1f1f1f] hover:bg-[#2a2a2a] text-white px-5 py-2.5 rounded-lg text-sm font-medium transition-colors"
        >
          Return to MCast Homepage
        </Link>
      </div>
    );
  }

  // Active Pending Session: Show "Connect with Google & YouTube"
  return (
    <div className="w-full max-w-lg bg-[#111111] border border-[#262626] rounded-2xl p-6 sm:p-8 shadow-2xl relative overflow-hidden">
      <div className="absolute -top-20 -right-20 w-48 h-48 bg-[#FF7A00]/10 rounded-full blur-3xl pointer-events-none"></div>

      <div className="text-center mb-6">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#FF7A00]/10 border border-[#FF7A00]/30 text-[#FF7A00] text-xs font-semibold mb-3">
          <span>Single-Use Link</span>
          <span>•</span>
          <span>Expires in {formattedTime}</span>
        </div>
        <h1 className="text-2xl font-bold text-white tracking-tight">Connect YouTube Channel</h1>
        <p className="text-sm text-[#A3A3A3] mt-1">
          Authorize your YouTube account to synchronize your live status in Minecraft.
        </p>
      </div>

      {/* Player and Server Summary */}
      <div className="bg-[#080808] border border-[#262626] rounded-xl p-4 mb-6 space-y-3">
        <div className="flex justify-between items-center text-sm">
          <span className="text-[#A3A3A3]">Minecraft Player:</span>
          <span className="font-semibold text-white flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-green-500"></span>
            {session.player_name}
          </span>
        </div>
        <div className="h-px bg-[#1f1f1f]"></div>
        <div className="flex justify-between items-center text-sm">
          <span className="text-[#A3A3A3]">Minecraft Server:</span>
          <span className="font-medium text-white">{session.server_name}</span>
        </div>
        <div className="h-px bg-[#1f1f1f]"></div>
        <div className="flex justify-between items-center text-xs">
          <span className="text-[#A3A3A3]">Verification Code:</span>
          <span className="font-mono text-[#FF7A00] bg-[#1a1a1a] px-2 py-0.5 rounded border border-[#333]">
            {session.code}
          </span>
        </div>
      </div>

      {/* Security & Permissions Callout */}
      <div className="bg-[#161616] border border-[#262626] rounded-xl p-4 mb-6 text-xs text-[#A3A3A3] space-y-2">
        <div className="flex items-center gap-1.5 font-semibold text-white text-[13px]">
          <span className="text-[#FF7A00]">🔒</span> Permissions & Privacy Guarantee
        </div>
        <p>
          MCast requests <strong className="text-white">read-only</strong> YouTube access (<code className="text-[#FF7A00]">youtube.readonly</code>) solely to detect when you are actively broadcasting Minecraft.
        </p>
        <p>
          • We <strong className="text-white">never</strong> post, comment, or manage your videos.
          <br />
          • We <strong className="text-white">never</strong> access Google passwords or private personal data.
          <br />
          • Refresh tokens are encrypted with AES-256-GCM and never shared with Minecraft server owners.
        </p>
      </div>

      {/* Google OAuth Action Button */}
      <a
        href={`${apiUrl}/v1/oauth/authorize?code=${encodeURIComponent(session.code || '')}`}
        className="w-full flex items-center justify-center gap-3 bg-[#FF7A00] hover:bg-[#FF9500] text-black font-bold py-3.5 px-6 rounded-xl transition-all duration-200 shadow-lg shadow-[#FF7A00]/20 text-center cursor-pointer"
      >
        <svg className="w-5 h-5" viewBox="0 0 24 24">
          <path
            fill="currentColor"
            d="M21.35 11.1h-9.17v2.98h5.27c-.23 1.25-.93 2.31-1.98 3.02v2.51h3.2c1.87-1.72 2.95-4.26 2.95-7.38 0-.71-.06-1.4-.27-2.13z"
          />
          <path
            fill="currentColor"
            d="M12.18 21.5c2.67 0 4.92-.89 6.56-2.4l-3.2-2.51c-.89.6-2.03.95-3.36.95-2.58 0-4.76-1.74-5.54-4.08H3.32v2.58c1.64 3.25 5.01 5.46 8.86 5.46z"
          />
          <path
            fill="currentColor"
            d="M6.64 13.46c-.2-.6-.31-1.24-.31-1.91 0-.67.11-1.31.31-1.91V7.06H3.32C2.66 8.38 2.28 9.87 2.28 11.55s.38 3.17 1.04 4.49l3.32-2.58z"
          />
          <path
            fill="currentColor"
            d="M12.18 5.42c1.45 0 2.76.5 3.79 1.48l2.84-2.84C17.09 2.41 14.84 1.5 12.18 1.5c-3.85 0-7.22 2.21-8.86 5.46l3.32 2.58c.78-2.34 2.96-4.08 5.54-4.08z"
          />
        </svg>
        <span>Connect with Google & YouTube</span>
      </a>

      <p className="text-[11px] text-center text-[#737373] mt-4 leading-relaxed">
        By continuing, you agree to the{" "}
        <Link href="/terms-of-service" target="_blank" className="text-[#FF7A00] hover:underline">
          MCast Terms of Service
        </Link>
        ,{" "}
        <Link href="/privacy-policy" target="_blank" className="text-[#FF7A00] hover:underline">
          Privacy Policy
        </Link>
        , and{" "}
        <a
          href="https://www.youtube.com/t/terms"
          target="_blank"
          rel="noopener noreferrer"
          className="text-[#FF7A00] hover:underline"
        >
          YouTube Terms of Service
        </a>.
      </p>
    </div>
  );
}

export default function PlayerLinkPage() {
  return (
    <div className="min-h-screen bg-[#080808] text-white flex flex-col justify-between selection:bg-[#FF7A00] selection:text-black">
      {/* Top Brand Header */}
      <header className="border-b border-[#262626] bg-[#080808]/80 backdrop-blur-md px-6 py-4 flex items-center justify-between">
        <Link href="/" className="flex items-center gap-2 group">
          <div className="w-8 h-8 rounded-lg bg-[#FF7A00] flex items-center justify-center font-bold text-black text-lg group-hover:bg-[#FF9500] transition-colors">
            M
          </div>
          <span className="font-bold text-lg tracking-wider">
            MCAST<span className="text-[#FF7A00]">.FUN</span>
          </span>
        </Link>
        <div className="flex items-center gap-2 text-xs text-[#A3A3A3] bg-[#111111] px-3 py-1.5 rounded-full border border-[#262626]">
          <span className="w-2 h-2 rounded-full bg-[#FF7A00] animate-pulse"></span>
          Centralized OAuth
        </div>
      </header>

      {/* Main Content Area inside Suspense */}
      <main className="flex-1 flex items-center justify-center px-4 py-12">
        <Suspense fallback={<div className="text-[#A3A3A3] text-sm">Loading session details...</div>}>
          <PlayerLinkContent />
        </Suspense>
      </main>

      {/* Footer */}
      <footer className="border-t border-[#262626] bg-[#080808] py-4 text-center text-xs text-[#737373] space-y-1.5">
        <p>MCast LiveStatus • Centralized Minecraft Broadcast Synchronization • mcast.fun</p>
        <div className="flex justify-center items-center gap-4 text-[11px] text-[#A3A3A3]">
          <Link href="/privacy-policy" className="hover:text-[#FF7A00] transition-colors">
            Privacy Policy
          </Link>
          <span>•</span>
          <Link href="/terms-of-service" className="hover:text-[#FF7A00] transition-colors">
            Terms of Service
          </Link>
        </div>
      </footer>
    </div>
  );
}
