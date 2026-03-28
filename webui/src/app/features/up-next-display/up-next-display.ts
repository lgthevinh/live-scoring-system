import { Component, OnDestroy, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BroadcastService } from '../../core/services/broadcast.service';
import { FieldDisplayCommand } from '../../core/define/FieldDisplayCommand';
import { SyncService } from '../../core/services/sync.service';
import { Team } from '../../core/models/team.model';
import { MatchDetailDto } from '../../core/models/match.model';

@Component({
    selector: 'app-up-next-display',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './up-next-display.html',
    styleUrl: './up-next-display.css'
})
export class UpNextDisplay implements OnInit, OnDestroy {
    // Match data
    matchCode: WritableSignal<string> = signal('');
    matchNumber: WritableSignal<number> = signal(0);
    fieldNumber: WritableSignal<number> = signal(0);
    scheduledTime: WritableSignal<string> = signal('');
    redTeams: WritableSignal<Team[]> = signal([]);
    blueTeams: WritableSignal<Team[]> = signal([]);
    hasUpNext: WritableSignal<boolean> = signal(false);

    // Fullscreen state
    isFullscreen: WritableSignal<boolean> = signal(false);

    // Gear control fade
    controlsVisible: WritableSignal<boolean> = signal(true);
    private hideTimer: any = null;

    constructor(
        private syncService: SyncService,
        private broadcastService: BroadcastService
    ) {}

    private onFullscreenChange = () => {
        const isFs =
            !!document.fullscreenElement ||
            !!(document as any).webkitFullscreenElement ||
            !!(document as any).mozFullScreenElement ||
            !!(document as any).msFullscreenElement;
        this.isFullscreen.set(isFs);
    };

    ngOnInit(): void {
        this.fetchUpNextMatch();
        this.subscribeToUpdates();

        this.resetHideTimer();
        document.addEventListener('fullscreenchange', this.onFullscreenChange);
        document.addEventListener('webkitfullscreenchange' as any, this.onFullscreenChange as any);
        document.addEventListener('mozfullscreenchange' as any, this.onFullscreenChange as any);
        document.addEventListener('MSFullscreenChange' as any, this.onFullscreenChange as any);
    }

    ngOnDestroy(): void {
        this.clearHideTimer();
        this.broadcastService.unsubscribeAll();
        document.removeEventListener('fullscreenchange', this.onFullscreenChange);
        document.removeEventListener('webkitfullscreenchange' as any, this.onFullscreenChange as any);
        document.removeEventListener('mozfullscreenchange' as any, this.onFullscreenChange as any);
        document.removeEventListener('MSFullscreenChange' as any, this.onFullscreenChange as any);
    }

    private fetchUpNextMatch(): void {
        this.syncService.getUpNextMatch().subscribe({
            next: (match: MatchDetailDto | null) => {
                if (match) {
                    this.applyMatchData(match);
                } else {
                    this.hasUpNext.set(false);
                }
            },
            error: (err) => {
                console.error('Error fetching up next match:', err.message);
                this.hasUpNext.set(false);
            }
        });
    }

    private applyMatchData(match: MatchDetailDto): void {
        this.matchCode.set(match.match.matchCode || '');
        this.matchNumber.set(match.match.matchNumber || 0);
        this.fieldNumber.set(match.match.fieldNumber || 0);
        this.scheduledTime.set(match.match.matchStartTime || '');
        this.redTeams.set(match.redTeams || []);
        this.blueTeams.set(match.blueTeams || []);
        this.hasUpNext.set(true);
    }

    private subscribeToUpdates(): void {
        // Subscribe to match available topic for real-time updates when next match is set
        this.broadcastService.subscribeToTopic('/topic/match/available').subscribe({
            next: (msg) => {
                console.log('UpNextDisplay received match available:', msg);
                if (msg.payload) {
                    this.applyMatchData(msg.payload as MatchDetailDto);
                }
            },
            error: (err) => {
                console.error('UpNextDisplay match available error:', err);
            }
        });

        // Subscribe to field display command for SHOW_UPNEXT commands
        this.broadcastService.subscribeToTopic('/topic/display/field/*/command').subscribe({
            next: (msg) => {
                if (msg.type === FieldDisplayCommand.SHOW_UPNEXT) {
                    console.log('UpNextDisplay SHOW_UPNEXT command received:', msg);
                    if (msg.payload) {
                        this.applyMatchData(msg.payload as MatchDetailDto);
                    } else {
                        this.fetchUpNextMatch();
                    }
                }
            },
            error: (err) => {
                console.error('UpNextDisplay command error:', err);
            }
        });
    }

    // ========== Fullscreen ==========
    toggleFullscreen(): void {
        if (!this.isFullscreen()) {
            this.enterFullscreen();
        } else {
            this.exitFullscreen();
        }
    }

    private enterFullscreen(): void {
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

    private exitFullscreen(): void {
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

    // ========== Controls fade/show ==========
    revealControls(): void {
        this.controlsVisible.set(true);
        this.resetHideTimer();
    }

    onAnyInteract(): void {
        this.revealControls();
    }

    private resetHideTimer(): void {
        this.clearHideTimer();
        this.hideTimer = setTimeout(() => this.controlsVisible.set(false), 5000);
    }

    private clearHideTimer(): void {
        if (this.hideTimer) {
            clearTimeout(this.hideTimer);
            this.hideTimer = null;
        }
    }

    formatScheduledTime(): string {
        if (!this.scheduledTime()) return '';
        try {
            const date = new Date(this.scheduledTime());
            return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        } catch {
            return this.scheduledTime();
        }
    }
}
