'use client';

import React, { useEffect, useState, useCallback, Suspense } from 'react';
import { useParams, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import ConnectedChannelCard, { ChannelInfo, LiveStatusInfo } from '@/components/ConnectedChannelCard';

function SuccessContent() {
  const params = useParams();
  const searchParams = useSearchParams();
  const code = (params?.code as string) || '';

  const initialChannelName = searchParams?.get('channel') || '';
  const initialPlayer = searchParams?.get('player') || '';
  const initialAvatar = searchParams?.get('avatar') || '';
  const initialCustomUrl = searchParams?.get('custom_url') || '';
  const initialChannelId = searchParams?.get('channel_id') || '';

  const [channel, setChannel] = useState<ChannelInfo | null>(() => {
    if (initialChannelId || initialChannelName) {
      return {
        channel_id: initialChannelId || 'connected',
        channel_title: initialChannelName || 'YouTube Channel',
        custom_url: initialCustomUrl || null,
        avatar_url: initialAvatar || null,
        channel_url: initialCustomUrl
          ? `https://www.youtube.com/${initialCustomUrl.startsWith('@') ? initialCustomUrl : '@' + initialCustomUrl}`
          : (initialChannelId ? `https://www.youtube.com/channel/${initialChannelId}` : 'https://www.youtube.com'),
      };
    }
    return null;
  });

  const [liveStatus, setLiveStatus] = useState<LiveStatusInfo | null>(null);
  const [playerName, setPlayerName] = useState<string>(initialPlayer);
  const [serverName, setServerName] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(!channel);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'https://api.mcast.fun';

  const fetchStatus = useCallback(async (isManualRefresh = false) => {
    if (!code) return;
    if (isManualRefresh) setIsRefreshing(true);

    try {
      const res = await fetch(`${apiUrl}/v1/link-sessions/verify?code=${encodeURIComponent(code)}`, {
        cache: 'no-store',
      });
      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.error || 'Failed to fetch status');
      }

      if (data.player_name) setPlayerName(data.player_name);
      if (data.server_name) setServerName(data.server_name);

      if (data.channel) {
        setChannel(data.channel);
      }

      if (data.live_status) {
        setLiveStatus(data.live_status);
      }
      setError(null);
    } catch (err: any) {
      console.warn('[MCast Frontend] Error fetching channel live status:', err.message);
      if (!channel) {
        setError(err.message || 'Unable to connect to verification API');
      }
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [code, apiUrl, channel]);

  useEffect(() => {
    fetchStatus();

    // Sensible 30-second polling interval to auto-refresh live status
    const timer = setInterval(() => {
      fetchStatus(false);
    }, 30_000);

    return () => clearInterval(timer);
  }, [fetchStatus]);

  return (
    <div className="w-full max-w-xl flex flex-col items-center">
      {/* Top Completion Banner */}
      <div className="w-full bg-[#111111] border border-[#262626] rounded-2xl p-6 sm:p-8 text-center mb-6 shadow-2xl relative overflow-hidden">
        <div className="absolute -top-20 -right-20 w-48 h-48 bg-[#FF7A00]/15 rounded-full blur-3xl pointer-events-none"></div>

        <div className="w-16 h-16 rounded-full bg-[#FF7A00]/10 border-2 border-[#FF7A00] text-[#FF7A00] flex items-center justify-center text-2xl mx-auto mb-4 shadow-lg shadow-[#FF7A00]/20">
          ✓
        </div>

        <h1 className="text-2xl sm:text-3xl font-extrabold text-white mb-2 tracking-tight">
          Account Linked Successfully!
        </h1>
        <p className="text-[#A3A3A3] text-sm leading-relaxed max-w-md mx-auto">
          Your YouTube account has been securely associated with your Minecraft streamer profile.
        </p>
      </div>

      {/* Connected Channel & Live Status Display Card */}
      <ConnectedChannelCard
        channel={channel}
        liveStatus={liveStatus}
        playerName={playerName}
        serverName={serverName}
        isLoading={isLoading}
        error={error}
        onRefresh={() => fetchStatus(true)}
        isRefreshing={isRefreshing}
      />

      {/* In-Game Instructions */}
      <div className="w-full bg-[#161616] border border-[#262626] rounded-xl p-4 mt-6 text-xs text-[#A3A3A3] text-left space-y-2">
        <p className="font-bold text-white text-sm">🎮 What happens now?</p>
        <p>
          1. <strong className="text-white">Return to Minecraft:</strong> Your server will confirm the link in chat.
        </p>
        <p>
          2. <strong className="text-white">Start Streaming:</strong> Go live on YouTube with category <strong className="text-white">Gaming (Minecraft)</strong>.
        </p>
        <p>
          3. <strong className="text-white">Automatic LIVE Indicator:</strong> Your nametag and chat prefix will automatically show <span className="text-red-400 font-bold bg-red-950/60 px-1 py-0.5 rounded border border-red-500/30">LIVE</span>!
        </p>
      </div>

      <div className="w-full mt-6 flex gap-3">
        <Link
          href="/"
          className="flex-1 bg-[#1f1f1f] hover:bg-[#2a2a2a] text-white font-medium py-3 px-6 rounded-xl transition-colors text-center text-sm"
        >
          Visit MCast Homepage
        </Link>
      </div>
    </div>
  );
}

export default function LinkSuccessPage() {
  return (
    <div className="min-h-screen bg-[#080808] text-white flex flex-col justify-between selection:bg-[#FF7A00] selection:text-black">
      {/* Header */}
      <header className="border-b border-[#262626] bg-[#080808]/80 backdrop-blur-md px-6 py-4 flex items-center justify-between">
        <Link href="/" className="flex items-center gap-2 group">
          <div className="w-8 h-8 rounded-lg bg-[#FF7A00] flex items-center justify-center font-bold text-black text-lg group-hover:bg-[#FF9500] transition-colors">
            M
          </div>
          <span className="font-bold text-lg tracking-wider">
            MCAST<span className="text-[#FF7A00]">.FUN</span>
          </span>
        </Link>
        <span className="text-xs text-green-400 bg-green-950/40 border border-green-500/30 px-3 py-1 rounded-full font-medium">
          ✓ Connected
        </span>
      </header>

      {/* Main Success Card inside Suspense */}
      <main className="flex-1 flex items-center justify-center px-4 py-12">
        <Suspense fallback={<div className="text-[#A3A3A3] text-sm">Loading confirmation...</div>}>
          <SuccessContent />
        </Suspense>
      </main>

      {/* Footer */}
      <footer className="border-t border-[#262626] bg-[#080808] py-4 text-center text-xs text-[#737373]">
        MCast LiveStatus • Centralized Minecraft Broadcast Synchronization • mcast.fun
      </footer>
    </div>
  );
}
