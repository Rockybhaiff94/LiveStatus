'use client';

import React, { useState, useEffect, useCallback, Suspense } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Search, Radio } from 'lucide-react';
import ConnectedChannelCard, { ChannelInfo, LiveStatusInfo } from '@/components/ConnectedChannelCard';

function DashboardContent() {
  const searchParams = useSearchParams();
  const queryParam = searchParams?.get('query') || searchParams?.get('channel') || '';

  const [inputVal, setInputVal] = useState(queryParam);
  const [channel, setChannel] = useState<ChannelInfo | null>(null);
  const [liveStatus, setLiveStatus] = useState<LiveStatusInfo | null>(null);
  const [playerName, setPlayerName] = useState<string>('');
  const [serverName, setServerName] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [hasSearched, setHasSearched] = useState<boolean>(false);

  const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'https://api.mcast.fun';

  const lookupStatus = useCallback(async (query: string, isManualRefresh = false) => {
    const trimmed = query.trim();
    if (!trimmed) return;

    if (isManualRefresh) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    setError(null);
    setHasSearched(true);

    try {
      // If starts with UC, query channel endpoint; otherwise query session status
      const isChannelId = trimmed.startsWith('UC') && trimmed.length >= 20;
      const url = isChannelId
        ? `${apiUrl}/v1/channels/${encodeURIComponent(trimmed)}/status`
        : `${apiUrl}/v1/link-sessions/verify?code=${encodeURIComponent(trimmed)}`;

      const res = await fetch(url, { cache: 'no-store' });
      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.error || 'Channel or session not found');
      }

      if (isChannelId) {
        setChannel({
          channel_id: data.channel_id,
          channel_title: data.channel_title,
          custom_url: data.custom_url,
          avatar_url: data.avatar_url,
          channel_url: data.channel_url,
        });
        setLiveStatus({
          is_live: data.is_live,
          is_minecraft: data.is_minecraft,
          status: data.status,
          video_id: data.video_id,
          title: data.title,
          stream_url: data.stream_url,
          checked_at: data.checked_at,
          error: data.error,
        });
        setPlayerName('');
        setServerName('');
      } else {
        if (data.status === 'completed' && data.channel) {
          setChannel(data.channel);
          setLiveStatus(data.live_status);
          setPlayerName(data.player_name || '');
          setServerName(data.server_name || '');
        } else if (data.status === 'pending') {
          setChannel(null);
          setLiveStatus(null);
          setPlayerName(data.player_name || '');
          setServerName(data.server_name || '');
          setError(`Session code '${data.code}' is pending authorization. Click the link sent in Minecraft to authorize your YouTube account.`);
        } else {
          setChannel(null);
          setLiveStatus(null);
          setError(data.error || 'This linking session is no longer active.');
        }
      }
    } catch (err: any) {
      setError(err.message || 'Unable to load channel information');
      setChannel(null);
      setLiveStatus(null);
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [apiUrl]);

  useEffect(() => {
    if (queryParam) {
      lookupStatus(queryParam);
    }
  }, [queryParam, lookupStatus]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (inputVal.trim()) {
      lookupStatus(inputVal.trim());
    }
  };

  return (
    <div className="w-full max-w-2xl flex flex-col items-center">
      {/* Search & Lookup Header */}
      <div className="w-full bg-[#111111] border border-[#262626] rounded-2xl p-6 sm:p-8 text-center mb-6 shadow-2xl relative overflow-hidden">
        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#FF7A00]/10 border border-[#FF7A00]/30 text-[#FF7A00] text-xs font-semibold mb-3">
          <Radio size={13} className="animate-pulse" />
          <span>Real-Time Broadcast Synchronization</span>
        </div>

        <h1 className="text-2xl sm:text-3xl font-extrabold text-white mb-2 tracking-tight">
          Live Status Dashboard
        </h1>
        <p className="text-[#A3A3A3] text-sm leading-relaxed max-w-md mx-auto mb-6">
          Look up any linked streamer or YouTube channel to verify active broadcast synchronization and nametag status.
        </p>

        {/* Input Form */}
        <form onSubmit={handleSubmit} className="flex flex-col sm:flex-row gap-2 max-w-lg mx-auto">
          <div className="relative flex-1">
            <input
              type="text"
              value={inputVal}
              onChange={(e) => setInputVal(e.target.value)}
              placeholder="Enter YouTube Channel ID (UC...) or Linking Code"
              className="w-full bg-[#080808] border border-[#333] focus:border-[#FF7A00] rounded-xl px-4 py-3 text-sm text-white placeholder-[#666] outline-none transition-colors"
            />
          </div>
          <button
            type="submit"
            disabled={isLoading || !inputVal.trim()}
            className="inline-flex items-center justify-center gap-2 bg-[#FF7A00] hover:bg-[#FF9500] text-black font-bold px-6 py-3 rounded-xl text-sm transition-colors cursor-pointer disabled:opacity-50"
          >
            <Search size={16} />
            <span>Search</span>
          </button>
        </form>
      </div>

      {/* Results View */}
      {hasSearched && (
        <div className="w-full">
          <ConnectedChannelCard
            channel={channel}
            liveStatus={liveStatus}
            playerName={playerName}
            serverName={serverName}
            isLoading={isLoading}
            error={error}
            onRefresh={() => lookupStatus(inputVal, true)}
            isRefreshing={isRefreshing}
          />
        </div>
      )}

      {/* How it works note */}
      {!hasSearched && (
        <div className="w-full bg-[#141414] border border-[#262626] rounded-xl p-5 text-left text-xs text-[#8A8A8A] space-y-2">
          <p className="font-semibold text-white text-sm">💡 How channels are tracked:</p>
          <p>
            • <strong className="text-white">Offline channels</strong> remain listed and connected. They are never hidden when offline.
          </p>
          <p>
            • <strong className="text-white">Going live:</strong> When a connected streamer starts a stream tagged <strong className="text-white">Gaming (Minecraft)</strong>, the status automatically switches to <span className="text-red-400 font-bold">LIVE</span>.
          </p>
          <p>
            • Run <code className="text-[#FF7A00] bg-[#1a1a1a] px-1.5 py-0.5 rounded">/link youtube</code> in Minecraft to connect a new account.
          </p>
        </div>
      )}
    </div>
  );
}

export default function DashboardPage() {
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
        <Link
          href="/"
          className="text-xs text-[#A3A3A3] hover:text-white bg-[#111111] px-3 py-1.5 rounded-full border border-[#262626] transition-colors"
        >
          ← Homepage
        </Link>
      </header>

      {/* Main Content inside Suspense */}
      <main className="flex-1 flex items-center justify-center px-4 py-12">
        <Suspense fallback={<div className="text-[#A3A3A3] text-sm">Loading dashboard...</div>}>
          <DashboardContent />
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
