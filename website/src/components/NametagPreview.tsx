interface Player {
  name: string;
  live: boolean;
}

const PLAYERS: Player[] = [
  { name: "Ashish", live: true },
  { name: "PlayerTwo", live: false },
  { name: "PlayerThree", live: true },
];

function LiveDot() {
  return (
    <span className="relative inline-flex h-2.5 w-2.5 mr-1">
      <span className="absolute inline-flex h-full w-full rounded-full bg-accent opacity-60 animate-pulse-dot" />
      <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-accent" />
    </span>
  );
}

function NametagRow({ player }: { player: Player }) {
  return (
    <div className="flex items-center gap-3 rounded-lg bg-bg-primary/80 border border-border px-4 py-3 hover:border-accent/40 transition-colors duration-200">
      {/* Avatar block */}
      <div className="h-9 w-9 rounded bg-bg-secondary border border-border flex items-center justify-center text-xs font-bold text-accent">
        {player.name[0]}
      </div>

      {/* Name */}
      <span className="font-semibold text-sm">{player.name}</span>

      {/* LIVE badge */}
      {player.live && (
        <span className="ml-auto inline-flex items-center gap-1 rounded-md bg-accent/15 border border-accent/30 px-2.5 py-0.5 text-xs font-bold text-accent">
          <LiveDot />
          LIVE
        </span>
      )}
    </div>
  );
}

export default function NametagPreview() {
  return (
    <div className="w-full max-w-sm">
      <div className="rounded-2xl border border-border bg-bg-secondary p-5 glow-border animate-float">
        {/* Mock window chrome */}
        <div className="flex items-center gap-2 mb-4 pb-3 border-b border-border">
          <span className="h-2.5 w-2.5 rounded-full bg-live-red" />
          <span className="h-2.5 w-2.5 rounded-full bg-yellow-500" />
          <span className="h-2.5 w-2.5 rounded-full bg-green-500" />
          <span className="ml-3 text-xs text-text-secondary">
            Minecraft Server — Players
          </span>
        </div>

        <div className="flex flex-col gap-2.5">
          {PLAYERS.map((p) => (
            <NametagRow key={p.name} player={p} />
          ))}
        </div>

        <p className="mt-4 text-[10px] text-text-secondary/60 text-center">
          Demonstration only — not connected to live data
        </p>
      </div>
    </div>
  );
}
