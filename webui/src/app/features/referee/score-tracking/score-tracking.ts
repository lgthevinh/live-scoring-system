import { Component, OnDestroy, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Location } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { SyncService } from '../../../core/services/sync.service';
import { MatchDetailDto } from '../../../core/models/match.model';
import { BroadcastService } from '../../../core/services/broadcast.service';
import { RefereeService } from '../../../core/services/referee.service';

type CounterKey =
| 'whiteBallsScored'
| 'goldenBallsScored'
| 'barriersPushed'
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

    // Fanroc scoring counters
    counters: Record<CounterKey, WritableSignal<number>> = {
        whiteBallsScored: signal(0),
        goldenBallsScored: signal(0),
        barriersPushed: signal(0),
        partialParking: signal(0),
        fullParking: signal(0),
        penaltyCount: signal(0),
        yellowCardCount: signal(0)
    };

    // Red card flag
    redCard: WritableSignal<boolean> = signal(false);

    imbalanceOptions: ImbalanceOption[] = [
        {value: 0, label: 'Balanced', description: '2.0x bonus - 0-1 ball difference', icon: '⚖️'},
        {value: 1, label: 'Medium', description: '1.5x bonus - 2-3 balls difference', icon: '⚖️'},
        {value: 2, label: 'Large', description: '1.3x bonus - 4+ balls difference', icon: '⚖️'}
    ];

  // Selected imbalance category
  selectedImbalance: WritableSignal<number> = signal(2);

  private sub: any;

  constructor(
    private route: ActivatedRoute,
    private sync: SyncService,
    private broadcastService: BroadcastService,
    private refereeService: RefereeService,
    private location: Location
  ) { }

  ngOnInit(): void {
    this.sub = this.route.paramMap.subscribe(params => {
      const colorParam = (params.get('color') || 'red').toLowerCase();
      this.color = (colorParam === 'blue' ? 'blue' : 'red');
      this.matchId = params.get('matchId') || '';
      this.allianceId = this.color === 'red' ? this.matchId + "_R" : this.matchId + "_B";
      this.fetchMatch();
    });
  }

  ngOnDestroy(): void {
    if (this.sub) this.sub.unsubscribe();
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

  toggleRedCard() {
    this.redCard.set(!this.redCard());
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
      case 'barriersPushed':
        return currentValue < 2; // Max 2 barriers
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

  get barriersIncrementDisabled(): boolean {
    return !this.canIncrement('barriersPushed') || this.submitting();
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
    this.refereeService.submitFinalScore(this.color, this.allianceId, payload).subscribe({
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
   * Handle score updates by broadcasting a full snapshot, so receivers can stay in sync.
   */
  onScoreUpdate(reason: UpdateReason, key: CounterKey, value: number) {
    const snapshot = this.buildFullSnapshot(reason, key, value);
    this.broadcastService.publishMessage(`/app/live/score/update/${this.color}`, snapshot);
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
          barriersPushed: this.counters.barriersPushed(),
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
      barriersPushed: this.counters.barriersPushed(),
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
