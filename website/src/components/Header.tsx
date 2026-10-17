"use client";

import { useState } from "react";
import Link from "next/link";

export default function Header() {
  const [open, setOpen] = useState(false);

  return (
    <header className="topbar">
      <div className="wrap nav">
        <Link className="brand" href="/" aria-label="MCast home">
          <img src="/assets/mcast-logo.png" alt="MCast" />
        </Link>
        <nav className={`nav-links ${open ? "open" : ""}`} id="nav-links" aria-label="Main navigation">
          <Link href="/#features" onClick={() => setOpen(false)}>
            Features
          </Link>
          <Link href="/#how-it-works" onClick={() => setOpen(false)}>
            How it works
          </Link>
          <Link href="/#commands" onClick={() => setOpen(false)}>
            Commands
          </Link>
          <Link href="/#setup" onClick={() => setOpen(false)}>
            Setup
          </Link>
          <Link href="/#faq" onClick={() => setOpen(false)}>
            FAQ
          </Link>
        </nav>
        <div className="nav-actions">
          <Link className="btn btn-primary" href="/#download">
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
          </Link>
          <button
            className="menu-toggle"
            id="menu-toggle"
            aria-label={open ? "Close navigation" : "Open navigation"}
            aria-controls="nav-links"
            aria-expanded={open}
            onClick={() => setOpen(!open)}
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
  );
}
