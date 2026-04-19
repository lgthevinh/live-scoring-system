/**
 * Mirrors {@code org.thingai.app.api.ws.WsMessageType} on the server.
 * One source of truth for the {@code type} discriminator string of every
 * WebSocket frame.
 */
export const WsMessageType = {
  // common
  SNAPSHOT: 'SNAPSHOT',
  // /ws/live
  MATCH_STATE: 'MATCH_STATE',
  SCORE_UPDATE: 'SCORE_UPDATE',
  DISPLAY_CONTROL: 'DISPLAY_CONTROL',
  // /ws/ranking
  RANKING_UPDATE: 'RANKING_UPDATE',
  // /ws/referee
  SCORE_DRAFT: 'SCORE_DRAFT',
  SCORE_ACK: 'SCORE_ACK',
} as const;
export type WsMessageType = (typeof WsMessageType)[keyof typeof WsMessageType];

// --- payload shapes -------------------------------------------------------
// These intentionally use {@code unknown} for the deeply-typed score fields:
// scoring rules are season-specific, so the WS layer doesn't know the shape.
// Consumers that care (e.g. score-tracking) cast to their season DTO.

export interface MatchStatePayload {
  matchId: string;
  loadedMatchId?: string;
  state: number;
  timerSecondsRemaining?: number;
  r?: string[]; // red team ids
  b?: string[]; // blue team ids
}

export interface ScoreUpdatePayload {
  matchId: string;
  r?: unknown; // red Score
  b?: unknown; // blue Score
}

export interface DisplayControlPayload {
  action: number;
  data?: unknown;
}

/** Snapshot frame on /ws/live. Fields may be null on a cold server. */
export interface LiveSnapshotPayload {
  matchState: MatchStatePayload | null;
  lastScoreRed: ScoreUpdatePayload | null;
  lastScoreBlue: ScoreUpdatePayload | null;
  lastDisplay: DisplayControlPayload | null;
}

/** Snapshot frame on /ws/ranking. */
export interface RankingSnapshotPayload {
  ranking: unknown[]; // RankingEntry[]
}

/** Snapshot frame on /ws/referee. {@code draft} is null if no draft cached. */
export interface RefereeSnapshotPayload {
  matchState: MatchStatePayload;
  draft: unknown | null;
}

export interface ScoreAckPayload {
  matchId: string;
  allianceId: string;
  state: string; // e.g. "ON_REVIEW"
}
