import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface Toast {
  id: string;
  message: string;
  type: 'info' | 'success' | 'error';
  duration: number;
}

@Injectable({
  providedIn: 'root'
})
export class ToastService implements OnDestroy {
  private toasts: Toast[] = [];
  private toastSubject = new BehaviorSubject<Toast[]>([]);
  public toasts$: Observable<Toast[]> = this.toastSubject.asObservable();
  private timeoutIds: Map<string, number> = new Map();

  show(message: string, type: 'info' | 'success' | 'error' = 'info', duration: number = 3000): string {
    const id = this.generateId();
    const toast: Toast = { id, message, type, duration };
    
    this.toasts = [...this.toasts, toast];
    this.toastSubject.next(this.toasts);
    
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
    
    this.toasts = this.toasts.filter(t => t.id !== id);
    this.toastSubject.next(this.toasts);
  }

  clearAll(): void {
    // Clear all pending timeouts
    this.timeoutIds.forEach(id => clearTimeout(id));
    this.timeoutIds.clear();
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
