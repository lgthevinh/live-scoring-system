import { Component, OnInit, computed, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  MatchService,
  MockMatchService,
  ProdMatchService
} from '../../core/services/match.service';
import { environment } from '../../../environments/environment';
import { MatchDetailDto } from '../../core/models/match.model';
import { BroadcastEventsService } from '../../core/services/broadcast-events.service';
import { RankService } from '../../core/services/rank.service';
import { ScoreSubmitBufferService, BufferedScoreSubmission } from '../../core/services/score-submit-buffer.service';
import { TempScore } from '../../core/models/score.model';
import { ScoresheetComponent } from '../match-results/components/scoresheet/scoresheet.component';
import { DisplayControlAction, MatchControlService, MatchControlState } from '../../core/services/match-control.service';

type TabKey =
  | 'schedule'
  | 'incomplete'
  | 'score-edit'
  | 'active-match'
  | 'settings'
  | 'alliance-selection'
  | 'video-switch'
  | 'present-awards'
  | 'help';

@Component({
  selector: 'app-match-control',
  standalone: true,
  imports: [CommonModule, FormsModule, ScoresheetComponent],
  providers: [
    {
      provide: MatchService,
      useClass: environment.useFakeData ? MockMatchService : ProdMatchService
    }
  ],
  templateUrl: './match-control.html',
  styleUrl: './match-control.css'
})
export class MatchControl implements OnInit {
  tabs: { key: TabKey; label: string; icon?: string }[] = [
    { key: 'schedule', label: 'Schedule', icon: 'bi-list-ul' },
    { key: 'incomplete', label: 'Incomplete Matches', icon: 'bi-exclamation-circle' },
    { key: 'score-edit', label: 'Score Edit', icon: 'bi-pencil-square' },
    { key: 'active-match', label: 'Active Match', icon: 'bi-lightning-charge' },
    { key: 'settings', label: 'Settings', icon: 'bi-gear' },
    { key: 'alliance-selection', label: 'Alliance Selection', icon: 'bi-people' },
    { key: 'video-switch', label: 'Video Switch', icon: 'bi-camera-video' },
    { key: 'present-awards', label: 'Present Awards', icon: 'bi-award' },
    { key: 'help', label: 'Help', icon: 'bi-question-circle' }
  ];

  selectedTab = signal<TabKey>('schedule');

  // Data
  schedule = signal<MatchDetailDto[]>([]);
  loaded = signal<MatchDetailDto | null>(null);
  active = signal<MatchDetailDto | null>(null);
  activeMatchTimer: WritableSignal<number | null> = signal<number | null>(null);

  // View Control
  viewMatchType: number = 1; // Default to Qualification

// Editing
  editingMatch = signal<MatchDetailDto | null>(null);
  isSaving = signal<boolean>(false);
  redScoreData: any = {};
  blueScoreData: any = {};
  isRecalculatingRanking = signal<boolean>(false);

  // Playoff Generation
  playoffType: number = 3; // Default to Elimination Bracket
  playoffStartTime: string = new Date().toISOString().slice(0, 16);
  playoffMatchDuration: number = 10;
  playoffFieldCount: number = 2;
  playoffAllianceTeamsJson: string = '[{"allianceId":"1","teamId":"123A"},{"allianceId":"2","teamId":"456B"}]';

  // Manual Match Creation
  manualMatchType: number = 1;
  manualMatchNumber: number = 1;
  manualStartTime: string = new Date().toISOString().slice(0, 16);
  manualRedTeams: string = '';
  manualBlueTeams: string = '';

  constructor(
    private matchService: MatchService,
    private matchControl: MatchControlService,
    private broadcastEvents: BroadcastEventsService,
    private rankService: RankService,
    public bufferService: ScoreSubmitBufferService
  ) { }

  // -----------------------------------------------------------------
  // Modal state. One signal holds the active modal; the legacy
  // show<Name>Modal() accessors derive from it so existing template
  // bindings keep working without scattering booleans across the file.
  // Backend wiring is still TODO — see {@link ScorekeeperService}.
  // -----------------------------------------------------------------
  private activeModal = signal<'commit' | 'override' | 'tempCommit' | 'tempReject' | null>(null);
  private modalContext = signal<{
    tempScoreId?: string | null;
    tempScoreAlliance?: 'red' | 'blue' | null;
    overrideAlliances?: string[];
    rejectReason?: string;
  }>({});

  showCommitModal = computed(() => this.activeModal() === 'commit');
  showOverrideConfirmModal = computed(() => this.activeModal() === 'override');
  showTempScoreCommitModal = computed(() => this.activeModal() === 'tempCommit');
  showTempScoreRejectModal = computed(() => this.activeModal() === 'tempReject');

  // Back-compat accessors used by the HTML template.
  get pendingOverrideAlliances(): string[] { return this.modalContext().overrideAlliances ?? []; }
  get pendingTempScoreId(): string | null { return this.modalContext().tempScoreId ?? null; }
  get pendingTempScoreAlliance(): 'red' | 'blue' | null { return this.modalContext().tempScoreAlliance ?? null; }
  get tempScoreRejectReason(): string { return this.modalContext().rejectReason ?? ''; }
  set tempScoreRejectReason(v: string) {
    this.modalContext.update(c => ({ ...c, rejectReason: v }));
  }

  redTempScores: WritableSignal<TempScore[]> = signal([]);
  blueTempScores: WritableSignal<TempScore[]> = signal([]);
  hasTempScores = computed(() => this.redTempScores().length > 0 || this.blueTempScores().length > 0);
  getTempScoreById(id: string | null, color: 'red' | 'blue'): TempScore | null {
    if (!id) return null;
    const list = color === 'red' ? this.redTempScores() : this.blueTempScores();
    return list.find(t => t.tempScoreId === id) ?? null;
  }

  private closeModal(): void {
    this.activeModal.set(null);
    this.modalContext.set({});
  }

  openCommitModal(): void { this.activeModal.set('commit'); }
  closeCommitModal(): void { this.closeModal(); }

  closeOverrideConfirmModal(): void { this.closeModal(); }
  confirmOverrideSave(): void {
    // Backend endpoint for override-save not implemented yet.
    this.closeModal();
  }

  openTempScoreCommitModal(tempScoreId: string, alliance: 'red' | 'blue'): void {
    this.modalContext.set({ tempScoreId, tempScoreAlliance: alliance });
    this.activeModal.set('tempCommit');
  }
  closeTempScoreCommitModal(): void { this.closeModal(); }
  confirmCommitTempScore(): void {
    // Backend endpoint for commit not implemented yet.
    this.closeModal();
  }

  openTempScoreRejectModal(tempScoreId: string, alliance: 'red' | 'blue'): void {
    this.modalContext.set({ tempScoreId, tempScoreAlliance: alliance, rejectReason: '' });
    this.activeModal.set('tempReject');
  }
  closeTempScoreRejectModal(): void { this.closeModal(); }
  confirmRejectTempScore(): void {
    // Backend endpoint for reject not implemented yet.
    this.closeModal();
  }

  submitBufferedSubmission(id: string): void {
    this.bufferService.markAsSubmitted(id, 'scorekeeper');
  }
  removeFromBuffer(id: string): void {
    this.bufferService.removeFromBuffer(id);
  }

  ngOnInit(): void {
    this.loadSchedule();
    this.refreshControlState();
    this.broadcastEvents.matchState$().subscribe({
      next: (event) => this.handleMatchControlBroadcast(event),
      error: (e) => console.error('Failed to subscribe to match updates:', e)
    });
  }

  setTab(key: TabKey) {
    this.selectedTab.set(key);
  }

  private refreshControlState() {
    this.matchControl.getState().subscribe({
      next: (state) => this.applyControlState(state),
      error: (e) => console.error('Failed to load match control state', e)
    });
  }

  private applyControlState(state: MatchControlState) {
    const loadedMatch = this.findMatchById(state.loadedMatchId);
    const activeMatch = this.findMatchById(state.currentMatchId);

    this.loaded.set(loadedMatch);
    this.active.set(activeMatch);
    if (state.timerSecondsRemaining !== undefined && state.timerSecondsRemaining !== null) {
      this.updateTimerFromPayload(state.timerSecondsRemaining);
    }
  }

  private findMatchById(matchId: string | null | undefined): MatchDetailDto | null {
    if (!matchId) {
      return null;
    }
    return this.schedule().find(m => m.match.id === matchId) ?? null;
  }

  private handleMatchControlBroadcast(event: any) {
    if (!event || !event.payload) {
      return;
    }
    const payload = event.payload;
    if (payload.timerSecondsRemaining !== undefined && payload.timerSecondsRemaining !== null) {
      this.updateTimerFromPayload(payload.timerSecondsRemaining);
    }

    if (payload.matchId) {
      const match = this.findMatchById(payload.matchId);
      if (payload.state !== undefined && payload.state !== null) {
        if (payload.state === 1) {
          this.loaded.set(match);
        } else if (payload.state >= 2) {
          this.active.set(match);
        }
      }
    }
  }

  private updateTimerFromPayload(newValue: number) {
    const wasCountdown = this.previousTimerValue !== null && this.previousTimerValue <= 3;

    if (this.previousTimerValue !== null &&
        this.previousTimerValue > 0 &&
        newValue === 0 &&
        !wasCountdown) {
      this.playMatchEndSound();
    }

    this.previousTimerValue = newValue;
    this.activeMatchTimer.set(newValue);
  }

  // ---- Data loading ----
  loadSchedule(matchType?: number) {
    const typeToLoad = matchType !== undefined ? matchType : this.viewMatchType;
    // Fetch matches WITH scores so we can edit them
    this.matchService.getMatches(typeToLoad, true).subscribe({
      next: (list) => {
        this.schedule.set(list);
        this.refreshControlState();
      },
      error: (e) => console.error('Failed to load schedule', e)
    });
  }

  onViewMatchTypeChange() {
    this.loadSchedule();
  }

  // ---- Schedule row actions ----
  playMatch(match: MatchDetailDto) {
    this.matchControl.loadMatch(match.match.id).subscribe({
      next: () => {
        this.loaded.set(match);
        this.refreshControlState();
      },
      error: (e) => console.error('Failed to load match', e)
    });
  }

  enterScores(match: MatchDetailDto) {
    console.log('Entering scores for match:', match.match.matchCode);
    this.editingMatch.set(match);

    // Initialize with current data to ensure we have something to save
    // even if the user doesn't edit anything (or if they only edit one side)
    this.redScoreData = match.redScore?.rawScoreData ? this.safeParse(match.redScore.rawScoreData) : {};
    this.blueScoreData = match.blueScore?.rawScoreData ? this.safeParse(match.blueScore.rawScoreData) : {};

    console.log('Initialized Red Data:', this.redScoreData);
    console.log('Initialized Blue Data:', this.blueScoreData);

    this.setTab('score-edit');
  }

  private safeParse(json: string): any {
    try {
      return JSON.parse(json);
    } catch (e) {
      console.error('Failed to parse score JSON', e);
      return {};
    }
  }

  saveScores() {
    const m = this.editingMatch();
    console.log('Saving scores for match:', m?.match?.matchCode);
    console.log('Red Data:', this.redScoreData);
    console.log('Blue Data:', this.blueScoreData);

    if (!m) {
      console.error('No match is being edited.');
      alert('Error: No match is being edited.');
      return;
    }

    this.isSaving.set(true);
    const requests: Observable<any>[] = [];

    // Submit red
    if (m.redScore) {
      console.log('Submitting red score override for alliance ID:', m.redScore.id);
      requests.push(
        this.matchControl.overrideScore(m.match.id + "_R", this.redScoreData).pipe(
          catchError(e => {
            console.error('Failed to update red score', e);
            return of({ error: true, alliance: 'red' });
          })
        )
      );
    }

    // Submit blue
    if (m.blueScore) {
      console.log("Submitting blue score override");
      requests.push(
        this.matchControl.overrideScore(m.match.id + "_B", this.blueScoreData).pipe(
          catchError(e => {
            console.error('Failed to update blue score', e);
            return of({ error: true, alliance: 'blue' });
          })
        )
      );
    }

    if (requests.length === 0) {
      console.warn('No score objects found to update. Match might not have scores initialized.');
      alert('Error: No score objects found to update.');
      this.isSaving.set(false);
      return;
    }

    forkJoin(requests).subscribe({
      next: (results) => {
        const errors = results.filter(r => r && r.error);
        if (errors.length > 0) {
          alert('Some scores failed to save. Check console.');
        } else {
          // Success
          this.editingMatch.set(null);
          this.setTab('schedule');
          // Reload schedule to get updated scores
          this.loadSchedule(1);
        }
      },
      error: (err) => {
        console.error('Error saving scores', err);
        alert('Error saving scores');
      },
      complete: () => {
        this.isSaving.set(false);
      }
    });
  }

cancelEdit() {
    this.editingMatch.set(null);
    this.setTab('schedule');
  }

  recalculateRankings() {
    if (this.isRecalculatingRanking()) {
      return;
    }
    this.isRecalculatingRanking.set(true);
    this.rankService.recalculateRankings().subscribe({
      next: (success) => {
        if (success) {
          console.log('Rankings recalculated successfully');
          alert('Rankings recalculated successfully');
        } else {
          console.warn('Rankings recalculation returned false');
          alert('Failed to recalculate rankings');
        }
      },
      error: (err) => {
        console.error('Error recalculating rankings', err);
        alert('Error recalculating rankings: ' + (err.message || 'Unknown error'));
      },
      complete: () => {
        this.isRecalculatingRanking.set(false);
      }
    });
  }

  onRedScoreChange(data: any) {
    console.log('MatchControl: Red score changed', data);
    this.redScoreData = data;
  }

  onBlueScoreChange(data: any) {
    console.log('MatchControl: Blue score changed', data);
    this.blueScoreData = data;
  }

  // ---- Top buttons (Loaded section) ----
  loadNextMatch() {
    const list = this.schedule();
    if (!list.length) return;
    const current = this.loaded();
    const idx = current ? list.findIndex(m => m.match.id === current.match.id) : -1;
    const next = list[(idx + 1 + list.length) % list.length];
    this.matchControl.loadMatch(next.match.id).subscribe({
      next: () => {
        this.loaded.set(next);
        this.refreshControlState();
      },
      error: () => {
        console.error('Failed to load next match');
        alert('Failed to load next match');
      }
    });
  }

  showUpNext() {
    const loaded = this.loaded();
    this.matchControl.displayAction(DisplayControlAction.SHOW_PREVIEW, {
      matchId: loaded?.match?.id
    }).subscribe({
      next: () => console.debug('Show up next command sent'),
      error: (e) => {
        console.error('Failed to show up next', e);
        alert('Failed to show up next on display');
      }
    });
  }

  showCurrentMatch() {
    const active = this.active();
    this.matchControl.displayAction(DisplayControlAction.SHOW_MATCH, {
      matchId: active?.match?.id
    }).subscribe({
      next: () => console.debug('Show current match command sent'),
      error: (e) => {
        console.error('Failed to show current match', e);
        alert('Failed to show current match on display');
      }
    });
  }

  // Timer tracking for sound
  private previousTimerValue: number | null = null;

  // ---- Sound playback ----
  private audioContext: AudioContext | null = null;

  private playMatchStartSound(): void {
    // Initialize AudioContext on user interaction
    if (!this.audioContext) {
      this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
    }

    // Resume audio context if suspended (browser autoplay policy)
    if (this.audioContext.state === 'suspended') {
      this.audioContext.resume();
    }

    fetch('/assets/MatchSoundEffect.m4a')
      .then(response => response.arrayBuffer())
      .then(arrayBuffer => {
        this.audioContext?.decodeAudioData(arrayBuffer, (audioBuffer) => {
          const source = this.audioContext!.createBufferSource();
          source.buffer = audioBuffer;
          source.connect(this.audioContext!.destination);
          source.start(0);
        }, (err) => {
          console.error('Failed to decode audio:', err);
        });
      })
      .catch(err => {
        console.warn('Failed to load match start sound:', err);
      });
  }

  private playMatchEndSound(): void {
    console.log("playMatchEndSound called");

    // Initialize AudioContext on user interaction
    if (!this.audioContext) {
      this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
      console.log("Created new AudioContext");
    }

    // Resume audio context if suspended (browser autoplay policy)
    if (this.audioContext.state === 'suspended') {
      console.log("AudioContext is suspended, resuming...");
      this.audioContext.resume().then(() => {
        console.log("AudioContext resumed successfully");
      }).catch(err => {
        console.error("Failed to resume AudioContext:", err);
      });
    } else {
      console.log("AudioContext state:", this.audioContext.state);
    }

    console.log("Fetching /assets/match_end.m4a...");
    fetch('/assets/match_end.m4a')
      .then(response => {
        console.log("Fetch response status:", response.status);
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.arrayBuffer();
      })
      .then(arrayBuffer => {
        console.log("Audio file loaded, decoding...");
        this.audioContext?.decodeAudioData(arrayBuffer, (audioBuffer) => {
          console.log("Audio decoded successfully, playing...");
          const source = this.audioContext!.createBufferSource();
          source.buffer = audioBuffer;
          source.connect(this.audioContext!.destination);
          source.start(0);
        }, (err) => {
          console.error('Failed to decode audio:', err);
        });
      })
      .catch(err => {
        console.warn('Failed to load match end sound:', err);
      });
  }

  // ---- Top buttons (Active section) ----
  activateMatch() {
    const toActivate = this.loaded();
    if (!toActivate) {
      console.warn('No loaded match to activate.');
      return;
    }
    this.matchControl.activateMatch(toActivate.match.id).subscribe({
      next: () => {
        this.active.set(toActivate);
        this.loaded.set(null);
        this.refreshControlState();
      },
      error: (e) => {
        console.error('Failed to activate match', e);
        alert('Failed to activate match');
      }
    });
  }

  startMatch() {
    if (!this.active()) {
      console.warn('No active match to start.');
      return;
    }

    // Initialize AudioContext during user interaction to unlock it for later sound playback
    this.initializeAudioContext();

    // Sound disabled - display scoring only
    // this.playMatchStartSound();

    this.matchControl.startMatch().subscribe({
      next: () => {
        // Match is already active, no need to set again
        this.loaded.set(null); // Clear loaded match after starting
        this.previousTimerValue = null; // Reset timer tracking
        console.debug('Match timer started for active match');
        this.refreshControlState();
      },
      error: (e) => {
        console.error('Failed to start current match', e);
        alert('Failed to start match timer');
      }
    });

  }

  private initializeAudioContext(): void {
    // Initialize AudioContext on user interaction to unlock it for later playback
    if (!this.audioContext) {
      this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
      console.log('AudioContext initialized during user interaction');
    }

    // Resume audio context if suspended (browser autoplay policy)
    if (this.audioContext.state === 'suspended') {
      this.audioContext.resume().then(() => {
        console.log('AudioContext resumed successfully during user interaction');
      }).catch(err => {
        console.error('Failed to resume AudioContext:', err);
      });
    }
  }

  abortMatch() {
    const toAbort = this.active();
    if (!toAbort) {
      console.warn('No active match to abort.');
      return;
    }

    if (!confirm(`Abort match ${toAbort.match.matchCode}? The match will be moved back to loaded state.`)) {
      return;
    }

    this.matchControl.abortMatch().subscribe({
      next: () => {
        this.refreshControlState();
        console.debug('Match aborted successfully');
      },
      error: (e) => {
        console.error('Failed to abort match', e);
        alert('Failed to abort match: ' + e.message);
      }
    });
  }

  commitAndPostLastMatch() {
    this.matchControl.commitMatch().subscribe({
      next: () => {
        console.debug('Committed last match results')
        alert('Successfully committed last match results');
        this.loadSchedule(this.viewMatchType);
        this.refreshControlState();
      },
      error: (e) => {
        console.error('Failed to commit last match', e)
        alert('Failed to commit last match results');
      }
    });
  }

  // ---- Labels and helpers for FTC-like header ----
  selectedTabTitle(): string {
    const t = this.tabs.find(x => x.key === this.selectedTab());
    return t?.label ?? '';
  }

  loadedTitle(): string {
    const m = this.loaded();
    return m ? `${m.match.matchCode}` : '-';
  }

  activeTitle(): string {
    const m = this.active();
    return m ? `${m.match.matchCode}` : '-';
  }

  loadedRed(): string {
    const m = this.loaded();
    return m ? m.redTeams.map(t => t.teamId).join(', ') : '-';
    // If you prefer team names: t.teamName
  }

  loadedBlue(): string {
    const m = this.loaded();
    return m ? m.blueTeams.map(t => t.teamId).join(', ') : '-';
  }

  activeRed(): string {
    const m = this.active();
    return m ? m.redTeams.map(t => t.teamId).join(', ') : '-';
  }

  activeBlue(): string {
    const m = this.active();
    return m ? m.blueTeams.map(t => t.teamId).join(', ') : '-';
  }

  loadedTime(): string {
    const m = this.loaded();
    return m ? this.formatLocalTime(m.match.matchStartTime) : '';
  }

  activeTime(): string {
    const m = this.active();
    return m ? this.formatLocalTime(m.match.matchStartTime) : '';
  }

  activeMatchTimerDisplay(): string {
    const seconds = this.activeMatchTimer();
    if (seconds === null) return '';
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  }



  private formatLocalTime(iso: string | null | undefined): string {
    if (!iso) return '';
    try {
      const d = new Date(iso);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return '';
    }
  }

  // ---- Alliance Selection / Playoff / Manual Match ----

  generatePlayoff() {
    try {
      const allianceTeams = JSON.parse(this.playoffAllianceTeamsJson);
      if (!Array.isArray(allianceTeams)) {
        alert('Alliance Teams JSON must be an array.');
        return;
      }

      const payload = {
        playoffType: this.playoffType,
        startTime: this.playoffStartTime,
        matchDuration: this.playoffMatchDuration,
        fieldCount: this.playoffFieldCount,
        allianceTeams: allianceTeams,
        timeBlocks: [] // Optional for now
      };

      this.matchService.generatePlayoffSchedule(payload).subscribe({
        next: (res) => {
          alert(res.message);
          this.loadSchedule(this.playoffType); // Reload schedule
        },
        error: (e: any) => {
          console.error('Failed to generate playoff schedule', e);
          alert('Failed to generate playoff schedule: ' + (e.error?.message || e.message));
        }
      });
    } catch (e) {
      alert('Invalid JSON format for Alliance Teams.');
    }
  }

  createManualMatch() {
    // Remove duplicate team IDs and trim whitespace
    const cleanTeamIds = (ids: string) => {
      const idSet = new Set<string>();
      ids.split(',').forEach(id => {
        const trimmed = id.trim();
        if (trimmed) {
          idSet.add(trimmed);
        }
      });
      return Array.from(idSet);
    }

    const payload = {
      matchType: this.manualMatchType,
      matchNumber: this.manualMatchNumber,
      matchStartTime: this.manualStartTime,
      redTeamIds: cleanTeamIds(this.manualRedTeams),
      blueTeamIds: cleanTeamIds(this.manualBlueTeams)
    };

    this.matchService.createMatch(payload).subscribe({
      next: (res) => {
        alert(res.message);
        this.loadSchedule(this.manualMatchType); // Reload schedule
      },
      error: (e: any) => {
        console.error('Failed to create match', e);
        alert('Failed to create match: ' + (e.error?.message || e.message));
      }
    });
  }

  isSurrogate(match: MatchDetailDto, teamId: string | undefined): boolean {
    if (!match || !match.surrogateMap || !teamId) {
      return false;
    }
    return match.surrogateMap[teamId];
  }
}
