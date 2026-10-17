import Link from "next/link";

export default function Footer() {
  return (
    <footer className="reveal">
      <div className="wrap">
        <div className="footer-top">
          <div className="footer-brand">
            <Link href="/" aria-label="MCast home">
              <img src="/assets/mcast-logo.png" alt="MCast" />
            </Link>
            <p>Show your YouTube Live status in Minecraft. Less friction, more community.</p>
          </div>
          <div className="footer-col">
            <h4>Explore</h4>
            <Link href="/#features">Features</Link>
            <Link href="/#how-it-works">How it works</Link>
            <Link href="/#commands">Commands</Link>
            <Link href="/#setup">Installation</Link>
          </div>
          <div className="footer-col">
            <h4>Resources</h4>
            <Link href="/#setup">Quick setup</Link>
            <Link href="/#faq">FAQ</Link>
            <Link href="/#download">Download</Link>
            <Link href="/#commands">Command list</Link>
            <Link href="/privacy-policy">Privacy Policy</Link>
            <Link href="/terms-of-service">Terms of Service</Link>
          </div>
          <div className="footer-col">
            <h4>Community</h4>
            <div className="social-links">
              <Link href="/#download" aria-label="YouTube link placeholder" title="YouTube">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path d="M23 7.1a3 3 0 0 0-2.1-2.1C19 4.5 12 4.5 12 4.5s-7 0-8.9.5A3 3 0 0 0 1 7.1C.5 9 .5 12 .5 12s0 3 .5 4.9a3 3 0 0 0 2.1 2.1c1.9.5 8.9.5 8.9.5s7 0 8.9-.5a3 3 0 0 0 2.1-2.1c.5-1.9.5-4.9.5-4.9s0-3-.5-4.9ZM9.7 15.5v-7l6 3.5-6 3.5Z" />
                </svg>
              </Link>
              <Link href="/#download" aria-label="Discord link placeholder" title="Discord">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path d="M19.7 5.2A18 18 0 0 0 15.3 4l-.5 1a16 16 0 0 0-5.6 0l-.5-1a18 18 0 0 0-4.4 1.2C1.5 9.3.8 13.3 1.2 17.3a18 18 0 0 0 5.4 2.7l1.2-2a11 11 0 0 1-1.9-.9l.5-.4a13 13 0 0 0 11.2 0l.5.4a11 11 0 0 1-1.9.9l1.2 2a18 18 0 0 0 5.4-2.7c.5-4.7-.8-8.6-3.1-12.1ZM8.7 14.4c-.9 0-1.7-.9-1.7-2s.8-2 1.7-2 1.7.9 1.7 2-.8 2-1.7 2Zm6.6 0c-.9 0-1.7-.9-1.7-2s.8-2 1.7-2 1.7.9 1.7 2-.8 2-1.7 2Z" />
                </svg>
              </Link>
              <a
                href="https://github.com/Rockybhaiff94/MCast"
                target="_blank"
                rel="noopener noreferrer"
                aria-label="GitHub repository"
                title="GitHub repository"
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
  );
}
