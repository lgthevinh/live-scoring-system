import { Component, OnDestroy, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Location } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { SyncService } from '../../../core/services/sync.service';
import { MatchDetailDto } from '../../../core/models/match.model';
import { RefereeWsService } from '../../../core/services/referee-ws.service';
import { RefereeService } from '../../../core/services/referee.service';
import { WS_CLOSE } from '../../../core/services/ws-client';

type CounterKey =
  | 'whiteBallsScored'
  | 'goldenBallsScored'
  | 'partialParking'
  | 'fullParking'
  | 'penaltyCount'
  | 'yellowCardCount';

type UpdateReason = 'inc' | 'dec' | 'reset' | 'init';

// Imbalance options for dropdown
interface ImbalanceOption {
  value: number;
  label: string;
  description: string;
  icon: string;
}

@Component({
  selector: 'app-score-tracking',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './score-tracking.html',
  styleUrl: './score-tracking.css'
})
export class ScoreTracking implements OnInit, OnDestroy {
  color: 'red' | 'blue' = 'red';
  matchId = '';
  allianceId = '';

  loading: WritableSignal<boolean> = signal(true);
  error: WritableSignal<string | null> = signal(null);
  match: WritableSignal<MatchDetailDto | null> = signal(null);

  // Submission state
  submitting: WritableSignal<boolean> = signal(false);
  submitMessage: WritableSignal<string> = signal('');

  // Versioning + source for robust live updates
  private version: WritableSignal<number> = signal(0);
  private readonly sourceId: string = this.initSourceId();

  // Fanroc scoring counters (barriersPushed moved to separate boolean signals)
  counters: Record<CounterKey, WritableSignal<number>> = {
    whiteBallsScored: signal(0),
    goldenBallsScored: signal(0),
    partialParking: signal(0),
    fullParking: signal(0),
    penaltyCount: signal(0),
    yellowCardCount: signal(0)
  };

  // Barrier push as boolean (toggle like red card)
  allianceBarrierPushed: WritableSignal<boolean> = signal(false);
  opponentBarrierPushed: WritableSignal<boolean> = signal(false);

  // Red card flag
  redCard: WritableSignal<boolean> = signal(false);

  imbalanceOptions: ImbalanceOption[] = [
    { value: 0, label: 'Balanced', description: '2.0x bonus - 0-1 ball difference', icon: '⚖️' },
    { value: 1, label: 'Medium', description: '1.5x bonus - 2-3 balls difference', icon: '⚖️' },
    { value: 2, label: 'Large', description: '1.3x bonus - 4+ balls difference', icon: '⚖️' }
  ];

  // Selected imbalance category
  selectedImbalance: WritableSignal<number> = signal(2);

  private sub: any;
  private wsSubs: Subscription[] = [];
  /** Banner shown when the WS closes with a fatal 4xxx code. */
  wsError: WritableSignal<string | null> = signal(null);

  constructor(
    private route: ActivatedRoute,
    private sync: SyncService,
    private refereeWs: RefereeWsService,
    private refereeService: RefereeService,
    private location: Location
  ) { }

  ngOnInit(): void {
    // Subscribe to WS streams BEFORE calling connect() -- the service
    // routes frames through a service-owned subject so subscription order
    // doesn't matter.
    this.wsSubs.push(
      this.refereeWs.scoreAck$().subscribe({
        next: (ack) => console.debug('[referee] SCORE_ACK', ack),
        error: (err) => console.error('[referee] scoreAck stream error', err)
      })
    );
    this.wsSubs.push(
      this.refereeWs.closeCode$.subscribe((code) => {
        this.wsError.set(this.describeCloseCode(code));
      })
    );

    this.sub = this.route.paramMap.subscribe(params => {
      const colorParam = (params.get('color') || 'red').toLowerCase();
      this.color = (colorParam === 'blue' ? 'blue' : 'red');
      this.matchId = params.get('matchId') || '';
      this.allianceId = this.color === 'red' ? this.matchId + "_R" : this.matchId + "_B";

      // Open the WS now that we know the route params. Server validates
      // token + REFEREE role + match-exists; failures arrive on closeCode$.
      if (this.matchId) {
        this.refereeWs.connect(this.matchId, this.color === 'red' ? 'R' : 'B');
      }

      this.fetchMatch();
    });
  }

  ngOnDestroy(): void {
    if (this.sub) this.sub.unsubscribe();
    this.wsSubs.forEach(s => s.unsubscribe());
    this.refereeWs.disconnect();
  }

  private describeCloseCode(code: number): string {
    switch (code) {
      case WS_CLOSE.UNAUTHORIZED:
        return 'Authentication failed. Please log in again.';
      case WS_CLOSE.FORBIDDEN:
        return 'You need REFEREE permissions to score this match.';
      case WS_CLOSE.NOT_FOUND:
        return 'Match not found on the server.';
      case WS_CLOSE.BAD_REQUEST:
        return 'Invalid match or alliance.';
      default:
        return `Connection closed (code ${code}).`;
    }
  }

  private fetchMatch() {
    this.loading.set(true);
    this.error.set(null);
    this.match.set(null);

    this.sync.syncPlayingMatches().subscribe({
      next: (list) => {
        const found = (list || []).find(m => m.match.id === this.matchId);
        this.match.set(found ?? null);
        this.loading.set(false);
        // Send an initial snapshot so displays can align immediately
        this.onScoreUpdate('init', 'whiteBallsScored', this.counters.whiteBallsScored());
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Failed to fetch playing matches.');
        this.loading.set(false);
      }
    });
  }

  titleText(): string {
    return `${this.matchId || '-'} score tracking`;
  }

  inc(key: CounterKey) {
    if (!this.canIncrement(key)) {
      return; // Don't increment if limit reached
    }
    this.counters[key].set(this.counters[key]() + 1);
    this.onScoreUpdate('inc', key, this.counters[key]());
  }

  dec(key: CounterKey) {
    this.counters[key].set(Math.max(0, this.counters[key]() - 1));
    this.onScoreUpdate('dec', key, this.counters[key]());
  }

  /**
   * Direct value-set used by the inline numeric inputs in the template
   * (e.g. {@code (change)="setCounterValue('whiteBallsScored', $event)"}).
   * Accepts either an Event from an input element, a string, or a number.
   * Clamps to non-negative and broadcasts a SCORE_DRAFT.
   */
  setCounterValue(key: CounterKey, eventOrValue: Event | string | number) {
    let raw: string | number | null = null;
    if (eventOrValue instanceof Event) {
      const target = eventOrValue.target as HTMLInputElement | null;
      raw = target ? target.value : null;
    } else {
      raw = eventOrValue;
    }
    const n = typeof raw === 'number' ? raw : parseInt(String(raw ?? '0'), 10);
    const safe = Number.isFinite(n) ? Math.max(0, Math.floor(n)) : 0;
    this.counters[key].set(safe);
    this.onScoreUpdate('reset', key, safe);
  }

  toggleRedCard() {
    this.redCard.set(!this.redCard());
    this.onScoreUpdate('reset', 'whiteBallsScored', this.counters.whiteBallsScored()); // Trigger update
  }

  toggleAllianceBarrierPush() {
    this.allianceBarrierPushed.set(!this.allianceBarrierPushed());
    this.onScoreUpdate('reset', 'whiteBallsScored', this.counters.whiteBallsScored()); // Trigger update
  }

  toggleOpponentBarrierPush() {
    this.opponentBarrierPushed.set(!this.opponentBarrierPushed());
    this.onScoreUpdate('reset', 'whiteBallsScored', this.counters.whiteBallsScored()); // Trigger update
  }

  setImbalance(imbalanceCategory: number) {
    this.selectedImbalance.set(imbalanceCategory);
    // Trigger update for the imbalance category
    this.onScoreUpdate('reset', 'penaltyCount', 0); // Just trigger a broadcast update
  }

  resetCounters() {
    (Object.keys(this.counters) as CounterKey[]).forEach(k => {
      this.counters[k].set(0);
      this.onScoreUpdate('reset', k, this.counters[k]());
    });
    this.selectedImbalance.set(2); // Reset to default
    this.redCard.set(false);
    this.allianceBarrierPushed.set(false);
    this.opponentBarrierPushed.set(false);
  }

  canDecrease(key: CounterKey): boolean {
    return this.counters[key]() > 0;
  }

  canIncrement(key: CounterKey): boolean {
    const currentValue = this.counters[key]();
    switch (key) {
      case 'whiteBallsScored':
      case 'goldenBallsScored':
        return currentValue < 50; // Max 50 balls each
      case 'partialParking':
      case 'fullParking':
        // Total parking cannot exceed 2 (since there are 2 robots)
        const otherKey = key === 'partialParking' ? 'fullParking' : 'partialParking';
        const otherValue = this.counters[otherKey]();
        return currentValue + otherValue < 2;
      case 'penaltyCount':
      case 'yellowCardCount':
        return true; // No limit for penalties
      default:
        return true;
    }
  }

  // Computed disable states for better Angular change detection
  get whiteBallsIncrementDisabled(): boolean {
    return !this.canIncrement('whiteBallsScored') || this.submitting();
  }

  get goldenBallsIncrementDisabled(): boolean {
    return !this.canIncrement('goldenBallsScored') || this.submitting();
  }

  get partialParkingIncrementDisabled(): boolean {
    return !this.canIncrement('partialParking') || this.submitting();
  }

  get fullParkingIncrementDisabled(): boolean {
    return !this.canIncrement('fullParking') || this.submitting();
  }

  get penaltyCountIncrementDisabled(): boolean {
    return this.submitting();
  }

  get yellowCardCountIncrementDisabled(): boolean {
    return this.submitting();
  }

  redTeamLine(): string {
    const m = this.match();
    return m ? m.redTeams.map(t => t.teamId).join(', ') : '';
  }

  blueTeamLine(): string {
    const m = this.match();
    return m ? m.blueTeams.map(t => t.teamId).join(', ') : '';
  }

  submitScore() {
    if (!this.match()) {
      this.submitMessage.set('No match loaded – cannot submit.');
      setTimeout(() => this.submitMessage.set(''), 3000);
      return;
    }
    this.submitting.set(true);
    this.submitMessage.set('');

    const payload = this.buildScorePayload();
    this.refereeService.submitFinalScore(this.matchId, this.allianceId, payload).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.submitMessage.set('Score submitted successfully.');
        setTimeout(() => this.submitMessage.set(''), 4000);

        this.location.back();
      },
      error: (err) => {
        this.submitting.set(false);
        this.submitMessage.set('Failed to submit score: ' + (err?.error?.message || 'Unknown error'));
        setTimeout(() => this.submitMessage.set(''), 6000);
      }
    });
  }

  /**
   * Handle score updates by sending a SCORE_DRAFT frame on the referee
   * socket. Replaces the legacy STOMP
   * {@code publishMessage('/app/live/score/update/{color}', ...)}.
   *
   * <p>The server extracts {@code state} from our envelope's payload and
   * wraps it into the {@code {matchId, alliance, state}} shape that
   * {@code ScoreControl.handleLiveScoreUpdate} still consumes.
   */
  onScoreUpdate(reason: UpdateReason, key: CounterKey, value: number) {
    const snapshot = this.buildFullSnapshot(reason, key, value);
    // Send just the `state` portion of the legacy snapshot; the server
    // re-wraps it, so we don't need to duplicate matchId/alliance.
    this.refereeWs.sendDraft(snapshot.payload.state);
    console.debug('[LIVE_SCORE_SNAPSHOT]', snapshot);
  }

  // Build a full, idempotent snapshot of current state (with version/source)
  private buildFullSnapshot(reason: UpdateReason, key: CounterKey, value: number) {
    const nextVersion = this.version() + 1;
    this.version.set(nextVersion);

    return {
      type: 'LIVE_SCORE_SNAPSHOT',
      payload: {
        matchId: this.matchId,
        alliance: this.color,
        version: nextVersion,
        sourceId: this.sourceId,
        at: new Date().toISOString(),
        state: {
          whiteBallsScored: this.counters.whiteBallsScored(),
          goldenBallsScored: this.counters.goldenBallsScored(),
          allianceBarrierPushed: this.allianceBarrierPushed(),
          opponentBarrierPushed: this.opponentBarrierPushed(),
          partialParking: this.counters.partialParking(),
          fullParking: this.counters.fullParking(),
          imbalanceCategory: this.selectedImbalance(),
          penaltyCount: this.counters.penaltyCount(),
          yellowCardCount: this.counters.yellowCardCount(),
          redCard: this.redCard()
        },
        lastChange: { key, reason, value }
      }
    };
  }

  private buildScorePayload() {
    return {
      whiteBallsScored: this.counters.whiteBallsScored(),
      goldenBallsScored: this.counters.goldenBallsScored(),
      allianceBarrierPushed: this.allianceBarrierPushed(),
      opponentBarrierPushed: this.opponentBarrierPushed(),
      partialParking: this.counters.partialParking(),
      fullParking: this.counters.fullParking(),
      imbalanceCategory: this.selectedImbalance(),
      penaltyCount: this.counters.penaltyCount(),
      yellowCardCount: this.counters.yellowCardCount(),
      redCard: this.redCard()
    };
  }

  // Generate or retrieve a stable device id for auditing
  private initSourceId(): string {
    const storageKey = 'refDeviceId';
    let id = localStorage.getItem(storageKey);
    if (!id) {
      id = 'ref-' + Math.random().toString(36).slice(2, 8);
      localStorage.setItem(storageKey, id);
    }
    return id;
  }
}
