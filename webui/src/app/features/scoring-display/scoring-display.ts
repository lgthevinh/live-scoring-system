import { Component, OnDestroy, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {BroadcastService} from "../../core/services/broadcast.service";
import {FieldDisplayCommand} from '../../core/define/FieldDisplayCommand';
import {SyncService} from '../../core/services/sync.service';
import {Team} from '../../core/models/team.model';
import {MatchDetailDto} from '../../core/models/match.model';

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

    // Teams and scores (placeholder values; wire to your services later)
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

    // Audio playback
    private audioContext: AudioContext | null = null;
    private soundPlayedForCurrentMatch: boolean = false;
    private preloadedAudioBuffer: AudioBuffer | null = null;
    private html5Audio: HTMLAudioElement | null = null;
    private isPlayingStartSound: boolean = false;
    private currentAudioSource: AudioBufferSourceNode | null = null;
    soundEnabled: WritableSignal<boolean> = signal(false); // Sound disabled by default (requires user gesture)
    showSoundPermissionPopup: WritableSignal<boolean> = signal(true); // Show popup by default
    outputDeviceId: WritableSignal<string> = signal('default'); // Output device ID
    availableAudioDevices: WritableSignal<Array<{deviceId: string, label: string}>> = signal([]);

    fieldBindValue: number = 0;

    constructor(
        private syncService: SyncService,
        private broadcastService: BroadcastService
    ) { }

    // Track fullscreen changes
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
        console.log('Current URL:', window.location.href);
        console.log('WebSocket URL expected:', 'ws://' + window.location.host + '/ws');

        this.syncService.getCurrentMatchField(0).subscribe({
            next: (match) => {
                console.log("Fetched current match field data:", match);

                if (match !== null) {
                    this.redTeams.set(match.redTeams);
                    this.blueTeams.set(match.blueTeams);
                    this.redScore.set(match.redScore?.totalScore || 0);
                    this.blueScore.set(match.blueScore?.totalScore || 0);
                }},
            error: (err) => {
                console.error("Error fetching current match field data:", err.message);
            }
        })

        this.subscribeToFieldTopic(0);

        // Preload sound file for mobile compatibility
        this.preloadSound();

        // Show sound permission popup automatically using vanilla JS (Bootstrap-compatible)
        setTimeout(() => {
            const modalElement = document.getElementById('soundPermissionModal');
            if (modalElement) {
                // Show the modal by adding Bootstrap classes
                modalElement.classList.add('show');
                modalElement.classList.add('d-block');
                modalElement.style.display = 'block';
                modalElement.setAttribute('aria-modal', 'true');
                modalElement.setAttribute('role', 'dialog');

                // Add backdrop
                let backdrop = document.createElement('div');
                backdrop.className = 'modal-backdrop fade show';
                backdrop.id = 'soundPermissionBackdrop';
                document.body.appendChild(backdrop);
                document.body.classList.add('modal-open');
                document.body.style.overflow = 'hidden';

                console.log('=== Sound permission popup shown ===');
            }
        }, 100);

        // Notify that the field display is active
        this.resetHideTimer();
        document.addEventListener('fullscreenchange', this.onFullscreenChange);
        // Safari/legacy vendor events (no-ops if unsupported)
        document.addEventListener('webkitfullscreenchange' as any, this.onFullscreenChange as any);
        document.addEventListener('mozfullscreenchange' as any, this.onFullscreenChange as any);
        document.addEventListener('MSFullscreenChange' as any, this.onFullscreenChange as any);
    }

    ngOnDestroy(): void {
        this.clearTick();
        this.clearHideTimer();
        document.removeEventListener('fullscreenchange', this.onFullscreenChange);
        document.removeEventListener('webkitfullscreenchange' as any, this.onFullscreenChange as any);
        document.removeEventListener('mozfullscreenchange' as any, this.onFullscreenChange as any);
        document.removeEventListener('MSFullscreenChange' as any, this.onFullscreenChange as any);
    }

    // ========== Fullscreen Button ==========
    toggleFullscreen() {
        if (!this.isFullscreen()) {
            this.enterFullscreen();
        } else {
            this.exitFullscreen();
        }
    }

    private enterFullscreen() {
        // Prefer the whole document; swap to a specific element if you want only the canvas:
        // const el = document.querySelector('.frame') as HTMLElement || document.documentElement;
        const el: any = document.documentElement;

        const req =
            el.requestFullscreen?.bind(el) ||
            el.webkitRequestFullscreen?.bind(el) ||   // Safari
            el.mozRequestFullScreen?.bind(el) ||      // Firefox old
            el.msRequestFullscreen?.bind(el);         // IE/Edge old

        if (req) {
            try {
                const p = req();
                if (p && typeof p.then === 'function') {
                    (p as Promise<void>).catch(err => console.warn('Fullscreen request failed:', err));
                }
            } catch (e) {
                console.warn('Fullscreen request threw:', e);
            }
        } else {
            console.warn('Fullscreen API not supported on this element/browser.');
        }
    }

    private exitFullscreen() {
        const anyDoc: any = document;
        const exit =
            document.exitFullscreen?.bind(document) ||
            anyDoc.webkitExitFullscreen?.bind(anyDoc) ||  // Safari
            anyDoc.mozCancelFullScreen?.bind(anyDoc) ||   // Firefox old
            anyDoc.msExitFullscreen?.bind(anyDoc);        // IE/Edge old

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

    onFieldBindChange(event: Event) {
        const target = event.target as HTMLSelectElement;
        this.fieldBindValue = target.value ? parseInt(target.value, 10) : 0;

        // Unsubscribe from all previous field topics
        this.broadcastService.unsubscribeAll();
        this.subscribeToFieldTopic(this.fieldBindValue!);

        this.syncService.getCurrentMatchField(this.fieldBindValue).subscribe({
            next: (match) => {
                console.log("Fetched current match field data:", match);

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

    // ======================================

    // Timer logic - instant start
    start() {
        if (this.running()) return;

        // Start main match timer immediately
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

    // Formatting
    mmss(): string {
        const total = Math.max(0, this.timeLeft());
        const m = Math.floor(total / 60);
        const s = total % 60;
        return `${m}:${s.toString().padStart(2, '0')}`;
    }

    // Controls fade/show
    revealControls() {
        this.controlsVisible.set(true);
        this.resetHideTimer();
    }

    onAnyInteract() {
        this.revealControls();

        // Auto-enable sound on first user interaction (browser requirement)
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

    private subscribeToFieldTopic(fieldId: number) {
        console.log('=== subscribeToFieldTopic called with fieldId:', fieldId, '===');
        if (fieldId === null || fieldId === undefined) return;

        let timerTopic: string;
        let commandTopic: string;
        let scoreTopicRed: string;
        let scoreTopicBlue: string;
        let soundTopic: string;

        if (fieldId === 0) {
            timerTopic = `/topic/display/field/*/timer`;
            commandTopic = `/topic/display/field/*/command`;
            scoreTopicRed = `/topic/live/field/*/score/red`;
            scoreTopicBlue = `/topic/live/field/*/score/blue`;
            soundTopic = `/topic/display/field/*/sound`;
        } else {
            timerTopic = `/topic/display/field/${fieldId}/timer`;
            commandTopic = `/topic/display/field/${fieldId}/command`;
            scoreTopicRed = `/topic/live/field/${fieldId}/score/red`;
            scoreTopicBlue = `/topic/live/field/${fieldId}/score/blue`;
            soundTopic = `/topic/display/field/${fieldId}/sound`;
        }

        console.log('Subscribing to topics:', { timerTopic, commandTopic, scoreTopicRed, scoreTopicBlue, soundTopic });

        this.broadcastService.subscribeToTopic(commandTopic).subscribe({
            next: (msg) => {
                console.log("FieldDisplay received message:", msg);
                if (msg.type === FieldDisplayCommand.SHOW_TIMER) {
                    console.log("FieldDisplay SHOW_TIMER command received");
                    this.displayMode.set('match');

                    this.redTeams.set(msg.payload.redTeams);
                    this.blueTeams.set(msg.payload.blueTeams);

                    this.redScore.set(0);
                    this.blueScore.set(0);
                } else if (msg.type === FieldDisplayCommand.SHOW_UPNEXT) {
                    console.log("FieldDisplay SHOW_UPNEXT command received");
                    this.applyUpNextData(msg.payload);
                    this.displayMode.set('upnext');
                } else if (msg.type === FieldDisplayCommand.SHOW_MATCH) {
                    console.log("FieldDisplay SHOW_MATCH command received");
                    if (msg.payload) {
                        this.redTeams.set(msg.payload.redTeams);
                        this.blueTeams.set(msg.payload.blueTeams);
                        this.redScore.set(msg.payload.redScore?.totalScore || 0);
                        this.blueScore.set(msg.payload.blueScore?.totalScore || 0);
                    }
                    this.displayMode.set('match');
                }
            },
            error: (err) => {
                console.error("FieldDisplay message error:", err);
            }
        });

        // Also subscribe to the broadcast-all command topic for display commands from match-control
        if (fieldId !== 0) {
            this.broadcastService.subscribeToTopic('/topic/display/field/0/command').subscribe({
                next: (msg) => {
                    console.log("FieldDisplay received broadcast-all command:", msg);
                    if (msg.type === FieldDisplayCommand.SHOW_UPNEXT) {
                        this.applyUpNextData(msg.payload);
                        this.displayMode.set('upnext');
                    } else if (msg.type === FieldDisplayCommand.SHOW_MATCH) {
                        if (msg.payload) {
                            this.redTeams.set(msg.payload.redTeams);
                            this.blueTeams.set(msg.payload.blueTeams);
                            this.redScore.set(msg.payload.redScore?.totalScore || 0);
                            this.blueScore.set(msg.payload.blueScore?.totalScore || 0);
                        }
                        this.displayMode.set('match');
                    }
                },
                error: (err) => {
                    console.error("FieldDisplay broadcast-all command error:", err);
                }
            });
        }

        this.broadcastService.subscribeToTopic(timerTopic).subscribe({
            next: (msg) => {
                console.log("FieldDisplay received timer message:", msg);
                if (msg.payload && msg.payload.remainingSeconds !== undefined) {
                    const newTime = msg.payload.remainingSeconds;
                    this.timeLeft.set(newTime);
                }
            },
            error: (err) => {
                console.error("FieldDisplay timer message error:", err);
            }
        });

        // Subscribe to PLAY_SOUND command for synchronized sound playback
        this.broadcastService.subscribeToTopic(soundTopic).subscribe({
            next: (msg) => {
                console.log("=== FieldDisplay received SOUND command:", msg, "===");
                // Handle STOP_SOUND command
                if (msg.messageType === 'STOP_SOUND') {
                    this.stopMatchSound();
                    return;
                }
                // Auto-enable sound if not already enabled when receiving sound command
                this.enableSound().then(() => {
                    setTimeout(() => {
                        this.playMatchStartSound();
                    }, 100);
                });
            },
            error: (err) => {
                console.error("FieldDisplay sound message error:", err);
            }
        });

        this.broadcastService.subscribeToTopic(scoreTopicRed).subscribe({
            next: (msg) => {
                console.debug("FieldDisplay received score message:", msg);
                if (msg.payload) {
                    this.redScore.set(msg.payload.totalScore);
                }
            },
            error: (err) => {
                console.error("FieldDisplay score message error:", err);
            }
        });

        this.broadcastService.subscribeToTopic(scoreTopicBlue).subscribe({
            next: (msg) => {
                console.debug("FieldDisplay received score message:", msg);
                if (msg.payload) {
                    this.blueScore.set(msg.payload.totalScore);
                }
            },
            error: (err) => {
                console.error("FieldDisplay score message error:", err);
            }
        });
    }

    private unsubscribeFromFieldTopic(fieldId: number) {
        if (fieldId === null || fieldId === undefined) return;

        if (fieldId === 0) {
            this.broadcastService.unsubscribeFromTopic(`/topic/display/field/*/command`);
            this.broadcastService.unsubscribeFromTopic(`/topic/display/field/*/timer`);
            this.broadcastService.unsubscribeFromTopic(`/topic/display/field/*/sound`);
            this.broadcastService.unsubscribeFromTopic(`/topic/live/field/*/score`);
            this.broadcastService.unsubscribeFromTopic(`/topic/live/field/*/score/red`);
            this.broadcastService.unsubscribeFromTopic(`/topic/live/field/*/score/blue`);
            return;
        }

        this.broadcastService.unsubscribeFromTopic(`/topic/display/field/${fieldId}/command`);
        this.broadcastService.unsubscribeFromTopic(`/topic/display/field/${fieldId}/timer`);
        this.broadcastService.unsubscribeFromTopic(`/topic/display/field/${fieldId}/sound`);
        this.broadcastService.unsubscribeFromTopic(`/topic/live/field/${fieldId}/score/red`);
        this.broadcastService.unsubscribeFromTopic(`/topic/live/field/${fieldId}/score/blue`);
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

    // Enable sound playback (required for mobile browsers)
    enableSound(): Promise<void> {
        console.log('=== Enabling sound playback ===');
        this.soundEnabled.set(true);

        // Initialize AudioContext on user interaction
        if (!this.audioContext) {
            try {
                this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
                console.log('AudioContext created:', this.audioContext.state);

                // Set output device if not default
                const deviceId = this.outputDeviceId();
                const audioContext = this.audioContext as any;
                if (typeof audioContext.setSinkId === 'function') {
                    audioContext.setSinkId(deviceId).then(() => {
                        console.log('=== Output device set ===');
                    }).catch((err: any) => {
                        console.warn('Failed to set output device:', err);
                    });
                }
            } catch (e) {
                console.error('Failed to create AudioContext:', e);
            }
        }

        // Resume AudioContext if suspended
        if (this.audioContext && this.audioContext.state === 'suspended') {
            return this.audioContext.resume().then(() => {
                console.log('AudioContext resumed');
            }).catch(err => {
                console.error('Failed to resume AudioContext:', err);
            });
        }
        return Promise.resolve();
    }

    // Stop match sound playback
    private stopMatchSound(): void {
        console.log('=== Stopping match sound ===');
        // Stop HTML5 Audio
        if (this.html5Audio) {
            this.html5Audio.pause();
            this.html5Audio.currentTime = 0;
        }
        // Stop current AudioContext source
        if (this.currentAudioSource) {
            try {
                this.currentAudioSource.stop();
            } catch (e) {
                // Ignore errors if already stopped
            }
            this.currentAudioSource = null;
        }
        this.isPlayingStartSound = false;
    }

    // Handle user response to sound permission popup
    // Handle output device selection change
    onOutputDeviceChange(event: Event): void {
        const target = event.target as HTMLSelectElement;
        const deviceId = target.value;
        console.log('=== Output device changed to:', deviceId, '===');
        this.outputDeviceId.set(deviceId);

        // Apply the device change to AudioContext if supported
        if (this.audioContext) {
            const audioContext = this.audioContext as any;
            if (typeof audioContext.setSinkId === 'function') {
                audioContext.setSinkId(deviceId).then(() => {
                    console.log('=== Audio output device changed successfully ===');
                }).catch((err: any) => {
                    console.error('Failed to change audio output device:', err);
                });
            } else {
                console.warn('AudioContext.setSinkId() is not supported in this browser');
            }
        }

        // Recreate HTML5 Audio element with new device (if not default)
        if (deviceId !== 'default') {
            console.log('Note: HTML5 Audio does not support device selection. Use AudioContext for device switching.');
        }

        // Preload sound again with new device setting
        this.preloadSound();
    }

    // Refresh available audio devices
    refreshAudioDevices(): void {
        console.log('=== Refreshing audio devices ===');
        if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) {
            console.warn('enumerateDevices() is not supported in this browser');
            return;
        }

        // Request permission first (required for device labels)
        navigator.mediaDevices.getUserMedia({ audio: true })
            .then(() => {
                return navigator.mediaDevices.enumerateDevices();
            })
            .then(devices => {
                const audioOutputDevices = devices.filter(device => device.kind === 'audiooutput');
                console.log('=== Available audio output devices ===');

                // Build device list with labels
                const deviceList = audioOutputDevices.map(device => ({
                    deviceId: device.deviceId,
                    label: device.label || `Device ${device.deviceId.substring(0, 8)}`
                }));

                // Add default option
                deviceList.unshift({ deviceId: 'default', label: 'System Default' });

                this.availableAudioDevices.set(deviceList);

                audioOutputDevices.forEach(device => {
                    console.log(`  - ${device.label || 'Unknown Device'} (ID: ${device.deviceId})`);
                });
            })
            .catch(err => {
                console.error('Error enumerating devices:', err);
                // Still set default option
                this.availableAudioDevices.set([{ deviceId: 'default', label: 'System Default' }]);
            });
    }


    onSoundPermissionResponse(enable: boolean): void {
        console.log('=== User sound permission response:', enable, '===');
        if (enable) {
            this.enableSound();
        } else {
            this.soundEnabled.set(false);
        }
        this.showSoundPermissionPopup.set(false);

        // Hide the modal using vanilla JS
        const modalElement = document.getElementById('soundPermissionModal');
        if (modalElement) {
            modalElement.classList.remove('show', 'd-block');
            modalElement.style.display = 'none';
            modalElement.removeAttribute('aria-modal');
            modalElement.removeAttribute('role');
        }

        // Remove backdrop
        const backdrop = document.getElementById('soundPermissionBackdrop');
        if (backdrop) {
            backdrop.remove();
        }
        document.body.classList.remove('modal-open');
        document.body.style.overflow = '';
    }

    // Play match sound (public method for testing)
    playMatchSound(): void {
        console.log('=== User triggered match sound playback ===');
        this.enableSound().then(() => {
            this.playMatchStartSound();
        });
    }


    // Preload sound file for better mobile compatibility
    private preloadSound(): void {
        console.log('=== Preloading sound file ===');
        // Try HTML5 Audio first (better for mobile)
        this.html5Audio = new Audio('assets/MatchSoundEffect.m4a');
        this.html5Audio.preload = 'auto';
        this.html5Audio.load();
        console.log('Sound preloaded using HTML5 Audio');

        // Also try to preload with AudioContext for desktop
        try {
            this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
            fetch('assets/MatchSoundEffect.m4a')
                .then(response => response.arrayBuffer())
                .then(arrayBuffer => {
                    this.audioContext?.decodeAudioData(arrayBuffer, (audioBuffer) => {
                        this.preloadedAudioBuffer = audioBuffer;
                        console.log('Sound preloaded using AudioContext');
                    });
                })
                .catch(err => console.warn('Failed to preload sound with AudioContext:', err));
        } catch (e) {
            console.warn('AudioContext not available:', e);
        }
    }

    // Audio playback for match start
    private playMatchStartSound(): void {
        console.log('=== Attempting to play match start sound ===');
        console.log('Sound enabled:', this.soundEnabled());
        console.log('HTML5 Audio available:', !!this.html5Audio);
        console.log('AudioContext available:', !!this.audioContext);
        console.log('Preloaded buffer available:', !!this.preloadedAudioBuffer);
        console.log('Selected output device:', this.outputDeviceId());

        // Prevent playing if already playing
        if (this.isPlayingStartSound) {
            console.log('=== Start sound already playing, skipping ===');
            return;
        }

        // Only play sound if enabled
        if (!this.soundEnabled()) {
            console.log('=== Sound playback disabled, skipping ===');
            return;
        }

        this.isPlayingStartSound = true;

        // If a specific device is selected, use AudioContext (HTML5 Audio doesn't support device selection)
        if (this.outputDeviceId() !== 'default') {
            console.log('=== Using AudioContext for device-specific playback ===');
            this.playWithAudioContext();
            return;
        }

        // Try HTML5 Audio first (better for mobile browsers, but uses system default)
        if (this.html5Audio) {
            try {
                this.html5Audio.currentTime = 0;
                const playPromise = this.html5Audio.play();

                if (playPromise !== undefined) {
                    playPromise
                        .then(() => {
                            console.log('=== Sound played successfully using HTML5 Audio ===');
                        })
                        .catch(err => {
                            console.warn('HTML5 Audio play failed, trying AudioContext:', err);
                            // Try to enable sound and retry with AudioContext
                            this.enableSound();
                            this.playWithAudioContext();
                        });
                } else {
                    console.log('=== Sound played using HTML5 Audio (no promise) ===');
                }
                return;
            } catch (e) {
                console.warn('HTML5 Audio error:', e);
            }
        }

        // Fallback to AudioContext
        this.playWithAudioContext();
    }

    private playWithAudioContext(): void {
        console.log('=== Attempting to play with AudioContext ===');
        if (!this.audioContext) {
            try {
                this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
            } catch (e) {
                console.error('AudioContext not supported:', e);
                return;
            }
        }

        // Resume AudioContext if suspended (required for mobile)
        if (this.audioContext.state === 'suspended') {
            console.log('Resuming suspended AudioContext...');
            this.audioContext.resume().then(() => {
                console.log('AudioContext resumed');
                this.playBuffer();
            }).catch(err => {
                console.error('Failed to resume AudioContext:', err);
            });
        } else {
            this.playBuffer();
        }
    }

    private playBuffer(): void {
        console.log('=== Attempting to play buffer ===');
        if (this.preloadedAudioBuffer && this.audioContext) {
            try {
                const source = this.audioContext.createBufferSource();
                source.buffer = this.preloadedAudioBuffer;
                source.connect(this.audioContext.destination);
                this.currentAudioSource = source;
                source.onended = () => {
                    this.isPlayingStartSound = false;
                    this.currentAudioSource = null;
                };
                source.start(0);
                console.log('=== Sound played using preloaded AudioContext buffer ===');
            } catch (e) {
                console.error('Failed to play with AudioContext:', e);
                this.isPlayingStartSound = false;
            }
        } else {
            // Fallback: fetch and play on demand
            console.log('=== Fetching and playing sound on demand ===');
            fetch('assets/MatchSoundEffect.m4a')
                .then(response => response.arrayBuffer())
                .then(arrayBuffer => {
                    if (this.audioContext) {
                        this.audioContext.decodeAudioData(arrayBuffer, (audioBuffer) => {
                            const source = this.audioContext!.createBufferSource();
                            source.buffer = audioBuffer;
                            source.connect(this.audioContext!.destination);
                            this.currentAudioSource = source;
                            source.onended = () => {
                                this.isPlayingStartSound = false;
                                this.currentAudioSource = null;
                            };
                            source.start(0);
                            console.log('=== Sound played using fetched AudioContext buffer ===');
                        });
                    }
                })
                .catch(err => {
                    console.error('Failed to load and play sound:', err);
                    this.isPlayingStartSound = false;
                });
        }
    }
}
