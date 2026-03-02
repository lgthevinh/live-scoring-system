import { Component, OnDestroy, OnInit, signal, WritableSignal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Location } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { SyncService } from '../../../core/services/sync.service';
import { MatchDetailDto } from '../../../core/models/match.model';
import { BroadcastService } from '../../../core/services/broadcast.service';
import { RefereeService } from '../../../core/services/referee.service';
import { ScoreSubmitBufferService, BufferedScoreSubmission } from '../../../core/services/score-submit-buffer.service';
import { ToastService } from '../../../core/services/toast.service';

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

  // Buffer-related state
  showCommitModal: WritableSignal<boolean> = signal(false);
  bufferedSubmissionId: WritableSignal<string | null> = signal(null);

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

  // Computed calculated score for display
  calculatedScore = computed(() => this.calculateTotalScore());

  // Breakdown computed values
  biologicalPoints = computed(() => (this.counters.goldenBallsScored() * 3) + this.counters.whiteBallsScored());
  barrierPoints = computed(() => (this.allianceBarrierPushed() ? 10 : 0) + (this.opponentBarrierPushed() ? 10 : 0));
  endGamePoints = computed(() => (this.counters.partialParking() * 5) + (this.counters.fullParking() * 10));
  fleetBonus = computed(() => this.counters.fullParking() >= 2 ? 10 : 0);
  penaltyPoints = computed(() => (this.counters.penaltyCount() * 5) + (this.counters.yellowCardCount() * 10));
  coefficient = computed(() => {
    const imbalanceMultipliers = [2.0, 1.5, 1.3];
    let coeff = imbalanceMultipliers[this.selectedImbalance()] || 1.3;
    if (!this.allianceBarrierPushed()) {
      coeff -= 0.2;
    }
    return Math.max(0, coeff);
  });

  private sub: any;

  constructor(
    private route: ActivatedRoute,
    private sync: SyncService,
    private broadcastService: BroadcastService,
    private refereeService: RefereeService,
    public bufferService: ScoreSubmitBufferService,
    private location: Location,
    private toastService: ToastService,
    private router: Router
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
    this.bufferedSubmissionId.set(null);

    this.sync.syncPlayingMatches().subscribe({
      next: (list) => {
        const found = (list || []).find(m => m.match.id === this.matchId);
        this.match.set(found ?? null);
        this.loading.set(false);

        // Check if there's already a pending submission for this match
        this.checkExistingPendingSubmission();

        // Send an initial snapshot so displays can align immediately
        this.onScoreUpdate('init', 'whiteBallsScored', this.counters.whiteBallsScored());
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Failed to fetch playing matches.');
        this.loading.set(false);
      }
    });
  }

  private checkExistingPendingSubmission(): void {
    const existing = this.bufferService.getPendingForMatch(this.matchId, this.color);
    if (existing) {
      // Load the existing data into the form
      this.loadFromBuffer(existing);
      this.bufferedSubmissionId.set(existing.id);
      this.submitMessage.set('Loaded existing pending submission for this match');
      setTimeout(() => this.submitMessage.set(''), 3000);
    }
  }

  private loadFromBuffer(submission: BufferedScoreSubmission): void {
    this.counters.whiteBallsScored.set(submission.payload.whiteBallsScored);
    this.counters.goldenBallsScored.set(submission.payload.goldenBallsScored);
    this.counters.partialParking.set(submission.payload.partialParking);
    this.counters.fullParking.set(submission.payload.fullParking);
    this.counters.penaltyCount.set(submission.payload.penaltyCount);
    this.counters.yellowCardCount.set(submission.payload.yellowCardCount);
    this.allianceBarrierPushed.set(submission.payload.allianceBarrierPushed);
    this.opponentBarrierPushed.set(submission.payload.opponentBarrierPushed);
    this.selectedImbalance.set(submission.payload.imbalanceCategory);
    this.redCard.set(submission.payload.redCard);
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
    this.bufferedSubmissionId.set(null);
  }

  setCounterValue(key: CounterKey, event: Event) {
    const input = event.target as HTMLInputElement;
    let value = parseInt(input.value, 10);

    if (isNaN(value) || value < 0) value = 0;

    // Apply max limits
    switch (key) {
      case 'whiteBallsScored':
      case 'goldenBallsScored':
        if (value > 100) value = 100;
        break;
      case 'partialParking':
      case 'fullParking':
        if (value > 2) value = 2;
        // Check total parking doesn't exceed 2
        const otherKey = key === 'partialParking' ? 'fullParking' : 'partialParking';
        const otherValue = this.counters[otherKey]();
        if (value + otherValue > 2) {
          value = 2 - otherValue;
        }
        break;
    }

    this.counters[key].set(value);
    this.onScoreUpdate('reset', key, value);
  }

  canDecrease(key: CounterKey): boolean {
    return this.counters[key]() > 0;
  }

  canIncrement(key: CounterKey): boolean {
    const currentValue = this.counters[key]();
    switch (key) {
      case 'whiteBallsScored':
      case 'goldenBallsScored':
        return currentValue < 100; // Max 100 balls each
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

  /**
   * Calculate the total score based on current counters
   * Matches server-side calculation in FanrocScore.java
   */
  calculateTotalScore(): number {
    if (this.redCard()) {
      return 0;
    }

    // Biological points (balls scored)
    const biologicalPoints = (this.counters.goldenBallsScored() * 3) + this.counters.whiteBallsScored();

    // Barrier points (10 points each)
    const barrierPoints = (this.allianceBarrierPushed() ? 10 : 0) + (this.opponentBarrierPushed() ? 10 : 0);

    // Calculate coefficient with barrier penalty (matches server logic)
    const imbalanceMultipliers = [2.0, 1.5, 1.3];
    let coefficient = imbalanceMultipliers[this.selectedImbalance()] || 1.3;
    if (!this.allianceBarrierPushed()) {
      coefficient -= 0.2; // Server subtracts 0.2 if alliance barrier not pushed
    }

    // End game points (parking)
    const endGamePoints = (this.counters.partialParking() * 5) + (this.counters.fullParking() * 10);

    // Fleet bonus (both robots fully parked)
    const fleetBonus = this.counters.fullParking() >= 2 ? 10 : 0;

    // Penalties
    const penalties = (this.counters.penaltyCount() * 5) + (this.counters.yellowCardCount() * 10);

    // Calculate base score (matches server: Math.round)
    const baseScore = (biologicalPoints + barrierPoints) * coefficient;
    const totalScore = Math.round(baseScore + endGamePoints + fleetBonus - penalties);

    return Math.max(0, totalScore);
  }

  /**
   * Submit score and add to buffer
   */
  submitScore() {
    if (!this.match()) {
      this.submitMessage.set('No match loaded – cannot submit.');
      setTimeout(() => this.submitMessage.set(''), 3000);
      return;
    }
    this.submitting.set(true);
    this.submitMessage.set('');

    const payload = this.buildScorePayload();

    // Add to buffer first
    const m = this.match()!;
    const teamIds = this.color === 'red'
      ? m.redTeams.map(t => t.teamId)
      : m.blueTeams.map(t => t.teamId);

    this.bufferService.addToBuffer({
      matchId: this.matchId,
      allianceId: this.allianceId,
      color: this.color,
      matchCode: m.match.matchCode || this.matchId,
      teamIds,
      payload,
      calculatedScore: this.calculateTotalScore()
    });

    this.refereeService.submitFinalScore(this.color, this.allianceId, payload).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.submitMessage.set('Score submitted and awaiting approval by scorekeeper.');
        setTimeout(() => this.submitMessage.set(''), 5000);
        this.toastService.show('Score submitted successfully', 'success');
        this.router.navigate(['/referee']);
      },
      error: (err) => {
        this.submitting.set(false);
        this.submitMessage.set('Failed to submit score: ' + (err?.error?.message || 'Unknown error'));
        setTimeout(() => this.submitMessage.set(''), 6000);
      }
    });
  }

  /**
   * Open the commit modal
   */
  openCommitModal() {
    this.showCommitModal.set(true);
  }

  /**
   * Close the commit modal
   */
  closeCommitModal() {
    this.showCommitModal.set(false);
  }

  /**
   * Submit a specific buffered submission
   */
  submitBufferedSubmission(submissionId: string) {
    const submission = this.bufferService.buffer().find(s => s.id === submissionId);
    if (!submission) return;


    this.refereeService.submitFinalScore(submission.color, submission.allianceId, submission.payload).subscribe({
      next: () => {
        this.bufferService.markAsSubmitted(submissionId);
      },
      error: (err) => {
        this.bufferService.markAsError(submissionId, err?.error?.message || 'Unknown error');
      }
    });
  }

  /**
   * Submit all pending buffered submissions
   */
  submitAllPending() {
    const pending = this.bufferService.getPendingSubmissions();
    pending.forEach(submission => {
      this.submitBufferedSubmission(submission.id);
    });
  }

  /**
   * Remove a submission from the buffer
   */
  removeFromBuffer(submissionId: string) {
    this.bufferService.removeFromBuffer(submissionId);
    if (this.bufferedSubmissionId() === submissionId) {
      this.bufferedSubmissionId.set(null);
    }
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
