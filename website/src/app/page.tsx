"use client";

import React, { useState, useEffect, useRef } from "react";
import Link from "next/link";
import commandsData from "@/data/commands.json";

interface CommandItem {
  id: string;
  syntax: string;
  display: string;
  copyText: string;
  category: "player" | "admin";
  description: string;
  permission: string;
  implemented: boolean;
  initialVisible: boolean;
}

export default function Home() {
  const [navOpen, setNavOpen] = useState(false);
  const [openFaq, setOpenFaq] = useState<{ [key: number]: boolean }>({});
  const [copiedCmd, setCopiedCmd] = useState<string | null>(null);
  const [showAllCommands, setShowAllCommands] = useState(false);
  const copyTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const [scrollProgress, setScrollProgress] = useState(0);
  const [isScrolled, setIsScrolled] = useState(false);

  useEffect(() => {
    // 1. Passive scroll listener for progress line and topbar backdrop
    let ticking = false;
    const handleScroll = () => {
      if (!ticking) {
        window.requestAnimationFrame(() => {
          const totalHeight = document.documentElement.scrollHeight - window.innerHeight;
          setScrollProgress(totalHeight > 0 ? Math.min(Math.max(window.scrollY / totalHeight, 0), 1) : 0);
          setIsScrolled(window.scrollY > 30);
          ticking = false;
        });
        ticking = true;
      }
    };
    window.addEventListener("scroll", handleScroll, { passive: true });
    handleScroll();

    // 2. IntersectionObserver for scroll-reveal animations
    if (typeof window !== "undefined" && "IntersectionObserver" in window) {
      const observer = new IntersectionObserver(
        (entries) => {
          entries.forEach((entry) => {
            if (entry.isIntersecting) {
              entry.target.classList.add("is-revealed");
              observer.unobserve(entry.target);
            }
          });
        },
        { threshold: 0.1, rootMargin: "0px 0px -30px 0px" }
      );

      document.querySelectorAll(".reveal, .reveal-stagger").forEach((el) => observer.observe(el));
      return () => {
        window.removeEventListener("scroll", handleScroll);
        observer.disconnect();
      };
    }

    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  const handleCopy = async (value: string) => {
    try {
      if (typeof navigator !== "undefined" && navigator.clipboard) {
        await navigator.clipboard.writeText(value);
      }
      setCopiedCmd(value);
      if (copyTimeoutRef.current) clearTimeout(copyTimeoutRef.current);
      copyTimeoutRef.current = setTimeout(() => setCopiedCmd(null), 2000);
    } catch {}
  };

  const renderCopyBtn = (cmd: string, label: string) => {
    const isCopied = copiedCmd === cmd;
    return (
      <button
        className={`copy-btn ${isCopied ? "copied" : ""}`}
        onClick={() => handleCopy(cmd)}
        aria-label={label}
      >
        {isCopied ? (
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M20 6 9 17l-5-5" />
          </svg>
        ) : (
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="8" y="8" width="12" height="12" rx="2" />
            <path d="M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3" />
          </svg>
        )}
        <span className="copy-word">{isCopied ? "Copied!" : "Copy"}</span>
      </button>
    );
  };

  const toggleFaq = (index: number) => {
    setOpenFaq((prev) => ({
      ...prev,
      [index]: !prev[index],
    }));
  };

  const handleDownload = (e?: React.MouseEvent) => {
    if (e) e.preventDefault();
    const configUrl =
      (typeof window !== "undefined" && (window as any).MCAST_CONFIG?.downloadUrl) ||
      process.env.NEXT_PUBLIC_DOWNLOAD_URL ||
      "";

    if (configUrl && /^https?:\/\//i.test(configUrl)) {
      window.location.assign(configUrl);
    } else {
      window.alert("Connect your official MCast release URL in site configuration to enable downloads.");
    }
  };

  return (
    <>
      {/* Topbar navigation */}
      <header className={`topbar ${isScrolled ? "scrolled" : ""}`}>
        <div
          className="scroll-progress-line"
          style={{ transform: `scaleX(${scrollProgress})` }}
          aria-hidden="true"
        />
        <div className="wrap nav">
          <a className="brand" href="#home" aria-label="MCast home">
            <img src="/assets/mcast-logo.png" alt="MCast" />
          </a>
          <nav
            className={`nav-links ${navOpen ? "open" : ""}`}
            id="nav-links"
            aria-label="Main navigation"
          >
            <a href="#features" onClick={() => setNavOpen(false)}>
              Features
            </a>
            <a href="#how-it-works" onClick={() => setNavOpen(false)}>
              How it works
            </a>
            <a href="#commands" onClick={() => setNavOpen(false)}>
              Commands
            </a>
            <a href="#setup" onClick={() => setNavOpen(false)}>
              Setup
            </a>
            <a href="#faq" onClick={() => setNavOpen(false)}>
              FAQ
            </a>
          </nav>
          <div className="nav-actions">
            <a className="btn btn-primary" href="#download" onClick={handleDownload}>
              Get MCast{" "}
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M12 3v12m-5-5 5 5 5-5" />
                <path d="M5 16v4h14v-4" />
              </svg>
            </a>
            <button
              className="menu-toggle"
              id="menu-toggle"
              aria-label={navOpen ? "Close navigation" : "Open navigation"}
              aria-controls="nav-links"
              aria-expanded={navOpen}
              onClick={() => setNavOpen(!navOpen)}
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
              >
                <path d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            </button>
          </div>
        </div>
      </header>

      <main>
        {/* Hero Section */}
        <section className="hero" id="home">
          <div className="wrap hero-grid">
            <div className="hero-copy">
              <div className="eyebrow hero-anim-1">Bring your stream to the server</div>
              <h1 className="hero-anim-2">
                <span className="line">Show when</span>
                <span className="line accent">you&apos;re live.</span>
                <span className="line">In Minecraft.</span>
              </h1>
              <p className="hero-anim-3">
                MCast brings your YouTube Live status into Minecraft, so your community can see when you&apos;re streaming—right in your in-game name tag.
              </p>
              <div className="hero-actions hero-anim-4">
                <a className="btn btn-primary" href="#download" onClick={handleDownload}>
                  Get MCast{" "}
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2.2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M12 3v12m-5-5 5 5 5-5" />
                    <path d="M5 16v4h14v-4" />
                  </svg>
                </a>
                <a className="btn btn-outline" href="#commands">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <path d="m8 5-5 7 5 7M13 19h8" />
                  </svg>{" "}
                  Explore commands
                </a>
              </div>
              <div className="proof-row hero-anim-5">
                <span className="proof">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <circle cx="12" cy="12" r="9" />
                    <path d="M12 7v5l3 2" />
                  </svg>
                  Real-time status
                </span>
                <span className="proof">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="M10 13a5 5 0 0 0 7.1 0l2-2a5 5 0 0 0-7.1-7.1l-1.1 1.1" />
                    <path d="M14 11a5 5 0 0 0-7.1 0l-2 2a5 5 0 0 0 7.1 7.1l1.1-1.1" />
                  </svg>
                  Simple channel linking
                </span>
                <span className="proof">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="m12 3 2.5 5.5L20 11l-5.5 2.5L12 19l-2.5-5.5L4 11l5.5-2.5L12 3Z" />
                    <path d="m19 16 1 2 2 1-2 1-1 2-1-2-2-1 2-1 1-2Z" />
                  </svg>
                  Built for communities
                </span>
              </div>
            </div>
            <div
              className="hero-visual hero-anim-visual"
              aria-label="Illustration of MCast live status in a Minecraft-style world"
            >
              <div className="chat-card">
                <div className="chat-top">
                  <span>SERVER CHAT</span>
                  <span className="chat-live">
                    <i className="live-dot"></i> STREAM LIVE
                  </span>
                </div>
                <div className="chat-line">
                  <span className="name">[GAMER]</span> hey, we&apos;re live!
                </div>
                <div className="chat-line">
                  <span className="name">[FRIEND]</span> joining in a sec
                </div>
                <div className="chat-line">
                  <span className="name">[VIEWER]</span> let&apos;s go!
                </div>
              </div>
              <div className="gametag">
                <span className="player">YourName</span>
                <span className="tag-live">
                  <i className="live-dot"></i> LIVE
                </span>
              </div>
              <div className="float-caption">Live together. Play together.</div>
            </div>
          </div>
          <a className="scroll-cue" href="#features" aria-label="Scroll to features">
            <span></span>
          </a>
        </section>

        {/* Trust Strip */}
        <div className="trust-strip reveal">
          <div className="wrap trust-inner">
            <span className="pill">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="m5 12 4 4L19 6" />
              </svg>
              Made for Minecraft servers
            </span>
            <span className="pill">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 3v18M3 12h18" />
              </svg>
              Simple commands
            </span>
            <span className="pill">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 3 4 7v5c0 5 3.5 8 8 9 4.5-1 8-4 8-9V7l-8-4Z" />
                <path d="m9 12 2 2 4-5" />
              </svg>
              Admin controls
            </span>
          </div>
        </div>

        {/* Features Section */}
        <section className="section features" id="features">
          <div className="wrap">
            <div className="section-head reveal">
              <div className="eyebrow">Why MCast</div>
              <h2>
                Small plugin. <span className="accent">Big connection.</span>
              </h2>
              <p>
                Give your Minecraft community a simple way to spot when a creator is live, without pulling everyone out of the game.
              </p>
            </div>
            <div className="feature-grid reveal-stagger">
              <article className="feature-card">
                <div className="iconbox">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                  >
                    <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7m10 10 1.4 1.4M18.4 5.6 17 7M7 17l-1.4 1.4" />
                    <circle cx="12" cy="12" r="5" />
                    <circle cx="12" cy="12" r="1" />
                  </svg>
                </div>
                <h3>Live status in-game</h3>
                <p>
                  Make streaming status visible beside a player&apos;s name, right where your community is playing.
                </p>
              </article>
              <article className="feature-card">
                <div className="iconbox">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="M10 13a5 5 0 0 0 7.1 0l2-2a5 5 0 0 0-7.1-7.1l-1.1 1.1" />
                    <path d="M14 11a5 5 0 0 0-7.1 0l-2 2a5 5 0 0 0 7.1 7.1l1.1-1.1" />
                  </svg>
                </div>
                <h3>Quick YouTube linking</h3>
                <p>
                  Let players connect their YouTube channel with an in-game command and a clear setup flow.
                </p>
              </article>
              <article className="feature-card">
                <div className="iconbox">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="M12 3v18M3 12h18" />
                    <path d="m5 5 14 14M19 5 5 19" />
                  </svg>
                </div>
                <h3>Made to fit your stack</h3>
                <p>
                  Designed to sit alongside your server&apos;s name-tag, scoreboard and team setup with configuration options.
                </p>
              </article>
              <article className="feature-card">
                <div className="iconbox">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="m13 2-3 8H4l7 12 3-8h6L13 2Z" />
                  </svg>
                </div>
                <h3>Lightweight by design</h3>
                <p>
                  One focused job: bring creator activity closer to your players with minimal fuss.
                </p>
              </article>
            </div>
          </div>
        </section>

        {/* About / Showcase Section */}
        <section className="section about" aria-labelledby="about-title">
          <div className="wrap about-grid">
            <div className="about-copy reveal">
              <div className="eyebrow">What is MCast?</div>
              <h2 id="about-title">
                Your stream belongs <span className="accent">in the world.</span>
              </h2>
              <p>
                MCast is a Minecraft plugin concept built around one simple idea: when you&apos;re live on YouTube, your server can show that status next to your Minecraft name. It helps friends and viewers discover the stream while they play.
              </p>
              <div className="about-points">
                <div className="about-point">
                  <span className="check">✓</span>
                  <span>Players link their own channel</span>
                </div>
                <div className="about-point">
                  <span className="check">✓</span>
                  <span>Admins can manage creator links</span>
                </div>
                <div className="about-point">
                  <span className="check">✓</span>
                  <span>Live status is easy to recognize</span>
                </div>
                <div className="about-point">
                  <span className="check">✓</span>
                  <span>Configuration can match your server</span>
                </div>
              </div>
              <a className="btn btn-outline" href="#how-it-works">
                See how it works{" "}
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M5 12h14m-6-6 6 6-6 6" />
                </svg>
              </a>
            </div>
            <div
              className="status-demo reveal"
              aria-label="Example of an in-game MCast name tag"
            >
              <div className="demo-label">Example in-game status</div>
              <div className="nameplate">
                <strong>YourName</strong>
                <span className="live-pill">
                  <i className="live-dot"></i> LIVE ON YOUTUBE
                </span>
              </div>
              <div className="demo-bottom">
                <div>
                  <strong>Streaming status, at a glance.</strong>
                  <span>Less friction between your server and your audience.</span>
                </div>
                <div className="tiny-blocks" aria-hidden="true">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* How It Works (Steps) Section */}
        <section className="section steps" id="how-it-works">
          <div className="wrap">
            <div className="section-head reveal">
              <div className="eyebrow">How it works</div>
              <h2>
                From install to <span className="accent">LIVE</span> in 3 steps.
              </h2>
              <p>
                No complicated player workflow: install the plugin, link a channel, and let MCast handle the status display.
              </p>
            </div>
            <div className="step-grid reveal-stagger">
              <article className="step-card">
                <div className="step-head">
                  <span className="step-num">01</span>
                  <h3>Install the plugin</h3>
                  <span className="step-symbol">
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <path d="M12 3v12m-5-5 5 5 5-5" />
                      <path d="M5 16v4h14v-4" />
                    </svg>
                  </span>
                </div>
                <p>
                  Add the MCast JAR to your server&apos;s <code>plugins</code> folder and restart the server.
                </p>
              </article>
              <article className="step-card">
                <div className="step-head">
                  <span className="step-num">02</span>
                  <h3>Link YouTube</h3>
                  <span className="step-symbol">
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <path d="M10 13a5 5 0 0 0 7.1 0l2-2a5 5 0 0 0-7.1-7.1l-1.1 1.1" />
                      <path d="M14 11a5 5 0 0 0-7.1 0l-2 2a5 5 0 0 0 7.1 7.1l1.1-1.1" />
                    </svg>
                  </span>
                </div>
                <p>
                  Use the in-game linking command and follow the configured authorization or verification flow.
                </p>
              </article>
              <article className="step-card">
                <div className="step-head">
                  <span className="step-num">03</span>
                  <h3>Go live</h3>
                  <span className="step-symbol">
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <circle cx="12" cy="12" r="2" />
                      <path d="M16.2 7.8a6 6 0 0 1 0 8.4M7.8 16.2a6 6 0 0 1 0-8.4M19 5a10 10 0 0 1 0 14M5 19a10 10 0 0 1 0-14" />
                    </svg>
                  </span>
                </div>
                <p>
                  When your channel is detected as live, the in-game indicator can show your status to the server.
                </p>
              </article>
            </div>
          </div>
        </section>

        {/* Commands Reference Section */}
        <section className="section commands" id="commands">
          <div className="wrap">
            <div className="section-head reveal">
              <div className="eyebrow">Command reference</div>
              <h2>
                Less typing. <span className="accent">More playing.</span>
              </h2>
              <p>Quick examples of the player and admin actions the MCast experience can expose.</p>
            </div>
            <div className="command-layout reveal-stagger" id="commands-layout">
              {/* Player Commands */}
              <div className="command-panel">
                <div className="command-panel-head">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="m8 5-5 7 5 7M16 5l5 7-5 7" />
                  </svg>
                  Player commands
                </div>
                {(showAllCommands
                  ? (commandsData as CommandItem[]).filter((c) => c.category === "player")
                  : (commandsData as CommandItem[]).filter((c) => c.category === "player" && c.initialVisible)
                ).map((cmd) => (
                  <div
                    key={cmd.id}
                    className={`command-row ${!cmd.initialVisible ? "command-row-expand" : ""}`}
                  >
                    <div className="command-main">
                      <code>{cmd.display}</code>
                      <small>{cmd.description}</small>
                      <span className="command-perm">{cmd.permission}</span>
                    </div>
                    {renderCopyBtn(cmd.copyText, `Copy ${cmd.syntax}`)}
                  </div>
                ))}
              </div>

              {/* Admin Commands */}
              <div className="command-panel">
                <div className="command-panel-head">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="M12 3 4 7v5c0 5 3.5 8 8 9 4.5-1 8-4 8-9V7l-8-4Z" />
                    <path d="m9 12 2 2 4-5" />
                  </svg>
                  Admin commands
                </div>
                {(showAllCommands
                  ? (commandsData as CommandItem[]).filter((c) => c.category === "admin")
                  : (commandsData as CommandItem[]).filter((c) => c.category === "admin" && c.initialVisible)
                ).map((cmd) => (
                  <div
                    key={cmd.id}
                    className={`command-row ${!cmd.initialVisible ? "command-row-expand" : ""}`}
                  >
                    <div className="command-main">
                      <code>{cmd.display}</code>
                      <small>{cmd.description}</small>
                      <span className="command-perm">{cmd.permission}</span>
                    </div>
                    {renderCopyBtn(cmd.copyText, `Copy ${cmd.syntax}`)}
                  </div>
                ))}
              </div>
            </div>

            <div className="command-toggle-wrap">
              <button
                type="button"
                className="btn btn-outline command-toggle-btn"
                onClick={() => setShowAllCommands((prev) => !prev)}
                aria-expanded={showAllCommands}
                aria-controls="commands-layout"
              >
                <span>{showAllCommands ? "Show fewer" : `Show all commands (${commandsData.length})`}</span>
                <svg
                  className={`toggle-caret ${showAllCommands ? "rotated" : ""}`}
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <path d="m6 9 6 6 6-6" />
                </svg>
              </button>
            </div>

            <p className="command-note">
              Authoritative MCast command reference synchronized with plugin release. Administrative commands require operator status or the corresponding permission node.
            </p>
          </div>
        </section>

        {/* Setup / Installation Guide Section */}
        <section className="section setup" id="setup">
          <div className="wrap">
            <div className="section-head reveal">
              <div className="eyebrow">Installation guide</div>
              <h2>
                Four steps to <span className="accent">get started.</span>
              </h2>
              <p>
                Keep setup straightforward for server owners and easy to explain to the rest of your staff team.
              </p>
            </div>
            <div className="setup-grid reveal-stagger">
              <article className="setup-card">
                <div className="setup-icon">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="M12 3v12m-5-5 5 5 5-5" />
                    <path d="M5 16v4h14v-4" />
                  </svg>
                </div>
                <div className="setup-meta">Step 01</div>
                <h3>Get the JAR</h3>
                <p>Download the latest MCast plugin file from your official release source.</p>
              </article>
              <article className="setup-card">
                <div className="setup-icon">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z" />
                    <path d="M3 10h18" />
                  </svg>
                </div>
                <div className="setup-meta">Step 02</div>
                <h3>Add to plugins</h3>
                <p>
                  Place the JAR in your server&apos;s <code>plugins</code> folder.
                </p>
              </article>
              <article className="setup-card">
                <div className="setup-icon">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <circle cx="12" cy="12" r="3" />
                    <path d="m19.4 15 .1.1 1.4 1.1-1.5 2.6-1.7-.7a8 8 0 0 1-1.8 1l-.3 1.9h-3l-.3-1.9a8 8 0 0 1-1.8-1l-1.7.7-1.5-2.6 1.4-1.1a7 7 0 0 1 0-2l-1.4-1.1 1.5-2.6 1.7.7a8 8 0 0 1 1.8-1L12 7.2h3l.3 1.9a8 8 0 0 1 1.8 1l1.7-.7 1.5 2.6-1.4 1.1a7 7 0 0 1 0 1.9Z" />
                  </svg>
                </div>
                <div className="setup-meta">Step 03</div>
                <h3>Configure</h3>
                <p>Review the generated config and complete any required YouTube setup.</p>
              </article>
              <article className="setup-card">
                <div className="setup-icon">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="m7 4 13 8-13 8V4Z" />
                  </svg>
                </div>
                <div className="setup-meta">Step 04</div>
                <h3>Restart & test</h3>
                <p>Restart or reload the server, then try the linking command in-game.</p>
              </article>
            </div>
            <div className="setup-callout reveal">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <circle cx="12" cy="12" r="9" />
                <path d="M12 11v5m0-8h.01" />
              </svg>
              <div>
                <strong>Server-owner tip:</strong> Back up your current configuration before updating the plugin, and check your server console after restarting for setup errors or missing credentials.
              </div>
            </div>
          </div>
        </section>

        {/* FAQ Section */}
        <section className="section faq" id="faq">
          <div className="wrap">
            <div className="section-head reveal">
              <div className="eyebrow">Good to know</div>
              <h2>
                Frequently asked <span className="accent">questions.</span>
              </h2>
              <p>Answers to the things server owners and creators usually want to know first.</p>
            </div>
            <div className="faq-grid reveal-stagger">
              {[
                {
                  q: "What does MCast do?",
                  a: "MCast is designed to show a player's YouTube Live status in Minecraft, using an indicator beside their in-game name.",
                },
                {
                  q: "Can players link their own channel?",
                  a: "The intended flow is for players to link their channel with an in-game command; the exact verification steps depend on your plugin configuration.",
                },
                {
                  q: "Can admins manage channel links?",
                  a: "The UI includes example admin commands for assigning or removing a player's linked channel. Match their syntax and permissions to the released build.",
                },
                {
                  q: "Will it work with other name-tag plugins?",
                  a: "Compatibility can depend on your scoreboard, teams, and name-tag stack. Test MCast with your server's existing plugins before using it in production.",
                },
                {
                  q: "Does it need YouTube credentials?",
                  a: "Any required API key or authorization details should be documented in the plugin's official setup guide. Keep credentials private and never publish them in your website source.",
                },
                {
                  q: "Where do I get the latest version?",
                  a: "Use the official MCast download or release page. This starter site has a configurable download destination, so you can connect it to the correct release URL.",
                },
              ].map((item, index) => {
                const isOpen = !!openFaq[index];
                return (
                  <article key={index} className={`faq-item ${isOpen ? "open" : ""}`}>
                    <button
                      className="faq-question"
                      aria-expanded={isOpen}
                      onClick={() => toggleFaq(index)}
                    >
                      <span>{item.q}</span>
                      <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                      >
                        <path d="m6 9 6 6 6-6" />
                      </svg>
                    </button>
                    <div className="faq-answer">
                      <div>
                        <p>{item.a}</p>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          </div>
        </section>

        {/* Download CTA Section */}
        <section className="download-cta" id="download">
          <div className="wrap">
            <div className="cta-box reveal">
              <div className="cta-content">
                <div className="eyebrow">Your community is waiting</div>
                <h2>
                  Ready to bring MCast <span className="accent">to your server?</span>
                </h2>
                <p>
                  Make your server a little more connected. Add MCast, link your channel, and let your community know when the stream is on.
                </p>
                <div className="hero-actions" style={{ marginBottom: 0 }}>
                  <button className="btn btn-primary" onClick={handleDownload}>
                    Download MCast{" "}
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2.2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <path d="M12 3v12m-5-5 5 5 5-5" />
                      <path d="M5 16v4h14v-4" />
                    </svg>
                  </button>
                  <a className="btn btn-outline" href="#setup">
                    Read setup guide
                  </a>
                </div>
                <div className="cta-meta">
                  <span>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="m5 12 4 4L19 6" />
                    </svg>
                    Clear setup flow
                  </span>
                  <span>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="m5 12 4 4L19 6" />
                    </svg>
                    Player & admin commands
                  </span>
                  <span>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="m5 12 4 4L19 6" />
                    </svg>
                    Community-first
                  </span>
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="reveal">
        <div className="wrap">
          <div className="footer-top">
            <div className="footer-brand">
              <a href="#home">
                <img src="/assets/mcast-logo.png" alt="MCast" />
              </a>
              <p>Show your YouTube Live status in Minecraft. Less friction, more community.</p>
            </div>
            <div className="footer-col">
              <h4>Explore</h4>
              <a href="#features">Features</a>
              <a href="#how-it-works">How it works</a>
              <a href="#commands">Commands</a>
              <a href="#setup">Installation</a>
            </div>
            <div className="footer-col">
              <h4>Resources</h4>
              <a href="#setup">Quick setup</a>
              <a href="#faq">FAQ</a>
              <a href="#download" onClick={handleDownload}>
                Download
              </a>
              <a href="#commands">Command list</a>
              <Link href="/privacy-policy">Privacy Policy</Link>
              <Link href="/terms-of-service">Terms of Service</Link>
            </div>
            <div className="footer-col">
              <h4>Community</h4>
              <div className="social-links">
                <a
                  href="#download"
                  aria-label="YouTube link placeholder"
                  title="Connect your YouTube link in the source"
                  onClick={handleDownload}
                >
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M23 7.1a3 3 0 0 0-2.1-2.1C19 4.5 12 4.5 12 4.5s-7 0-8.9.5A3 3 0 0 0 1 7.1C.5 9 .5 12 .5 12s0 3 .5 4.9a3 3 0 0 0 2.1 2.1c1.9.5 8.9.5 8.9.5s7 0 8.9-.5a3 3 0 0 0 2.1-2.1c.5-1.9.5-4.9.5-4.9s0-3-.5-4.9ZM9.7 15.5v-7l6 3.5-6 3.5Z" />
                  </svg>
                </a>
                <a
                  href="#download"
                  aria-label="Discord link placeholder"
                  title="Connect your Discord invite in the source"
                  onClick={handleDownload}
                >
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M19.7 5.2A18 18 0 0 0 15.3 4l-.5 1a16 16 0 0 0-5.6 0l-.5-1a18 18 0 0 0-4.4 1.2C1.5 9.3.8 13.3 1.2 17.3a18 18 0 0 0 5.4 2.7l1.2-2a11 11 0 0 1-1.9-.9l.5-.4a13 13 0 0 0 11.2 0l.5.4a11 11 0 0 1-1.9.9l1.2 2a18 18 0 0 0 5.4-2.7c.5-4.7-.8-8.6-3.1-12.1ZM8.7 14.4c-.9 0-1.7-.9-1.7-2s.8-2 1.7-2 1.7.9 1.7 2-.8 2-1.7 2Zm6.6 0c-.9 0-1.7-.9-1.7-2s.8-2 1.7-2 1.7.9 1.7 2-.8 2-1.7 2Z" />
                  </svg>
                </a>
                <a
                  href="#download"
                  aria-label="Source code link placeholder"
                  title="Connect your repository in the source"
                  onClick={handleDownload}
                >
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 .9a11.1 11.1 0 0 0-3.5 21.6c.6.1.8-.3.8-.6v-2.1c-3.2.7-3.9-1.4-3.9-1.4-.5-1.3-1.3-1.7-1.3-1.7-1-.7.1-.7.1-.7 1.1.1 1.7 1.1 1.7 1.1 1 .1.7 2.1 3.3 1.6.1-.8.4-1.4.8-1.7-2.5-.3-5.2-1.3-5.2-5.7 0-1.3.5-2.3 1.1-3.1-.1-.3-.5-1.5.1-3.1 0 0 .9-.3 3.1 1.2a10.7 10.7 0 0 1 5.7 0c2.2-1.5 3.1-1.2 3.1-1.2.6 1.6.2 2.8.1 3.1.7.8 1.1 1.8 1.1 3.1 0 4.4-2.7 5.4-5.2 5.7.4.4.8 1 .8 2v3c0 .3.2.7.8.6A11.1 11.1 0 0 0 12 .9Z" />
                  </svg>
                </a>
              </div>
            </div>
          </div>
          <div className="footer-bottom">
            <span>© 2026 MCast. Built for the Minecraft community.</span>
            <span className="disclaimer">
              Not affiliated with Mojang or YouTube. Product and command details should match the current release.
            </span>
          </div>
        </div>
      </footer>
    </>
  );
}
