import { Component, OnInit, signal, WritableSignal } from '@angular/core';
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
    private scorekeeper: ScorekeeperService,
    private broadcastService: BroadcastService,
    private syncService: SyncService
  ) { }

  ngOnInit(): void {
    this.loadSchedule();
    this.syncService.syncPlayingMatches().subscribe({
      next: (matches) => {
        console.log('Synced playing matches', matches);

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
        console.log("Received timer update:", msg);
        if (msg.payload && msg.payload.remainingSeconds !== undefined) {
          const newValue = msg.payload.remainingSeconds;

          // Debug logging for timer values
          console.log(`Timer: previous=${this.previousTimerValue}, new=${newValue}`);

          // Only play match end sound when the match timer (not countdown) reaches 0
          // The match timer starts at 180+ seconds, countdown is 3 seconds
          const wasCountdown = this.previousTimerValue !== null && this.previousTimerValue <= 3;
          const isMatchTime = newValue > 3;

          console.log(`Timer check: wasCountdown=${wasCountdown}, isMatchTime=${isMatchTime}`);

          // If transitioning from countdown (0) to match time (180+), don't play sound
          // Only play match end sound when actual match time reaches 0
          if (this.previousTimerValue !== null &&
              this.previousTimerValue > 0 &&
              newValue === 0 &&
              !wasCountdown) {
            console.log("Triggering match end sound!");
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
        this.scorekeeper.overrideScore(m.match.id + "_R", this.redScoreData).pipe(
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
        this.scorekeeper.overrideScore(m.match.id + "_B", this.blueScoreData).pipe(
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
    // Optimistic UI update, then backend (if any)
    this.scorekeeper.setNextMatch(next.match.id).subscribe({
      next: () => this.loaded.set(next),
      error: () => {
        console.error('Failed to set next match');
        alert('Failed to load next match');
      }
    });
  }

  showUpNext() {
    this.scorekeeper.showUpNext().subscribe({
      next: () => console.debug('Show up next command sent'),
      error: (e) => {
        console.error('Failed to show up next', e);
        alert('Failed to show up next on display');
      }
    });
  }

  showCurrentMatch() {
    this.scorekeeper.showCurrentMatch().subscribe({
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
    this.scorekeeper.activateMatch().subscribe({
      next: () => {
        this.active.set(toActivate);
        this.loaded.set(null);
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

    this.scorekeeper.startCurrentMatch().subscribe({
      next: () => {
        // Match is already active, no need to set again
        this.loaded.set(null); // Clear loaded match after starting
        this.previousTimerValue = null; // Reset timer tracking
        console.debug('Match timer started for active match');
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

    this.scorekeeper.abortCurrentMatch().subscribe({
      next: () => {
        // Move match from active back to loaded
        this.loaded.set(toAbort);
        this.active.set(null);
        this.activeMatchTimer.set(null);
        this.previousTimerValue = null; // Reset timer tracking
        console.debug('Match aborted successfully');
      },
      error: (e) => {
        console.error('Failed to abort match', e);
        alert('Failed to abort match: ' + e.message);
      }
    });
  }

  commitAndPostLastMatch() {
    this.scorekeeper.commitFinalScore().subscribe({
      next: () => {
        console.debug('Committed last match results')
        alert('Successfully committed last match results');
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
