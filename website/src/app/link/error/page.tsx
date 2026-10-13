'use client';

import React, { Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';

function ErrorContent() {
  const searchParams = useSearchParams();
  const reason = searchParams?.get('reason') || 'unknown';
  const details = searchParams?.get('details');

  const reasonMap: Record<string, { title: string; desc: string }> = {
    consent_denied: {
      title: 'Google Consent Cancelled',
      desc: 'The Google OAuth authorization was declined. MCast requires read-only YouTube permission to detect when you are streaming.',
    },
    session_expired: {
      title: 'Linking Session Expired',
      desc: 'This link has expired for security reasons. Each linking session is valid for 5 minutes.',
    },
    invalid_session: {
      title: 'Invalid Linking Session',
      desc: 'The linking code does not exist or has already been used.',
    },
    invalid_state: {
      title: 'Security Verification Failed',
      desc: 'The OAuth state signature could not be verified. Please start a fresh linking session in Minecraft.',
    },
    exchange_failed: {
      title: 'Google Token Exchange Failed',
      desc: 'An error occurred while exchanging the authorization code with Google.',
    },
  };

  const info = reasonMap[reason] || {
    title: 'Linking Unsuccessful',
    desc: 'An unexpected issue occurred while connecting your YouTube account.',
  };

  return (
    <div className="w-full max-w-lg bg-[#111111] border border-[#262626] rounded-2xl p-6 sm:p-8 shadow-2xl text-center relative overflow-hidden">
      <div className="w-16 h-16 rounded-full bg-red-950/50 border border-red-500/30 text-red-400 flex items-center justify-center text-2xl mx-auto mb-4">
        ✕
      </div>

      <h1 className="text-2xl font-bold text-white mb-2">{info.title}</h1>
      <p className="text-sm text-[#A3A3A3] mb-6 leading-relaxed">{info.desc}</p>

      {details && (
        <div className="bg-[#080808] border border-[#262626] rounded-xl p-3 text-xs font-mono text-red-400 mb-6 text-left break-all">
          Error details: {details}
        </div>
      )}

      <div className="bg-[#161616] border border-[#262626] rounded-xl p-4 mb-6 text-xs text-[#A3A3A3] text-left space-y-2">
        <p className="font-semibold text-white">How to resolve:</p>
        <p>1. Open Minecraft and reconnect to your server.</p>
        <p>2. Execute <code className="text-[#FF7A00] bg-[#1a1a1a] px-1.5 py-0.5 rounded">/link youtube</code> in chat.</p>
        <p>3. Click the new link and ensure you grant the requested YouTube permissions.</p>
      </div>

      <Link
        href="/"
        className="inline-block w-full bg-[#1f1f1f] hover:bg-[#2a2a2a] text-white font-medium py-3 px-6 rounded-xl transition-colors text-sm"
      >
        Back to MCast Homepage
      </Link>
    </div>
  );
}

export default function LinkErrorPage() {
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
        <span className="text-xs text-red-400 bg-red-950/40 border border-red-500/30 px-3 py-1 rounded-full font-medium">
          Connection Alert
        </span>
      </header>

      {/* Main Content inside Suspense */}
      <main className="flex-1 flex items-center justify-center px-4 py-12">
        <Suspense fallback={<div className="text-[#A3A3A3] text-sm">Loading details...</div>}>
          <ErrorContent />
        </Suspense>
      </main>

      {/* Footer */}
      <footer className="border-t border-[#262626] bg-[#080808] py-4 text-center text-xs text-[#737373]">
        MCast LiveStatus • Centralized Minecraft Broadcast Synchronization • mcast.fun
      </footer>
    </div>
  );
}
