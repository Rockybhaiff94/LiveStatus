# MCast.fun — Minecraft Live Status Website

Official landing page and web presence for **MCast.fun** (LiveStatus), a Minecraft Paper plugin that displays a YouTube LIVE indicator next to player nametags when streaming on YouTube.

---

## Features

- **Black + Orange Aesthetic**: Tailored gaming theme (`#080808`, `#111111`, `#FF7A00`) with subtle glows and clean borders.
- **Interactive Nametag Preview**: Real-time mock of in-game player list with pulsing `LIVE` badge indicator.
- **Responsive Layout**: Optimized for mobile, tablet, and desktop viewports.
- **Zero-Latency Fonts**: Self-hosted `Inter` font via `next/font/google`.
- **Fast & Lightweight**: Server-rendered React components with minimal client-side runtime.

---

## Tech Stack

- **Framework**: [Next.js 16](https://nextjs.org/) (App Router, Turbopack)
- **Library**: [React 19](https://react.dev/)
- **Styling**: [Tailwind CSS v4](https://tailwindcss.com/)
- **Icons**: [Lucide React](https://lucide.dev/)

---

## Local Development

```bash
cd website
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

---

## Production Build

```bash
npm run build
npm run start
```

---

## Deployment (Vercel)

When deploying this monorepo to Vercel:

1. Connect the GitHub repository `Rockybhaiff94/MCast`.
2. In **Project Settings** → **General** → **Root Directory**, set:
   ```text
   website
   ```
3. Vercel will automatically detect Next.js and deploy the website.
