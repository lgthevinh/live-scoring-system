import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface ToastAction {
  label: string;
  onAction: () => void;
}

export interface Toast {
  id: string;
  message: string;
  type: 'info' | 'success' | 'error';
  duration: number;
  remainingTime: number;
  action?: ToastAction;
}

@Injectable({
  providedIn: 'root'
})
export class ToastService implements OnDestroy {
  private toasts: Toast[] = [];
  private toastSubject = new BehaviorSubject<Toast[]>([]);
  public toasts$: Observable<Toast[]> = this.toastSubject.asObservable();
  private timeoutIds: Map<string, number> = new Map();
  private intervalIds: Map<string, number> = new Map();

  show(
    message: string,
    type: 'info' | 'success' | 'error' = 'info',
    duration: number = 15000,
    action?: ToastAction
  ): string {
    const id = this.generateId();
    const toast: Toast = { id, message, type, duration, remainingTime: Math.ceil(duration / 1000), action };
    
    this.toasts = [...this.toasts, toast];
    this.toastSubject.next(this.toasts);
    
    // Start countdown interval for action toasts
    if (action) {
      const intervalId = window.setInterval(() => {
        const currentToast = this.toasts.find(t => t.id === id);
        if (currentToast && currentToast.remainingTime > 0) {
          currentToast.remainingTime--;
          this.toastSubject.next([...this.toasts]);
        }
      }, 1000);
      this.intervalIds.set(id, intervalId);
    }
    
    // Auto-remove after duration
    const timeoutId = window.setTimeout(() => {
      this.remove(id);
    }, duration);
    this.timeoutIds.set(id, timeoutId);
    
    return id;
  }

  remove(id: string): void {
    // Clear pending timeout if exists
    const timeoutId = this.timeoutIds.get(id);
    if (timeoutId) {
      clearTimeout(timeoutId);
      this.timeoutIds.delete(id);
    }
    
    // Clear interval if exists
    const intervalId = this.intervalIds.get(id);
    if (intervalId) {
      clearInterval(intervalId);
      this.intervalIds.delete(id);
    }
    
    this.toasts = this.toasts.filter(t => t.id !== id);
    this.toastSubject.next(this.toasts);
  }

  clearAll(): void {
    // Clear all pending timeouts
    this.timeoutIds.forEach(id => clearTimeout(id));
    this.timeoutIds.clear();
    
    // Clear all intervals
    this.intervalIds.forEach(id => clearInterval(id));
    this.intervalIds.clear();
    
    this.toasts = [];
    this.toastSubject.next([]);
  }

  ngOnDestroy(): void {
    this.clearAll();
  }

  private generateId(): string {
    return 'toast-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
  }
}
