import { Injectable, signal, WritableSignal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, finalize, map, Observable, of } from 'rxjs';
import { ScorekeeperService } from './scorekeeper.service';
import { TempScore } from '../models/score.model';

export interface TempScoreData {
  whiteBallsScored: number;
  goldenBallsScored: number;
  allianceBarrierPushed: boolean;
  opponentBarrierPushed: boolean;
  partialParking: number;
  fullParking: number;
  imbalanceCategory: number;
  penaltyCount: number;
  yellowCardCount: number;
  redCard: boolean;
}

export interface BufferedScoreSubmission {
  id: string;
  matchId: string;
  allianceId: string;
  color: 'red' | 'blue';
  matchCode: string;
  teamIds: string[];
  payload: TempScoreData;
  tempScoreData?: TempScoreData; // From backend temp score
  tempScoreId?: string; // Backend temp score ID
  calculatedScore: number;
  timestamp: number;
  status: 'pending' | 'submitted' | 'error';
  errorMessage?: string;
}

@Injectable({ providedIn: 'root' })
export class ScoreSubmitBufferService {
  private readonly STORAGE_KEY = 'referee_score_submit_buffer';
  private http = inject(HttpClient);
  private scorekeeperService = inject(ScorekeeperService);

  // Signal for reactive updates
  buffer: WritableSignal<BufferedScoreSubmission[]> = signal([]);

  // Track cleanup timeouts to prevent memory leaks
  private cleanupTimeouts: Map<string, number> = new Map();

  // Track submission attempts to backend
  private backendAttempts: Map<string, boolean> = new Map();

  // Computed signals for real-time reactive updates
  pendingCount = computed(() => this.buffer().filter(s => s.status === 'pending').length);
  pendingSubmissions = computed(() => this.buffer().filter(s => s.status === 'pending'));
  errorSubmissions = computed(() => this.buffer().filter(s => s.status === 'error'));

  constructor() {
    this.loadFromStorage();
    this.setupStorageListener();
  }

  /**
   * Listen for localStorage changes from other tabs/windows
   */
  private setupStorageListener(): void {
    if (typeof window !== 'undefined') {
      window.addEventListener('storage', (event) => {
        if (event.key === this.STORAGE_KEY) {
          // Reload from storage when another tab modifies the buffer
          this.loadFromStorage();
        }
      });
    }
  }

  /**
   * Add a new score submission to the buffer and sync to backend
   */
  addToBuffer(submission: Omit<BufferedScoreSubmission, 'id' | 'timestamp' | 'status'>, submittedBy: string = 'referee'): BufferedScoreSubmission {
    const id = this.generateId();
    const newSubmission: BufferedScoreSubmission = {
      ...submission,
      id,
      timestamp: Date.now(),
      status: 'pending'
    };

    this.buffer.update(current => [...current, newSubmission]);
    this.saveToStorage();

    // Sync to backend for scorekeeper review
    this.syncToBackend(newSubmission, submittedBy);

    return newSubmission;
  }

  /**
   * Sync a submission to the backend temp-score endpoint
   */
  private syncToBackend(submission: BufferedScoreSubmission, submittedBy: string): void {
    if (this.backendAttempts.get(submission.id)) {
      return; // Already attempting
    }

    this.backendAttempts.set(submission.id, true);

    this.scorekeeperService.saveTempScore(
      submission.allianceId,
      submission.payload,
      submittedBy
    ).pipe(
      finalize(() => this.backendAttempts.delete(submission.id))
    ).subscribe({
      next: (response) => {
        // Successfully synced to backend - store tempScoreId and keep as pending
        this.markAsSyncedToBackend(submission.id, response.tempScoreId);
      },
      error: (error) => {
        console.warn('Failed to sync score to backend, will retry later:', error);
        // Mark as error so user can retry
        this.markAsError(submission.id, 'Failed to sync to server. Will retry automatically.');
      }
    });
  }

  /**
   * Mark submission as synced to backend (but still pending scorekeeper approval)
   */
  private markAsSyncedToBackend(id: string, tempScoreId: string): boolean {
    const current = this.buffer();
    const index = current.findIndex(s => s.id === id);

    if (index === -1) return false;

    this.buffer.update(current =>
      current.map(s => s.id === id ? { ...s, status: 'pending', tempScoreId, errorMessage: undefined } : s)
    );
    this.saveToStorage();
    return true;
  }

  /**
   * Remove a submission from the buffer by ID
   * Allows removing pending or error items
   */
  removeFromBuffer(id: string): boolean {
    const current = this.buffer();
    const index = current.findIndex(s => s.id === id);

    if (index === -1) return false;

    // Allow removing pending or error items (not submitted)
    const status = current[index].status;
    if (status === 'submitted') return false;

    this.buffer.update(current => current.filter(s => s.id !== id));
    this.saveToStorage();
    return true;
  }

  /**
   * Update a submission in the buffer
   */
  updateSubmission(id: string, updates: Partial<BufferedScoreSubmission>): boolean {
    const current = this.buffer();
    const index = current.findIndex(s => s.id === id);

    if (index === -1) return false;
    if (current[index].status !== 'pending') return false;

    this.buffer.update(current =>
      current.map(s => s.id === id ? { ...s, ...updates } : s)
    );
    this.saveToStorage();
    return true;
  }

  /**
   * Check if a submission is ready to be committed (has tempScoreId and is not in error state)
   */
  isReadyToCommit(id: string): { ready: boolean; message?: string } {
    const current = this.buffer();
    const submission = current.find(s => s.id === id);

    if (!submission) {
      return { ready: false, message: 'Submission not found' };
    }

    if (submission.status === 'error') {
      return { ready: false, message: submission.errorMessage || 'Submission has errors - please retry' };
    }

    if (!submission.tempScoreId) {
      return { ready: false, message: 'Waiting for temp score sync...' };
    }

    return { ready: true };
  }

  
  /**
   * Mark a submission as submitted directly (for when score is committed outside temp score system)
   */
  markAsSubmittedDirect(id: string): boolean {
    const result = this.updateStatus(id, 'submitted');
    if (result) {
      this.clearCleanupTimeout(id);
      
      // Remove submitted items from buffer after a delay
      const timeoutId = window.setTimeout(() => {
        this.buffer.update(current => current.filter(s => s.id !== id));
        this.saveToStorage();
        this.cleanupTimeouts.delete(id);
      }, 3000);
      this.cleanupTimeouts.set(id, timeoutId);
    }
    return result;
  }

  /**
   * Mark a submission as submitted (called when scorekeeper commits the score)
   */
  markAsSubmitted(id: string, approvedBy: string = 'scorekeeper'): boolean {
    const current = this.buffer();
    const submission = current.find(s => s.id === id);

    if (!submission) return false;

    // Check if submission is ready to commit
    const readiness = this.isReadyToCommit(id);
    if (!readiness.ready) {
      console.warn('Cannot commit submission:', readiness.message);
      if (submission.status === 'error') {
        // Error message already set
        return false;
      }
      // For pending/syncing state, just return false without marking as error
      // The UI should show the syncing message
      return false;
    }

    // Call backend commit endpoint with tempScoreId
    this.scorekeeperService.commitTempScore(submission.tempScoreId!, approvedBy).subscribe({
      next: () => {
        const result = this.updateStatus(id, 'submitted');
        if (result) {
          this.clearCleanupTimeout(id);

          // Remove submitted items from buffer after a delay
          const timeoutId = window.setTimeout(() => {
            this.buffer.update(current => current.filter(s => s.id !== id));
            this.saveToStorage();
            this.cleanupTimeouts.delete(id);
          }, 3000);
          this.cleanupTimeouts.set(id, timeoutId);
        }
      },
      error: (error) => {
        console.error('Failed to commit score:', error);
        this.markAsError(id, 'Failed to finalize score commit');
      }
    });

    return true;
  }

  /**
   * Mark a submission as error
   */
  markAsError(id: string, errorMessage: string): boolean {
    const current = this.buffer();
    const index = current.findIndex(s => s.id === id);

    if (index === -1) return false;

    this.buffer.update(current =>
      current.map(s => s.id === id ? { ...s, status: 'error', errorMessage } : s)
    );
    this.saveToStorage();
    return true;
  }

  /**
   * Retry a failed submission (will re-attempt backend sync)
   */
  retrySubmission(id: string, submittedBy: string = 'referee'): boolean {
    const current = this.buffer();
    const index = current.findIndex(s => s.id === id);

    if (index === -1) return false;
    if (current[index].status !== 'error') return false;

    const submission = current[index];

    // Reset status and retry backend sync
    this.buffer.update(current =>
      current.map(s => s.id === id ? { ...s, status: 'pending', errorMessage: undefined } : s)
    );
    this.saveToStorage();

    // Re-attempt backend sync
    this.syncToBackend(submission, submittedBy);

    return true;
  }

  /**
   * Get all pending submissions (reactive computed signal)
   */
  getPendingSubmissions(): BufferedScoreSubmission[] {
    return this.pendingSubmissions();
  }

  /**
   * Get all submissions with errors (reactive computed signal)
   */
  getErrorSubmissions(): BufferedScoreSubmission[] {
    return this.errorSubmissions();
  }

  /**
   * Get count of pending submissions (reactive computed signal)
   */
  getPendingCount(): number {
    return this.pendingCount();
  }

  /**
   * Check if there's already a pending submission for a specific match and color
   */
  hasPendingForMatch(matchId: string, color: 'red' | 'blue'): boolean {
    return this.buffer().some(
      s => s.matchId === matchId && s.color === color && s.status === 'pending'
    );
  }

  /**
   * Get pending submission for a specific match and color
   */
  getPendingForMatch(matchId: string, color: 'red' | 'blue'): BufferedScoreSubmission | undefined {
    return this.buffer().find(
      s => s.matchId === matchId && s.color === color && s.status === 'pending'
    );
  }

  /**
   * Clear all submitted items from buffer
   */
  clearSubmitted(): void {
    this.buffer.update(current => current.filter(s => s.status !== 'submitted'));
    this.saveToStorage();
  }

  /**
   * Clear the entire buffer (use with caution)
   */
  clearAll(): void {
    // Clear all pending timeouts to prevent memory leaks
    this.cleanupTimeouts.forEach((timeoutId) => clearTimeout(timeoutId));
    this.cleanupTimeouts.clear();
    this.buffer.set([]);
    this.saveToStorage();
  }

  /**
   * Clear a specific cleanup timeout
   */
  private clearCleanupTimeout(id: string): void {
    const timeoutId = this.cleanupTimeouts.get(id);
    if (timeoutId) {
      clearTimeout(timeoutId);
      this.cleanupTimeouts.delete(id);
    }
  }

  private updateStatus(id: string, status: BufferedScoreSubmission['status']): boolean {
    const current = this.buffer();
    const index = current.findIndex(s => s.id === id);

    if (index === -1) return false;

    this.buffer.update(current =>
      current.map(s => s.id === id ? { ...s, status } : s)
    );
    this.saveToStorage();
    return true;
  }

  private generateId(): string {
    return `score_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private saveToStorage(): void {
    try {
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(this.buffer()));
    } catch (e) {
      console.error('Failed to save score buffer to storage:', e);
    }
  }

  private loadFromStorage(): void {
    try {
      const stored = localStorage.getItem(this.STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        // Filter out submitted items older than 1 hour on load
        const oneHourAgo = Date.now() - (60 * 60 * 1000);
        const filtered = parsed.filter((s: BufferedScoreSubmission) =>
          s.status !== 'submitted' || s.timestamp > oneHourAgo
        );
        this.buffer.set(filtered);
      }
    } catch (e) {
      console.error('Failed to load score buffer from storage:', e);
      this.buffer.set([]);
    }
  }

  /**
   * Get all temp scores from backend for an alliance
   */
  getBackendTempScores(allianceId: string): Observable<TempScore[]> {
    return this.scorekeeperService.getTempScores(allianceId);
  }

  /**
   * Get temp score details from backend (single - for backwards compatibility)
   */
  getBackendTempScore(allianceId: string): Observable<any> {
    return this.scorekeeperService.getTempScore(allianceId);
  }

  /**
   * Check if there are any pending temp scores for an alliance
   */
  hasBackendTempScores(allianceId: string): Observable<boolean> {
    return this.scorekeeperService.getTempScores(allianceId).pipe(
      map(scores => scores.length > 0)
    );
  }

  /**
   * Reject a temp score by tempScoreId (scorekeeper action)
   */
  rejectTempScore(tempScoreId: string, rejectedBy: string, reason: string): Observable<any> {
    return this.scorekeeperService.rejectTempScore(tempScoreId, rejectedBy, reason);
  }

  /**
   * Get all pending submissions for a specific alliance
   */
  getPendingForAlliance(allianceId: string): BufferedScoreSubmission[] {
    return this.buffer().filter(
      s => s.allianceId === allianceId && s.status === 'pending'
    );
  }

  /**
   * Update submission with tempScoreId
   */
  setTempScoreId(submissionId: string, tempScoreId: string): boolean {
    const current = this.buffer();
    const index = current.findIndex(s => s.id === submissionId);

    if (index === -1) return false;

    this.buffer.update(current =>
      current.map(s => s.id === submissionId ? { ...s, tempScoreId } : s)
    );
    this.saveToStorage();
    return true;
  }
}
