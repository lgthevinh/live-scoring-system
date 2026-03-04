import { Component, OnInit, signal, WritableSignal, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable, map, forkJoin, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import {
  MatchService,
  MockMatchService,
  ProdMatchService
} from '../../core/services/match.service';
import { environment } from '../../../environments/environment';
import { MatchDetailDto } from '../../core/models/match.model';
import { ScorekeeperService } from '../../core/services/scorekeeper.service';
import { BroadcastService } from '../../core/services/broadcast.service';
import { SyncService } from '../../core/services/sync.service';
import { ScoresheetComponent } from '../match-results/components/scoresheet/scoresheet.component';
import { ToastService } from '../../core/services/toast.service';
import { ScoreSubmitBufferService } from '../../core/services/score-submit-buffer.service';
import { RefereeService } from '../../core/services/referee.service';
import { TempScore } from '../../core/models/score.model';

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
  private matchService = inject(MatchService);
  private scorekeeper = inject(ScorekeeperService);
  private broadcastService = inject(BroadcastService);
  private syncService = inject(SyncService);
  private toastService = inject(ToastService);
  public bufferService = inject(ScoreSubmitBufferService);
  private refereeService = inject(RefereeService);

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
  private redScoreExistedBeforeEdit = false;
  private blueScoreExistedBeforeEdit = false;

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

  // Buffer Modal
  showCommitModal = signal<boolean>(false);

  // Override Confirmation Modal
  showOverrideConfirmModal = signal<boolean>(false);
  pendingOverrideAlliances: string[] = [];
  pendingSaveData: { redData?: any, blueData?: any } | null = null;

  // Temp Score Management
  redTempScores = signal<TempScore[]>([]);
  blueTempScores = signal<TempScore[]>([]);
  hasRedTempScore = computed(() => this.redTempScores().length > 0);
  hasBlueTempScore = computed(() => this.blueTempScores().length > 0);
  hasTempScores = computed(() => this.hasRedTempScore() || this.hasBlueTempScore());
  isLoadingTempScores = signal<boolean>(false);
  showTempScoreCommitModal = signal<boolean>(false);
  showTempScoreRejectModal = signal<boolean>(false);
  pendingTempScoreId: string | null = null;
  pendingTempScoreAlliance: 'red' | 'blue' | null = null;
  tempScoreRejectReason = signal<string>('');

  ngOnInit(): void {
    this.loadSchedule();
    this.syncService.syncPlayingMatches().subscribe({
      next: (matches) => {
        if (matches && matches.length > 0) {
          // Assume first match is active, second is loaded
          this.active.set(matches[0]);
          if (matches.length > 1) {
            this.loaded.set(matches[1]);
          } else {
            this.loaded.set(null);
          }
        }
      },
      error: (e) => console.error('Failed to sync playing matches', e.message)
    });
    this.broadcastService.subscribeToTopic("/topic/display/field/*/timer").subscribe({
      next: (msg) => {
        if (msg.payload && msg.payload.remainingSeconds !== undefined) {
          const newValue = msg.payload.remainingSeconds;

          // Only play match end sound when the match timer (not countdown) reaches 0
          // The match timer starts at 180+ seconds, countdown is 3 seconds
          const wasCountdown = this.previousTimerValue !== null && this.previousTimerValue <= 3;

          // If transitioning from countdown (0) to match time (180+), don't play sound
          // Only play match end sound when actual match time reaches 0
          if (this.previousTimerValue !== null &&
              this.previousTimerValue > 0 &&
              newValue === 0 &&
              !wasCountdown) {
            this.playMatchEndSound();
          }

          this.previousTimerValue = newValue;
          this.activeMatchTimer.set(newValue);
        }
      },
      error: (e) => console.error("Failed to subscribe to timer updates:", e)
    });
  }

  setTab(key: TabKey) {
    this.selectedTab.set(key);
    if (key === 'schedule') {
      this.loadSchedule();
    }
  }

  // ---- Data loading ----
  loadSchedule(matchType?: number) {
    const typeToLoad = matchType !== undefined ? matchType : this.viewMatchType;
    // Fetch matches WITH scores so we can edit them
    this.matchService.getMatches(typeToLoad, true).subscribe({
      next: (list) => this.schedule.set(list),
      error: (e) => console.error('Failed to load schedule', e)
    });
  }

  onViewMatchTypeChange() {
    this.loadSchedule();
  }

  // ---- Schedule row actions ----
  playMatch(match: MatchDetailDto) {
    // Backend: set next match on the field
    this.scorekeeper.setNextMatch(match.match.id).subscribe({
      next: () => this.loaded.set(match),
      error: (e) => {
        console.error('Failed to set next match', e);
        // Fallback for UI in case backend not ready
        this.loaded.set(match);
      }
    });
  }

  enterScores(match: MatchDetailDto) {
    this.editingMatch.set(match);

    // Track if scores existed before editing (for override confirmation)
    // A score exists if status === 1 (SCORED) AND rawScoreData has actual content
    // ScoreStatus: NOT_SCORED = 0, SCORED = 1
    const hasRedData = match.redScore?.rawScoreData && match.redScore.rawScoreData !== '{}' && match.redScore.rawScoreData.trim() !== '';
    const hasBlueData = match.blueScore?.rawScoreData && match.blueScore.rawScoreData !== '{}' && match.blueScore.rawScoreData.trim() !== '';

    // Explicit check for status === 1 (SCORED) - handles null/undefined correctly
    const redStatus = match.redScore?.status;
    const blueStatus = match.blueScore?.status;
    this.redScoreExistedBeforeEdit = redStatus === 1 && !!hasRedData;
    this.blueScoreExistedBeforeEdit = blueStatus === 1 && !!hasBlueData;

    // Initialize with current data to ensure we have something to save
    // even if the user doesn't edit anything (or if they only edit one side)
    let redLoaded = false;
    let blueLoaded = false;
    let parseError = false;

    try {
      if (match.redScore?.rawScoreData) {
        this.redScoreData = this.safeParse(match.redScore.rawScoreData);
        redLoaded = Object.keys(this.redScoreData).length > 0;
      } else {
        this.redScoreData = {};
      }
    } catch (e) {
      console.error('Failed to parse red score JSON', e);
      this.redScoreData = {};
      parseError = true;
    }

    try {
      if (match.blueScore?.rawScoreData) {
        this.blueScoreData = this.safeParse(match.blueScore.rawScoreData);
        blueLoaded = Object.keys(this.blueScoreData).length > 0;
      } else {
        this.blueScoreData = {};
      }
    } catch (e) {
      console.error('Failed to parse blue score JSON', e);
      this.blueScoreData = {};
      parseError = true;
    }


    // Show toast notifications
    if (parseError) {
      this.toastService.show('Error loading existing scores. Starting with empty scores.', 'error', 5000);
    } else if (redLoaded || blueLoaded) {
      const messages = [];
      if (redLoaded) messages.push('Red');
      if (blueLoaded) messages.push('Blue');
      this.toastService.show(`Preloaded existing scores: ${messages.join(' & ')} Alliance`, 'success', 3000);
    } else {
      this.toastService.show('No existing scores found. Starting with empty scores.', 'info', 3000);
    }

    // Check for temp scores after setting up the editing match
    this.checkForTempScores(match);

    this.setTab('score-edit');
  }

  // ---- Temp Score Management ----

  loadTempScores(matchId: string) {
    const match = this.schedule().find(m => m.match.id === matchId);
    if (match) {
      this.checkForTempScores(match);
    }
  }

  checkForTempScores(match: MatchDetailDto) {
    this.isLoadingTempScores.set(true);
    this.redTempScores.set([]);
    this.blueTempScores.set([]);

    const getRedTemps$ = match.redScore
      ? this.bufferService.getBackendTempScores(match.redScore.id)
      : of([]);

    const getBlueTemps$ = match.blueScore
      ? this.bufferService.getBackendTempScores(match.blueScore.id)
      : of([]);

    forkJoin({
      redTemps: getRedTemps$,
      blueTemps: getBlueTemps$
    }).pipe(
      finalize(() => this.isLoadingTempScores.set(false))
    ).subscribe({
      next: (results) => {
        if (results.redTemps && results.redTemps.length > 0) {
          this.redTempScores.set(results.redTemps);
          this.toastService.show('Pending temp score available for Red alliance', 'info', 3000);
        }
        if (results.blueTemps && results.blueTemps.length > 0) {
          this.blueTempScores.set(results.blueTemps);
          this.toastService.show('Pending temp score available for Blue alliance', 'info', 3000);
        }
      },
      error: (err) => {
        console.error('Error checking for temp scores:', err);
      }
    });
  }

  commitTempScore(tempScoreId: string, alliance: 'red' | 'blue') {
    this.scorekeeper.commitTempScore(tempScoreId, 'scorekeeper').subscribe({
      next: () => {
        this.toastService.show(`${alliance === 'red' ? 'Red' : 'Blue'} temp score committed successfully`, 'success');
        if (alliance === 'red') {
          this.redTempScores.set(this.redTempScores().filter(ts => ts.tempScoreId !== tempScoreId));
        } else {
          this.blueTempScores.set(this.blueTempScores().filter(ts => ts.tempScoreId !== tempScoreId));
        }
        this.loadSchedule();
      },
      error: (err) => {
        console.error('Failed to commit temp score:', err);
        this.toastService.show('Failed to commit temp score', 'error');
      }
    });
  }

  rejectTempScore(tempScoreId: string, alliance: 'red' | 'blue', reason: string) {
    this.bufferService.rejectTempScore(tempScoreId, 'scorekeeper', reason).subscribe({
      next: () => {
        this.toastService.show(`${alliance === 'red' ? 'Red' : 'Blue'} temp score rejected`, 'success');
        if (alliance === 'red') {
          this.redTempScores.set(this.redTempScores().filter(ts => ts.tempScoreId !== tempScoreId));
        } else {
          this.blueTempScores.set(this.blueTempScores().filter(ts => ts.tempScoreId !== tempScoreId));
        }
      },
      error: (err) => {
        console.error('Failed to reject temp score:', err);
        this.toastService.show('Failed to reject temp score', 'error');
      }
    });
  }

  getTempScoreById(tempScoreId: string, alliance: 'red' | 'blue'): TempScore | undefined {
    if (alliance === 'red') {
      return this.redTempScores().find(ts => ts.tempScoreId === tempScoreId);
    } else {
      return this.blueTempScores().find(ts => ts.tempScoreId === tempScoreId);
    }
  }

  openTempScoreCommitModal(tempScoreId: string, alliance: 'red' | 'blue') {
    this.pendingTempScoreId = tempScoreId;
    this.pendingTempScoreAlliance = alliance;
    this.showTempScoreCommitModal.set(true);
  }

  closeTempScoreCommitModal() {
    this.showTempScoreCommitModal.set(false);
    this.pendingTempScoreId = null;
    this.pendingTempScoreAlliance = null;
  }

  openTempScoreRejectModal(tempScoreId: string, alliance: 'red' | 'blue') {
    this.pendingTempScoreId = tempScoreId;
    this.pendingTempScoreAlliance = alliance;
    this.showTempScoreRejectModal.set(true);
  }

  closeTempScoreRejectModal() {
    this.showTempScoreRejectModal.set(false);
    this.pendingTempScoreId = null;
    this.pendingTempScoreAlliance = null;
    this.tempScoreRejectReason.set('');
  }

  confirmCommitTempScore() {
    if (!this.pendingTempScoreAlliance || !this.pendingTempScoreId) {
      return;
    }

    this.scorekeeper.commitTempScore(this.pendingTempScoreId, 'scorekeeper').subscribe({
      next: () => {
        this.toastService.show(`${this.pendingTempScoreAlliance === 'red' ? 'Red' : 'Blue'} temp score committed successfully`, 'success');
        if (this.pendingTempScoreAlliance === 'red') {
          this.redTempScores.set(this.redTempScores().filter(ts => ts.tempScoreId !== this.pendingTempScoreId));
        } else {
          this.blueTempScores.set(this.blueTempScores().filter(ts => ts.tempScoreId !== this.pendingTempScoreId));
        }
        // Reload schedule to reflect changes
        this.loadSchedule();
      },
      error: (err) => {
        console.error('Failed to commit temp score:', err);
        this.toastService.show('Failed to commit temp score', 'error');
      }
    });

    this.closeTempScoreCommitModal();
  }

  confirmRejectTempScore() {
    if (!this.pendingTempScoreAlliance || !this.pendingTempScoreId) {
      return;
    }

    const reason = this.tempScoreRejectReason() || 'Rejected by scorekeeper';

    this.bufferService.rejectTempScore(this.pendingTempScoreId, 'scorekeeper', reason).subscribe({
      next: () => {
        this.toastService.show(`${this.pendingTempScoreAlliance === 'red' ? 'Red' : 'Blue'} temp score rejected`, 'success');
        if (this.pendingTempScoreAlliance === 'red') {
          this.redTempScores.set(this.redTempScores().filter(ts => ts.tempScoreId !== this.pendingTempScoreId));
        } else {
          this.blueTempScores.set(this.blueTempScores().filter(ts => ts.tempScoreId !== this.pendingTempScoreId));
        }
        this.tempScoreRejectReason.set('');
      },
      error: (err) => {
        console.error('Failed to reject temp score:', err);
        this.toastService.show('Failed to reject temp score', 'error');
      }
    });

    this.closeTempScoreRejectModal();
  }

  applyTempScoreToEditor(tempScoreId: string, alliance: 'red' | 'blue') {
    const tempScore = this.getTempScoreById(tempScoreId, alliance);
    if (!tempScore) return;

    const scoreData = tempScore.scoreData;
    if (alliance === 'red') {
      this.redScoreData = { ...this.redScoreData, ...scoreData };
    } else {
      this.blueScoreData = { ...this.blueScoreData, ...scoreData };
    }

    this.toastService.show(`${alliance === 'red' ? 'Red' : 'Blue'} temp score data applied to editor`, 'success');
  }

  private safeParse(json: string): any {
    try {
      return JSON.parse(json);
    } catch (e) {
      console.error('Failed to parse score JSON', e);
      throw e; // Re-throw to handle in caller
    }
  }

  saveScores() {
    const m = this.editingMatch();

    if (!m) {
      console.error('No match is being edited.');
      this.toastService.show('Error: No match is being edited.', 'error');
      return;
    }

    // Check if there are existing scores that will be overridden
    const alliancesToOverride: string[] = [];

    // Check if red had existing score before editing (tracked in enterScores)
    if (this.redScoreExistedBeforeEdit) {
      alliancesToOverride.push('Red');
    }

    // Check if blue had existing score before editing
    if (this.blueScoreExistedBeforeEdit) {
      alliancesToOverride.push('Blue');
    }

    // If there are existing scores, show confirmation modal
    if (alliancesToOverride.length > 0) {
      this.openOverrideConfirmModal(alliancesToOverride, this.redScoreData, this.blueScoreData);
      return;
    }

    // No existing scores, proceed with save
    this.executeSaveScores(this.redScoreData, this.blueScoreData);
  }

  executeSaveScores(redData: any, blueData: any) {
    const m = this.editingMatch();
    if (!m) {
      this.toastService.show('Error: No match is being edited.', 'error');
      return;
    }

    this.isSaving.set(true);
    const requests: Observable<any>[] = [];

    // Submit red
    if (m.redScore) {
      requests.push(
        this.scorekeeper.overrideScore(m.match.id + "_R", redData).pipe(
          catchError(e => {
            console.error('Failed to update red score', e);
            return of({ error: true, alliance: 'red' });
          })
        )
      );
    }

    // Submit blue
    if (m.blueScore) {
      requests.push(
        this.scorekeeper.overrideScore(m.match.id + "_B", blueData).pipe(
          catchError(e => {
            console.error('Failed to update blue score', e);
            return of({ error: true, alliance: 'blue' });
          })
        )
      );
    }

    if (requests.length === 0) {
      console.warn('No score objects found to update. Match might not have scores initialized.');
      this.toastService.show('Error: No score objects found to update.', 'error');
      this.isSaving.set(false);
      return;
    }

    forkJoin(requests).pipe(
      finalize(() => {
        this.isSaving.set(false);
      })
    ).subscribe({
      next: (results) => {
        const errors = results.filter(r => r && r.error);
        if (errors.length > 0) {
          this.toastService.show('Some scores failed to save. Check console.', 'error');
        } else {
          // Success
          this.editingMatch.set(null);
          this.setTab('schedule');
          // Reload schedule to get updated scores
          this.loadSchedule();
          this.toastService.show('Scores saved successfully', 'success');
        }
      },
      error: (err) => {
        console.error('Error saving scores', err);
        this.toastService.show('Error saving scores', 'error');
        // isSaving is reset in finalize operator
      },
      complete: () => {
        this.redScoreExistedBeforeEdit = false;
        this.blueScoreExistedBeforeEdit = false;
        this.redTempScores.set([]);
        this.blueTempScores.set([]);
      }
    });
  }

  cancelEdit() {
    this.redScoreExistedBeforeEdit = false;
    this.blueScoreExistedBeforeEdit = false;
    this.redTempScores.set([]);
    this.blueTempScores.set([]);
    this.editingMatch.set(null);
    this.setTab('schedule');
  }

  onRedScoreChange(data: any) {
    this.redScoreData = data;
  }

  onBlueScoreChange(data: any) {
    this.blueScoreData = data;
  }

  // ---- Top buttons (Loaded section) ----
  loadNextMatch() {
    const list = this.schedule();
    if (!list.length) return;
    const current = this.loaded();
    const idx = current ? list.findIndex(m => m.match.id === current.match.id) : -1;
    const next = list[(idx + 1 + list.length) % list.length];
    // Optimistic UI update, then backend (if any)
    this.scorekeeper.setNextMatch(next.match.id).subscribe({
      next: () => this.loaded.set(next),
      error: () => {
        console.error('Failed to set next match');
        this.toastService.show('Failed to load next match', 'error');
      }
    });
  }

  showUpNext() {
    this.scorekeeper.showUpNext().subscribe({
      next: () => {},
      error: (e) => {
        console.error('Failed to show up next', e);
        this.toastService.show('Failed to show up next on display', 'error');
      }
    });
  }

  showCurrentMatch() {
    this.scorekeeper.showCurrentMatch().subscribe({
      next: () => {},
      error: (e) => {
        console.error('Failed to show current match', e);
        this.toastService.show('Failed to show current match on display', 'error');
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
    // Initialize AudioContext on user interaction
    if (!this.audioContext) {
      this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
    }

    // Resume audio context if suspended (browser autoplay policy)
    if (this.audioContext.state === 'suspended') {
      this.audioContext.resume().then(() => {
      }).catch(err => {
        console.error("Failed to resume AudioContext:", err);
      });
    }

    fetch('/assets/match_end.m4a')
      .then(response => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.arrayBuffer();
      })
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
    this.scorekeeper.activateMatch().subscribe({
      next: () => {
        this.active.set(toActivate);
        this.loaded.set(null);
      },
      error: (e) => {
        console.error('Failed to activate match', e);
        this.toastService.show('Failed to activate match', 'error');
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

    this.scorekeeper.startCurrentMatch().subscribe({
      next: () => {
        // Match is already active, no need to set again
        this.loaded.set(null); // Clear loaded match after starting
        this.previousTimerValue = null; // Reset timer tracking
      },
      error: (e) => {
        console.error('Failed to start current match', e);
        this.toastService.show('Failed to start match timer', 'error');
      }
    });

  }

  private initializeAudioContext(): void {
    // Initialize AudioContext on user interaction to unlock it for later playback
    if (!this.audioContext) {
      this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
    }

    // Resume audio context if suspended (browser autoplay policy)
    if (this.audioContext.state === 'suspended') {
      this.audioContext.resume().then(() => {
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

    // Execute abort immediately, then show undo toast
    this.scorekeeper.abortCurrentMatch().subscribe({
      next: () => {
        // Move match from active back to loaded
        this.loaded.set(toAbort);
        this.active.set(null);
        this.activeMatchTimer.set(null);
        this.previousTimerValue = null; // Reset timer tracking

        this.toastService.show(
          `Match ${toAbort.match.matchCode} aborted`,
          'info',
          5000
        );
      },
      error: (e) => {
        console.error('Failed to abort match', e);
        this.toastService.show('Failed to abort match: ' + e.message, 'error');
      }
    });
  }

  // ---- Buffer/Commit Manager Modal ----

  openCommitModal() {
    this.showCommitModal.set(true);
  }

  closeCommitModal() {
    this.showCommitModal.set(false);
  }

  // Override Confirmation Modal Methods
  openOverrideConfirmModal(alliances: string[], redData?: any, blueData?: any) {
    this.pendingOverrideAlliances = alliances;
    this.pendingSaveData = { redData, blueData };
    this.showOverrideConfirmModal.set(true);
  }

  closeOverrideConfirmModal() {
    this.showOverrideConfirmModal.set(false);
    this.pendingOverrideAlliances = [];
    this.pendingSaveData = null;
  }

  confirmOverrideSave() {
    this.showOverrideConfirmModal.set(false);
    // Execute the actual save
    this.executeSaveScores(this.pendingSaveData?.redData, this.pendingSaveData?.blueData);
  }

  submitBufferedSubmission(submissionId: string) {
    const submission = this.bufferService.buffer().find(s => s.id === submissionId);
    if (!submission) {
      console.error('Submission not found:', submissionId);
      return;
    }

    // If we have a tempScoreId, use the temp score commit flow
    if (submission.tempScoreId) {
      this.scorekeeper.commitTempScore(submission.tempScoreId, 'scorekeeper').subscribe({
        next: (res) => {
          this.bufferService.markAsSubmittedDirect(submissionId);
          this.toastService.show(`Committed ${submission.matchCode} ${submission.color} score from temp!`, 'success');
          this.loadSchedule();
        },
        error: (err) => {
          console.error('Failed to commit temp score:', err);
          this.bufferService.markAsError(submissionId, err?.error?.message || 'Failed to commit temp score');
          this.toastService.show(`Failed to commit ${submission.matchCode}: ${err?.error?.message || 'Unknown error'}`, 'error');
        }
      });
      return;
    }

    // Fallback: if no tempScoreId, use direct submission (legacy path)
    this.refereeService.submitFinalScore(submission.color, submission.allianceId, submission.payload).subscribe({
      next: (res) => {
        this.toastService.show(`Submitted ${submission.matchCode} ${submission.color} score!`, 'success');
        this.bufferService.markAsSubmittedDirect(submissionId);
        this.loadSchedule();
      },
      error: (err) => {
        console.error('Failed to submit score:', err);
        this.bufferService.markAsError(submissionId, err?.error?.message || 'Unknown error');
        this.toastService.show(`Failed to submit ${submission.matchCode}: ${err?.error?.message || 'Unknown error'}`, 'error');
      }
    });
  }

  submitAllPending() {
    const pending = this.bufferService.getPendingSubmissions();
    if (pending.length === 0) {
      this.toastService.show('No pending submissions to commit', 'info');
      return;
    }

    this.toastService.show(`Committing ${pending.length} pending submissions...`, 'info');

    pending.forEach(submission => {
      this.submitBufferedSubmission(submission.id);
    });
  }

  removeFromBuffer(submissionId: string) {
    this.bufferService.removeFromBuffer(submissionId);
    this.toastService.show('Removed from buffer', 'info');
  }

  commitAndPostLastMatch() {
    // Open the commit manager modal instead
    this.openCommitModal();
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
        this.toastService.show('Alliance Teams JSON must be an array.', 'error');
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
          this.toastService.show(res.message, 'success');
          this.loadSchedule(this.playoffType); // Reload schedule
        },
        error: (e: any) => {
          console.error('Failed to generate playoff schedule', e);
          this.toastService.show('Failed to generate playoff schedule: ' + (e.error?.message || e.message), 'error');
        }
      });
    } catch (e) {
      this.toastService.show('Invalid JSON format for Alliance Teams.', 'error');
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
        this.toastService.show(res.message, 'success');
        this.loadSchedule(this.manualMatchType); // Reload schedule
      },
      error: (e: any) => {
        console.error('Failed to create match', e);
        this.toastService.show('Failed to create match: ' + (e.error?.message || e.message), 'error');
      }
    });
  }

  isSurrogate(match: MatchDetailDto, teamId: string | undefined): boolean {
    if (!match || !match.surrogateMap || !teamId) {
      return false;
    }
    return match.surrogateMap[teamId];
  }

  hasMatchTempScore(match: MatchDetailDto): boolean {
    return this.redTempScores().length > 0 || this.blueTempScores().length > 0;
  }
}
