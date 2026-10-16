'use client';

import React from 'react';
import { ExternalLink, RefreshCw, Radio, CheckCircle2, AlertCircle, Gamepad2 } from 'lucide-react';

function YouTubeIcon({ className = "w-4 h-4 text-red-500" }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor">
      <path d="M23.498 6.186a3.016 3.016 0 0 0-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 0 0 .502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 0 0 2.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 0 0 2.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z"/>
    </svg>
  );
}

export interface ChannelInfo {
  channel_id: string;
  channel_title: string;
  custom_url: string | null;
  avatar_url?: string | null;
  channel_url: string;
}

export interface LiveStatusInfo {
  is_live: boolean;
  is_minecraft: boolean;
  status: 'LIVE_MINECRAFT' | 'LIVE_NOT_MINECRAFT' | 'OFFLINE' | 'UNKNOWN';
  video_id?: string | null;
  title?: string | null;
  stream_url?: string | null;
  checked_at?: string;
  error?: string | null;
}

interface ConnectedChannelCardProps {
  channel: ChannelInfo | null;
  liveStatus: LiveStatusInfo | null;
  playerName?: string;
  serverName?: string;
  isLoading?: boolean;
  error?: string | null;
  onRefresh?: () => void;
  isRefreshing?: boolean;
}

export default function ConnectedChannelCard({
  channel,
  liveStatus,
  playerName,
  serverName,
  isLoading = false,
  error = null,
  onRefresh,
  isRefreshing = false,
}: ConnectedChannelCardProps) {
  // State 2: Account connected, channel information loading
  if (isLoading) {
    return (
      <div className="w-full bg-[#111111] border border-[#262626] rounded-2xl p-6 sm:p-8 text-center animate-pulse">
        <div className="w-20 h-20 bg-[#1f1f1f] rounded-full mx-auto mb-4"></div>
        <div className="h-6 bg-[#1f1f1f] rounded-lg w-48 mx-auto mb-3"></div>
        <div className="h-4 bg-[#1f1f1f] rounded-lg w-32 mx-auto mb-6"></div>
        <div className="bg-[#080808] border border-[#262626] rounded-xl p-4 h-24 mb-4"></div>
        <p className="text-xs text-[#A3A3A3]">Synchronizing connected channel & live status...</p>
      </div>
    );
  }

  // State 5: Channel information unavailable due to an API error (when no channel data at all)
  if (error && !channel) {
    return (
      <div className="w-full bg-[#111111] border border-red-500/30 rounded-2xl p-6 sm:p-8 text-center">
        <div className="w-16 h-16 rounded-full bg-red-950/40 border border-red-500/40 text-red-400 flex items-center justify-center text-2xl mx-auto mb-4">
          <AlertCircle size={32} />
        </div>
        <h2 className="text-xl font-bold text-white mb-2">Channel Information Unavailable</h2>
        <p className="text-sm text-[#A3A3A3] mb-6 leading-relaxed">
          {error || 'Unable to retrieve your YouTube channel data. Please try again shortly.'}
        </p>
        {onRefresh && (
          <button
            onClick={onRefresh}
            disabled={isRefreshing}
            className="inline-flex items-center gap-2 bg-[#FF7A00] hover:bg-[#FF9500] text-black font-semibold px-5 py-2.5 rounded-xl transition-colors text-sm"
          >
            <RefreshCw size={16} className={isRefreshing ? 'animate-spin' : ''} />
            <span>Retry Connection Check</span>
          </button>
        )}
      </div>
    );
  }

  // State 1: Account not connected
  if (!channel) {
    return (
      <div className="w-full bg-[#111111] border border-[#262626] rounded-2xl p-6 sm:p-8 text-center">
        <div className="w-16 h-16 rounded-full bg-[#1a1a1a] border border-[#333] text-[#A3A3A3] flex items-center justify-center mx-auto mb-4">
          <YouTubeIcon className="w-8 h-8 text-[#A3A3A3]" />
        </div>
        <h2 className="text-xl font-bold text-white mb-2">No YouTube Account Connected</h2>
        <p className="text-sm text-[#A3A3A3] mb-6 leading-relaxed">
          Connect your YouTube channel using a linking code from your Minecraft server.
        </p>
      </div>
    );
  }

  const isLive = liveStatus?.is_live ?? false;
  const isMinecraft = liveStatus?.is_minecraft ?? false;
  const channelUrl = channel.channel_url || `https://www.youtube.com/channel/${channel.channel_id}`;
  const streamUrl = liveStatus?.stream_url || (liveStatus?.video_id ? `https://www.youtube.com/watch?v=${liveStatus.video_id}` : channelUrl);

  return (
    <div className="w-full bg-[#111111] border border-[#262626] rounded-2xl p-6 sm:p-8 shadow-2xl relative overflow-hidden text-left">
      {/* Dynamic ambient glow based on live status */}
      {isLive ? (
        <div className="absolute -top-24 -right-24 w-56 h-56 bg-red-600/20 rounded-full blur-3xl pointer-events-none animate-pulse"></div>
      ) : (
        <div className="absolute -top-20 -right-20 w-48 h-48 bg-[#FF7A00]/10 rounded-full blur-3xl pointer-events-none"></div>
      )}

      {/* Header bar: Connection Status & Refresh Action */}
      <div className="flex items-center justify-between pb-4 mb-6 border-b border-[#262626]">
        <div className="flex items-center gap-2">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-green-950/40 border border-green-500/30 text-green-400 text-xs font-semibold">
            <CheckCircle2 size={13} />
            <span>Connected & Synchronized</span>
          </span>
        </div>

        {onRefresh && (
          <button
            onClick={onRefresh}
            disabled={isRefreshing}
            className="flex items-center gap-1.5 text-xs text-[#A3A3A3] hover:text-white bg-[#1a1a1a] hover:bg-[#262626] border border-[#333] px-3 py-1.5 rounded-lg transition-colors cursor-pointer disabled:opacity-50"
            title="Check live status now"
          >
            <RefreshCw size={13} className={isRefreshing ? 'animate-spin text-[#FF7A00]' : ''} />
            <span>{isRefreshing ? 'Checking...' : 'Refresh Status'}</span>
          </button>
        )}
      </div>

      {/* Channel Profile Box (Avatar, Name, Handle, Link) */}
      <div className="flex flex-col sm:flex-row items-center sm:items-start gap-4 mb-6 bg-[#080808] border border-[#262626] rounded-xl p-4">
        {/* Channel Avatar with Live indicator border */}
        <div className="relative shrink-0">
          {channel.avatar_url ? (
            <img
              src={channel.avatar_url}
              alt={channel.channel_title}
              className={`w-20 h-20 rounded-full object-cover border-2 shadow-md ${
                isLive ? 'border-red-500 ring-4 ring-red-500/20' : 'border-[#333]'
              }`}
            />
          ) : (
            <div className={`w-20 h-20 rounded-full bg-[#1c1c1c] border-2 flex items-center justify-center text-white text-2xl font-bold shadow-md ${
              isLive ? 'border-red-500 ring-4 ring-red-500/20 text-red-500' : 'border-[#333]'
            }`}>
              {channel.channel_title.slice(0, 1).toUpperCase()}
            </div>
          )}

          {isLive && (
            <span className="absolute -bottom-1 -right-1 bg-red-600 text-white text-[10px] font-black px-1.5 py-0.5 rounded-md uppercase tracking-wider shadow">
              Live
            </span>
          )}
        </div>

        {/* Channel Details */}
        <div className="flex-1 text-center sm:text-left min-w-0">
          <div className="flex flex-col sm:flex-row sm:items-center gap-1 sm:gap-2">
            <h3 className="text-lg font-bold text-white truncate" title={channel.channel_title}>
              {channel.channel_title}
            </h3>
            {channel.custom_url && (
              <span className="text-xs font-mono text-[#FF7A00] truncate">
                {channel.custom_url.startsWith('@') ? channel.custom_url : `@${channel.custom_url}`}
              </span>
            )}
          </div>

          <p className="text-xs text-[#737373] font-mono mt-0.5 truncate">
            ID: {channel.channel_id}
          </p>

          <div className="mt-3 flex flex-wrap items-center justify-center sm:justify-start gap-2">
            <a
              href={channelUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 text-xs text-[#A3A3A3] hover:text-[#FF7A00] transition-colors bg-[#141414] border border-[#262626] px-2.5 py-1 rounded-md"
            >
              <YouTubeIcon className="w-3.5 h-3.5 text-red-500" />
              <span>Visit YouTube Channel</span>
              <ExternalLink size={11} />
            </a>

            {playerName && (
              <span className="text-xs text-[#737373] bg-[#141414] border border-[#262626] px-2.5 py-1 rounded-md">
                Player: <strong className="text-white font-medium">{playerName}</strong>
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Live Status Presentation Card */}
      {/* State 4: Channel connected and live */}
      {isLive ? (
        <div className="bg-red-950/30 border border-red-500/40 rounded-xl p-5 mb-6 shadow-inner">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <span className="relative flex h-3 w-3">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-3 w-3 bg-red-500"></span>
              </span>
              <span className="text-red-400 font-extrabold text-sm tracking-wide uppercase">
                Currently Live on YouTube
              </span>
            </div>

            {isMinecraft && (
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold bg-[#FF7A00]/15 text-[#FF7A00] border border-[#FF7A00]/30 px-2 py-0.5 rounded-md">
                <Gamepad2 size={12} />
                Minecraft Tag Active
              </span>
            )}
          </div>

          {liveStatus?.title && (
            <h4 className="text-white font-semibold text-sm mb-3 line-clamp-2">
              &ldquo;{liveStatus.title}&rdquo;
            </h4>
          )}

          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-2 border-t border-red-500/20">
            <span className="text-xs text-[#A3A3A3]">
              Minecraft nametag displaying <span className="text-red-400 font-bold bg-red-950/60 px-1 py-0.5 rounded">LIVE</span>
            </span>

            <a
              href={streamUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center justify-center gap-2 bg-red-600 hover:bg-red-700 text-white font-bold text-xs px-4 py-2 rounded-lg transition-colors shadow-lg shadow-red-900/30"
            >
              <Radio size={14} />
              <span>Watch Live Stream</span>
              <ExternalLink size={12} />
            </a>
          </div>
        </div>
      ) : (
        /* State 3: Channel connected and offline (Explicitly visible, never hidden) */
        <div className="bg-[#141414] border border-[#262626] rounded-xl p-5 mb-6">
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-[#525252]"></span>
              <span className="text-[#A3A3A3] font-semibold text-xs tracking-wider uppercase">
                Channel Offline
              </span>
            </div>
            {liveStatus?.checked_at && (
              <span className="text-[11px] text-[#525252]">
                Checked {new Date(liveStatus.checked_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
              </span>
            )}
          </div>

          <p className="text-xs text-[#8A8A8A] leading-relaxed">
            No active YouTube live broadcast detected. When you go live on YouTube with category <strong className="text-white">Gaming (Minecraft)</strong>, MCast will automatically attach your <span className="text-red-400 font-bold">LIVE</span> nametag on <strong className="text-white">{serverName || 'your Minecraft server'}</strong>.
          </p>

          {liveStatus?.error && (
            <div className="mt-3 p-2.5 rounded-lg bg-amber-950/20 border border-amber-500/20 text-[11px] text-amber-300">
              Notice: {liveStatus.error}
            </div>
          )}
        </div>
      )}

      {/* Sub-card: Server & Player Context */}
      <div className="bg-[#080808] border border-[#262626] rounded-xl p-3 text-xs text-[#737373] space-y-1.5">
        <div className="flex justify-between items-center">
          <span>Target Minecraft Server:</span>
          <span className="text-white font-medium">{serverName || 'Minecraft Server'}</span>
        </div>
        {playerName && (
          <div className="flex justify-between items-center">
            <span>Linked In-Game Player:</span>
            <span className="text-white font-medium">{playerName}</span>
          </div>
        )}
      </div>
    </div>
  );
}
