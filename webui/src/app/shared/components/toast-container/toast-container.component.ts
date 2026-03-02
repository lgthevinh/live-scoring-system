import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService, Toast } from '../../../core/services/toast.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toast-container">
      @for (toast of toasts$ | async; track toast.id) {
        <div class="toast" [class.success]="toast.type === 'success'" [class.error]="toast.type === 'error'" [class.info]="toast.type === 'info'">
          <div class="toast-content">
            <span class="toast-icon">
              @if (toast.type === 'success') { ✓ }
              @if (toast.type === 'error') { ✗ }
              @if (toast.type === 'info') { ℹ }
            </span>
            <span class="toast-text">{{ toast.message }}</span>
          </div>
          <button class="toast-close" (click)="removeToast(toast.id)">×</button>
        </div>
      }
    </div>
  `,
  styles: [`
    .toast-container {
      position: fixed;
      top: 20px;
      right: 20px;
      z-index: 9999;
      display: flex;
      flex-direction: column;
      gap: 10px;
      max-width: 450px;
    }
    
    .toast {
      background: #3b82f6;
      color: white;
      padding: 16px 20px;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      min-width: 300px;
      animation: slideIn 0.3s ease;
      transition: all 0.3s ease;
    }
    
    .toast.success {
      background: #10b981;
    }
    
    .toast.error {
      background: #ef4444;
    }
    
    .toast.info {
      background: #3b82f6;
    }
    
    .toast-content {
      display: flex;
      align-items: center;
      gap: 12px;
      flex: 1;
    }
    
    .toast-icon {
      font-size: 20px;
      font-weight: bold;
    }
    
    .toast-text {
      font-size: 14px;
      font-weight: 500;
    }
    
    .toast-close {
      background: rgba(255,255,255,0.2);
      border: none;
      color: white;
      width: 24px;
      height: 24px;
      border-radius: 50%;
      cursor: pointer;
      font-size: 18px;
      line-height: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.2s;
    }
    
    .toast-close:hover {
      background: rgba(255,255,255,0.3);
    }
    
    @keyframes slideIn {
      from {
        transform: translateX(100%);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }
    
    @keyframes slideOut {
      from {
        transform: translateX(0);
        opacity: 1;
      }
      to {
        transform: translateX(100%);
        opacity: 0;
      }
    }
    
    .toast.removing {
      animation: slideOut 0.3s ease forwards;
    }
  `]
})
export class ToastContainerComponent {
  private toastService = inject(ToastService);
  toasts$: Observable<Toast[]> = this.toastService.toasts$;
  
  removeToast(id: string): void {
    this.toastService.remove(id);
  }
}
