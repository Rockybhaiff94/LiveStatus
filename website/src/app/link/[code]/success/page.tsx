'use client';

import React, { Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';

function SuccessContent() {
  const searchParams = useSearchParams();
  const channel = searchParams?.get('channel') || 'Your YouTube Channel';
  const player = searchParams?.get('player') || 'Minecraft Player';

  return (
    <div className="w-full max-w-lg bg-[#111111] border border-[#262626] rounded-2xl p-6 sm:p-8 shadow-2xl text-center relative overflow-hidden">
      {/* Subtle Orange Glow Ambient */}
      <div className="absolute -top-20 -right-20 w-48 h-48 bg-[#FF7A00]/15 rounded-full blur-3xl pointer-events-none"></div>

      {/* Success Checkmark Icon */}
      <div className="w-20 h-20 rounded-full bg-[#FF7A00]/10 border-2 border-[#FF7A00] text-[#FF7A00] flex items-center justify-center text-3xl mx-auto mb-6 shadow-lg shadow-[#FF7A00]/20">
        ✓
      </div>

      <h1 className="text-2xl sm:text-3xl font-extrabold text-white mb-2 tracking-tight">
        Account Linked Successfully!
      </h1>
      <p className="text-[#A3A3A3] text-sm mb-6 leading-relaxed">
        Your YouTube channel is now connected to your Minecraft identity.
      </p>

      {/* Connected Details Box */}
      <div className="bg-[#080808] border border-[#262626] rounded-xl p-4 mb-6 space-y-3 text-left">
        <div className="flex justify-between items-center text-sm">
          <span className="text-[#A3A3A3]">Minecraft Streamer:</span>
          <span className="font-semibold text-white">{player}</span>
        </div>
        <div className="h-px bg-[#1f1f1f]"></div>
        <div className="flex justify-between items-center text-sm">
          <span className="text-[#A3A3A3]">YouTube Channel:</span>
          <span className="font-semibold text-[#FF7A00]">{channel}</span>
        </div>
        <div className="h-px bg-[#1f1f1f]"></div>
        <div className="flex justify-between items-center text-xs">
          <span className="text-[#A3A3A3]">Status:</span>
          <span className="text-green-400 font-semibold flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-green-400 animate-ping"></span>
            Synchronized with Server
          </span>
        </div>
      </div>

      {/* In-Game Instructions */}
      <div className="bg-[#161616] border border-[#262626] rounded-xl p-4 mb-8 text-xs text-[#A3A3A3] text-left space-y-2">
        <p className="font-bold text-white text-sm">🎮 What happens now?</p>
        <p>
          1. <strong className="text-white">Return to Minecraft:</strong> Check your in-game chat for the confirmation message.
        </p>
        <p>
          2. <strong className="text-white">Go LIVE:</strong> Start a YouTube live stream in category <strong className="text-white">Gaming (Minecraft)</strong>.
        </p>
        <p>
          3. <strong className="text-white">Automatic LIVE Indicator:</strong> Your nametag will automatically append <span className="text-red-500 font-bold bg-red-950/40 px-1 py-0.5 rounded border border-red-500/30">LIVE</span> for all players on the server!
        </p>
      </div>

      <Link
        href="/"
        className="inline-block w-full bg-[#1f1f1f] hover:bg-[#2a2a2a] text-white font-medium py-3 px-6 rounded-xl transition-colors text-sm"
      >
        Visit MCast Homepage
      </Link>
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
