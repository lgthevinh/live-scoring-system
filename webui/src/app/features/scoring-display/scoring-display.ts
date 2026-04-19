import { Component, OnDestroy, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { LiveWsService } from '../../core/services/live-ws.service';
import { DisplayControlAction } from '../../core/services/match-control.service';
import { SyncService } from '../../core/services/sync.service';
import { Team } from '../../core/models/team.model';
import { MatchDetailDto } from '../../core/models/match.model';
import {
  DisplayControlPayload,
  MatchStatePayload,
  ScoreUpdatePayload,
} from '../../core/services/ws-types';

/**
 * Field display screen. Consumes the unified {@code /ws/live} feed:
 *
 * <ul>
 *   <li>{@code MATCH_STATE} &rarr; drive the timer and set team rosters.</li>
 *   <li>{@code SCORE_UPDATE} &rarr; alliance totals (payload carries both
 *       {@code r} and {@code b}).</li>
 *   <li>{@code DISPLAY_CONTROL} &rarr; numeric action codes
 *       (see {@link DisplayControlAction}). SHOW_PREVIEW switches to
 *       "upnext" mode, SHOW_MATCH/UPDATE_MATCH back to "match" mode.</li>
 * </ul>
 *
 * <p>Replaces the legacy per-field topic subscriptions. The backend no
 * longer shards by field id &mdash; everyone watching a given scoring
 * laptop gets the same stream. The field-bind selector is retained for
 * the REST {@code getCurrentMatchField} call only.
 */
@Component({
    selector: 'app-field-display',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './scoring-display.html',
    styleUrl: './scoring-display.css'
})
export class ScoringDisplay implements OnInit, OnDestroy {
    // Display mode: 'match' shows scoring timer, 'upnext' shows up-next preview
    displayMode: WritableSignal<string> = signal('match');

    // Display state
    durationSec: WritableSignal<number> = signal(180); // 3 minutes
    timeLeft: WritableSignal<number> = signal(this.durationSec());
    running: WritableSignal<boolean> = signal(false);

    // Teams and scores
    redTeams: WritableSignal<Team[]> = signal([]);
    blueTeams: WritableSignal<Team[]> = signal([]);
    redScore: WritableSignal<number> = signal(0);
    blueScore: WritableSignal<number> = signal(0);

    // Up Next data
    upNextMatchCode: WritableSignal<string> = signal('');
    upNextFieldNumber: WritableSignal<number> = signal(0);
    upNextScheduledTime: WritableSignal<string> = signal('');
    upNextRedTeams: WritableSignal<Team[]> = signal([]);
    upNextBlueTeams: WritableSignal<Team[]> = signal([]);
    hasUpNext: WritableSignal<boolean> = signal(false);

    // Fullscreen state
    isFullscreen: WritableSignal<boolean> = signal(false);

    // Gear control fade
    controlsVisible: WritableSignal<boolean> = signal(true);
    private hideTimer: any = null;
    private tickTimer: any = null;

    private readonly subs: Subscription[] = [];

    // Audio playback
    private audioContext: AudioContext | null = null;
    private preloadedAudioBuffer: AudioBuffer | null = null;
    private html5Audio: HTMLAudioElement | null = null;
    soundEnabled: WritableSignal<boolean> = signal(false);
    showSoundPermissionPopup: WritableSignal<boolean> = signal(true);
    outputDeviceId: WritableSignal<string> = signal('default');
    availableAudioDevices: WritableSignal<Array<{deviceId: string, label: string}>> = signal([]);

    // -----------------------------------------------------------------
    // Sound-playback UI state (STUBS).
    //
    // The template references {@code isSoundPlaying}, {@code audioLevels},
    // {@code volume}, {@code stopMatchSound}, and {@code onVolumeChange}
    // for a sound visualizer + volume slider. The actual playback is done
    // via the AudioContext below; these are minimal signals to keep the
    // template bound. {@code stopMatchSound} cancels playback by closing
    // the AudioContext source if one is alive.
    // -----------------------------------------------------------------
    isSoundPlaying: WritableSignal<boolean> = signal(false);
    /** 8 bars for the equalizer-style visualizer. */
    audioLevels: WritableSignal<number[]> = signal([0, 0, 0, 0, 0, 0, 0, 0]);
    /** Volume in 0..100 (HTML range input). */
    volume: WritableSignal<number> = signal(80);

    /** Stop currently-playing match sound. */
    stopMatchSound(): void {
        try {
            if (this.html5Audio) {
                this.html5Audio.pause();
                this.html5Audio.currentTime = 0;
            }
            if (this.audioContext && this.audioContext.state === 'running') {
                this.audioContext.suspend().catch(() => {});
            }
        } catch (e) {
            console.warn('[ScoringDisplay] stopMatchSound failed:', e);
        }
        this.isSoundPlaying.set(false);
    }

    /** Volume slider handler. */
    onVolumeChange(eventOrValue: Event | number): void {
        let v: number;
        if (eventOrValue instanceof Event) {
            const el = eventOrValue.target as HTMLInputElement | null;
            v = el ? parseInt(el.value, 10) : this.volume();
        } else {
            v = eventOrValue;
        }
        if (!Number.isFinite(v)) return;
        const clamped = Math.max(0, Math.min(100, Math.floor(v)));
        this.volume.set(clamped);
        if (this.html5Audio) this.html5Audio.volume = clamped / 100;
    }

    fieldBindValue: number = 0;

    constructor(
        private syncService: SyncService,
        private liveWs: LiveWsService
    ) { }

    private onFullscreenChange = () => {
        const isFs =
            !!document.fullscreenElement ||
            !!(document as any).webkitFullscreenElement ||
            !!(document as any).mozFullScreenElement ||
            !!(document as any).msFullscreenElement;
        this.isFullscreen.set(isFs);
    };

    ngOnInit(): void {
        console.log('=== ScoringDisplay ngOnInit ===');

        this.syncService.getCurrentMatchField(0).subscribe({
            next: (match) => {
                if (match !== null) {
                    this.redTeams.set(match.redTeams);
                    this.blueTeams.set(match.blueTeams);
                    this.redScore.set(match.redScore?.totalScore || 0);
                    this.blueScore.set(match.blueScore?.totalScore || 0);
                }
            },
            error: (err) => {
                console.error("Error fetching current match field data:", err.message);
            }
        });

        this.subscribeToLiveFeed();
        this.preloadSound();
        this.showSoundPermissionPopupIfNeeded();

        this.resetHideTimer();
        document.addEventListener('fullscreenchange', this.onFullscreenChange);
        document.addEventListener('webkitfullscreenchange' as any, this.onFullscreenChange as any);
        document.addEventListener('mozfullscreenchange' as any, this.onFullscreenChange as any);
        document.addEventListener('MSFullscreenChange' as any, this.onFullscreenChange as any);
    }

    ngOnDestroy(): void {
        this.clearTick();
        this.clearHideTimer();
        this.subs.forEach(s => s.unsubscribe());
        document.removeEventListener('fullscreenchange', this.onFullscreenChange);
        document.removeEventListener('webkitfullscreenchange' as any, this.onFullscreenChange as any);
        document.removeEventListener('mozfullscreenchange' as any, this.onFullscreenChange as any);
        document.removeEventListener('MSFullscreenChange' as any, this.onFullscreenChange as any);
    }

    // ========== Fullscreen ==========
    toggleFullscreen() {
        if (!this.isFullscreen()) {
            this.enterFullscreen();
        } else {
            this.exitFullscreen();
        }
    }

    private enterFullscreen() {
        const el: any = document.documentElement;
        const req =
            el.requestFullscreen?.bind(el) ||
            el.webkitRequestFullscreen?.bind(el) ||
            el.mozRequestFullScreen?.bind(el) ||
            el.msRequestFullscreen?.bind(el);
        if (req) {
            try {
                const p = req();
                if (p && typeof p.then === 'function') {
                    (p as Promise<void>).catch(err => console.warn('Fullscreen request failed:', err));
                }
            } catch (e) {
                console.warn('Fullscreen request threw:', e);
            }
        }
    }

    private exitFullscreen() {
        const anyDoc: any = document;
        const exit =
            document.exitFullscreen?.bind(document) ||
            anyDoc.webkitExitFullscreen?.bind(anyDoc) ||
            anyDoc.mozCancelFullScreen?.bind(anyDoc) ||
            anyDoc.msExitFullscreen?.bind(anyDoc);
        if (exit) {
            try {
                const p = exit();
                if (p && typeof p.then === 'function') {
                    (p as Promise<void>).catch(err => console.warn('Exit fullscreen failed:', err));
                }
            } catch (e) {
                console.warn('Exit fullscreen threw:', e);
            }
        }
    }

    /**
     * Field selector only drives which match we REST-fetch; the WS feed is
     * global. Kept for backward compatibility with the existing UI.
     */
    onFieldBindChange(event: Event) {
        const target = event.target as HTMLSelectElement;
        this.fieldBindValue = target.value ? parseInt(target.value, 10) : 0;

        this.syncService.getCurrentMatchField(this.fieldBindValue).subscribe({
            next: (match) => {
                if (match !== null) {
                    this.redTeams.set(match.redTeams);
                    this.blueTeams.set(match.blueTeams);
                }
            },
            error: (err) => {
                console.error("Error fetching current match field data:", err.message);
            }
        });
    }

    // ========== Timer ==========
    start() {
        if (this.running()) return;
        this.timeLeft.set(this.durationSec());
        this.running.set(true);
        this.clearTick();
        this.tickTimer = setInterval(() => {
            const t = this.timeLeft() - 1;
            this.timeLeft.set(t);
            if (t <= 0) {
                this.stop();
                this.timeLeft.set(0);
            }
        }, 1000);
    }

    stop() {
        this.running.set(false);
        this.clearTick();
    }

    reset() {
        this.stop();
        this.timeLeft.set(this.durationSec());
    }

    setDurationFromForm(minutes: number, seconds: number) {
        const total = Math.max(0, Math.floor(minutes) * 60 + Math.floor(seconds));
        this.durationSec.set(total);
        if (!this.running()) this.timeLeft.set(total);
    }

    mmss(): string {
        const total = Math.max(0, this.timeLeft());
        const m = Math.floor(total / 60);
        const s = total % 60;
        return `${m}:${s.toString().padStart(2, '0')}`;
    }

    // ========== Controls fade/show ==========
    revealControls() {
        this.controlsVisible.set(true);
        this.resetHideTimer();
    }

    onAnyInteract() {
        this.revealControls();
        if (!this.soundEnabled()) {
            this.enableSound();
        }
    }

    private resetHideTimer() {
        this.clearHideTimer();
        this.hideTimer = setTimeout(() => this.controlsVisible.set(false), 5000);
    }

    private clearHideTimer() {
        if (this.hideTimer) {
            clearTimeout(this.hideTimer);
            this.hideTimer = null;
        }
    }

    private clearTick() {
        if (this.tickTimer) {
            clearInterval(this.tickTimer);
            this.tickTimer = null;
        }
    }

    // ========== WS feed ==========

    private subscribeToLiveFeed(): void {
        // SNAPSHOT: replay of the last broadcast state so a reconnecting
        // display immediately shows the right thing.
        this.subs.push(
            this.liveWs.snapshot$().subscribe({
                next: (snap) => {
                    if (snap.matchState) {
                        this.applyMatchState(snap.matchState);
                    }
                    if (snap.lastScoreRed) {
                        this.applyScore(snap.lastScoreRed);
                    }
                    if (snap.lastScoreBlue) {
                        this.applyScore(snap.lastScoreBlue);
                    }
                    if (snap.lastDisplay) {
                        this.applyDisplayAction(snap.lastDisplay);
                    }
                },
                error: (err) => console.error('FieldDisplay snapshot error:', err)
            })
        );

        this.subs.push(
            this.liveWs.matchState$().subscribe({
                next: (payload) => this.applyMatchState(payload),
                error: (err) => console.error('FieldDisplay match state error:', err)
            })
        );

        this.subs.push(
            this.liveWs.scoreUpdate$().subscribe({
                next: (payload) => this.applyScore(payload),
                error: (err) => console.error('FieldDisplay score update error:', err)
            })
        );

        this.subs.push(
            this.liveWs.displayControl$().subscribe({
                next: (payload) => this.applyDisplayAction(payload),
                error: (err) => console.error('FieldDisplay display control error:', err)
            })
        );
    }

    private applyMatchState(state: MatchStatePayload): void {
        if (state.timerSecondsRemaining !== undefined && state.timerSecondsRemaining !== null) {
            this.timeLeft.set(state.timerSecondsRemaining);
        }
    }

    private applyScore(score: ScoreUpdatePayload): void {
        const red = (score.r as any)?.totalScore;
        const blue = (score.b as any)?.totalScore;
        if (typeof red === 'number') {
            this.redScore.set(red);
        }
        if (typeof blue === 'number') {
            this.blueScore.set(blue);
        }
    }

    /**
     * Handle the server's numeric DisplayControlAction. The legacy string
     * codes (SHOW_TIMER, SHOW_UPNEXT, SHOW_MATCH) mapped loosely to
     * SHOW_MATCH (1) / SHOW_PREVIEW (2); we collapse to the new enum.
     */
    private applyDisplayAction(ctl: DisplayControlPayload): void {
        const data = ctl.data as MatchDetailDto | undefined;
        switch (ctl.action) {
            case DisplayControlAction.SHOW_MATCH:
            case DisplayControlAction.UPDATE_MATCH:
                if (data) {
                    this.redTeams.set(data.redTeams || []);
                    this.blueTeams.set(data.blueTeams || []);
                    this.redScore.set(data.redScore?.totalScore || 0);
                    this.blueScore.set(data.blueScore?.totalScore || 0);
                }
                this.displayMode.set('match');
                break;
            case DisplayControlAction.SHOW_PREVIEW:
                if (data) {
                    this.applyUpNextData(data);
                }
                this.displayMode.set('upnext');
                break;
            case DisplayControlAction.SHOW_BLANK:
                this.displayMode.set('match');
                this.redTeams.set([]);
                this.blueTeams.set([]);
                this.redScore.set(0);
                this.blueScore.set(0);
                break;
            case DisplayControlAction.SHOW_RESULT:
            case DisplayControlAction.SHOW_RANKING:
                // No dedicated UI for these yet; leave current mode untouched.
                break;
            case DisplayControlAction.UPDATE_SCORE:
                if (data) {
                    this.redScore.set(data.redScore?.totalScore || 0);
                    this.blueScore.set(data.blueScore?.totalScore || 0);
                }
                break;
            default:
                console.log('FieldDisplay: unhandled display action', ctl.action);
        }
    }

    // ========== Up Next Helpers ==========
    private applyUpNextData(match: MatchDetailDto): void {
        if (!match) {
            this.hasUpNext.set(false);
            return;
        }
        this.upNextMatchCode.set(match.match?.matchCode || '');
        this.upNextFieldNumber.set(match.match?.fieldNumber || 0);
        this.upNextScheduledTime.set(match.match?.matchStartTime || '');
        this.upNextRedTeams.set(match.redTeams || []);
        this.upNextBlueTeams.set(match.blueTeams || []);
        this.hasUpNext.set(true);
    }

    formatScheduledTime(): string {
        if (!this.upNextScheduledTime()) return '';
        try {
            const date = new Date(this.upNextScheduledTime());
            return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        } catch {
            return this.upNextScheduledTime();
        }
    }

    // ========== Sound ==========

    private showSoundPermissionPopupIfNeeded(): void {
        setTimeout(() => {
            const modalElement = document.getElementById('soundPermissionModal');
            if (modalElement) {
                modalElement.classList.add('show', 'd-block');
                modalElement.style.display = 'block';
                modalElement.setAttribute('aria-modal', 'true');
                modalElement.setAttribute('role', 'dialog');

                const backdrop = document.createElement('div');
                backdrop.className = 'modal-backdrop fade show';
                backdrop.id = 'soundPermissionBackdrop';
                document.body.appendChild(backdrop);
                document.body.classList.add('modal-open');
                document.body.style.overflow = 'hidden';
            }
        }, 100);
    }

    enableSound(): void {
        this.soundEnabled.set(true);
        if (!this.audioContext) {
            try {
                this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
                const deviceId = this.outputDeviceId();
                const audioContext = this.audioContext as any;
                if (typeof audioContext.setSinkId === 'function') {
                    audioContext.setSinkId(deviceId).catch((err: any) => {
                        console.warn('Failed to set output device:', err);
                    });
                }
            } catch (e) {
                console.error('Failed to create AudioContext:', e);
            }
        }
        if (this.audioContext && this.audioContext.state === 'suspended') {
            this.audioContext.resume().catch(err => {
                console.error('Failed to resume AudioContext:', err);
            });
        }
    }

    onOutputDeviceChange(event: Event): void {
        const target = event.target as HTMLSelectElement;
        const deviceId = target.value;
        this.outputDeviceId.set(deviceId);
        if (this.audioContext) {
            const audioContext = this.audioContext as any;
            if (typeof audioContext.setSinkId === 'function') {
                audioContext.setSinkId(deviceId).catch((err: any) => {
                    console.error('Failed to change audio output device:', err);
                });
            }
        }
        this.preloadSound();
    }

    refreshAudioDevices(): void {
        if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) {
            return;
        }
        navigator.mediaDevices.getUserMedia({ audio: true })
            .then(() => navigator.mediaDevices.enumerateDevices())
            .then(devices => {
                const audioOutputDevices = devices.filter(d => d.kind === 'audiooutput');
                const deviceList = audioOutputDevices.map(d => ({
                    deviceId: d.deviceId,
                    label: d.label || `Device ${d.deviceId.substring(0, 8)}`
                }));
                deviceList.unshift({ deviceId: 'default', label: 'System Default' });
                this.availableAudioDevices.set(deviceList);
            })
            .catch(err => {
                console.error('Error enumerating devices:', err);
                this.availableAudioDevices.set([{ deviceId: 'default', label: 'System Default' }]);
            });
    }

    onSoundPermissionResponse(enable: boolean): void {
        if (enable) {
            this.enableSound();
        } else {
            this.soundEnabled.set(false);
        }
        this.showSoundPermissionPopup.set(false);
        const modalElement = document.getElementById('soundPermissionModal');
        if (modalElement) {
            modalElement.classList.remove('show', 'd-block');
            modalElement.style.display = 'none';
            modalElement.removeAttribute('aria-modal');
            modalElement.removeAttribute('role');
        }
        const backdrop = document.getElementById('soundPermissionBackdrop');
        if (backdrop) {
            backdrop.remove();
        }
        document.body.classList.remove('modal-open');
        document.body.style.overflow = '';
    }

    playMatchSound(): void {
        if (!this.soundEnabled()) {
            this.enableSound();
        }
        this.playMatchStartSound();
    }

    private preloadSound(): void {
        this.html5Audio = new Audio('assets/MatchSoundEffect.m4a');
        this.html5Audio.preload = 'auto';
        this.html5Audio.load();
        try {
            this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
            fetch('assets/MatchSoundEffect.m4a')
                .then(response => response.arrayBuffer())
                .then(arrayBuffer => {
                    this.audioContext?.decodeAudioData(arrayBuffer, (audioBuffer) => {
                        this.preloadedAudioBuffer = audioBuffer;
                    });
                })
                .catch(err => console.warn('Failed to preload sound with AudioContext:', err));
        } catch (e) {
            console.warn('AudioContext not available:', e);
        }
    }

    private playMatchStartSound(): void {
        if (!this.soundEnabled()) {
            return;
        }
        if (this.outputDeviceId() !== 'default') {
            this.playWithAudioContext();
            return;
        }
        if (this.html5Audio) {
            try {
                this.html5Audio.currentTime = 0;
                const playPromise = this.html5Audio.play();
                if (playPromise !== undefined) {
                    playPromise.catch(err => {
                        console.warn('HTML5 Audio play failed, trying AudioContext:', err);
                        this.enableSound();
                        this.playWithAudioContext();
                    });
                }
                return;
            } catch (e) {
                console.warn('HTML5 Audio error:', e);
            }
        }
        this.playWithAudioContext();
    }

    private playWithAudioContext(): void {
        if (!this.audioContext) {
            try {
                this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
            } catch (e) {
                console.error('AudioContext not supported:', e);
                return;
            }
        }
        if (this.audioContext.state === 'suspended') {
            this.audioContext.resume()
                .then(() => this.playBuffer())
                .catch(err => console.error('Failed to resume AudioContext:', err));
        } else {
            this.playBuffer();
        }
    }

    private playBuffer(): void {
        if (this.preloadedAudioBuffer && this.audioContext) {
            try {
                const source = this.audioContext.createBufferSource();
                source.buffer = this.preloadedAudioBuffer;
                source.connect(this.audioContext.destination);
                source.start(0);
            } catch (e) {
                console.error('Failed to play with AudioContext:', e);
            }
        } else {
            fetch('assets/MatchSoundEffect.m4a')
                .then(response => response.arrayBuffer())
                .then(arrayBuffer => {
                    if (this.audioContext) {
                        this.audioContext.decodeAudioData(arrayBuffer, (audioBuffer) => {
                            const source = this.audioContext!.createBufferSource();
                            source.buffer = audioBuffer;
                            source.connect(this.audioContext!.destination);
                            source.start(0);
                        });
                    }
                })
                .catch(err => console.error('Failed to load and play sound:', err));
        }
    }
}
