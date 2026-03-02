import {Component, OnInit, signal, WritableSignal, inject} from '@angular/core';
import { CommonModule } from '@angular/common';
import { EventService } from '../../core/services/event.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { Event as GameEvent } from '../../core/models/event.model';
import { AccountRoleType } from '../../core/define/AccounRoleType';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

interface UserAccount {
  username: string;
  role: number;
}

@Component({
  selector: 'app-event-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './event-dashboard.html',
  styleUrl: './event-dashboard.css'
})
export class EventDashboard implements OnInit {
  private eventService = inject(EventService);
  private authService = inject(AuthService);
  private toastService = inject(ToastService);

  tabs: string[] = ['All Events', 'Event Manager', 'Account Management'];
  activeTab: string = 'All Events';

  events: WritableSignal<GameEvent[]> = signal([]);
  isEditing: WritableSignal<boolean> = signal(false);
  currentEditEvent: WritableSignal<GameEvent | null> = signal(null);
  showForm: WritableSignal<boolean> = signal(false);
  users: WritableSignal<UserAccount[]> = signal([]);
  currentEventCode: WritableSignal<string | null> = signal(null);

  // Form model
  formEvent: GameEvent = {
    uuid: '',
    name: '',
    eventCode: '',
    fieldCount: 3,
    date: '',
    location: '',
    description: '',
    website: '',
    organizer: ''
  };

  // Account Management
  AccountRoleType = AccountRoleType;
  accounts: UserAccount[] = [];
  isLoadingAccounts: boolean = false;
  showAccountModal: boolean = false;
  passwordVisible: boolean = false;

  // Account Form
  accountForm = {
    username: '',
    password: '',
    reEnterPassword: '',
    role: 0
  };

  ngOnInit() {
    this.loadEvents();
    this.loadCurrentEvent();
    this.loadAccounts(); // Preload accounts
  }

  loadUsers() {
    this.authService.getAllUsers().subscribe({
      next: (users) => this.users.set(users),
      error: (err) => {
        console.error('Failed to load users', err);
        this.toastService.show('Failed to load users: ' + err.message, 'error');
      }
    });
  }

  getRoleName(role: number): string {
    switch (role) {
      case AccountRoleType.EVENT_ADMIN: return 'Event Admin';
      case AccountRoleType.SCOREKEEPER: return 'Scorekeeper';
      case AccountRoleType.HEAD_REFEREE: return 'Head Referee';
      case AccountRoleType.SCORING_REFEREE: return 'Scoring Referee';
      case AccountRoleType.EMCEE: return 'Emcee';
      default: return 'Unknown';
    }
  }

  loadEvents() {
    this.eventService.listEvents().subscribe(events => {
      this.events.set(events);
    });
  }

  loadCurrentEvent() {
    this.eventService.getCurrentEvent().subscribe({
      next: (event) => {
        if (event) {
          this.currentEventCode.set(event.eventCode);
        }
      },
      error: (err) => {
        console.log('No current event set');
      }
    });
  }

  setActiveTab(tab: string) {
    this.activeTab = tab;
    if (tab === 'Account Management') {
      this.loadAccounts();
    }
  }

  openCreateForm() {
    this.isEditing.set(false);
    this.currentEditEvent.set(null);
    this.resetForm();
    this.showForm.set(true)
  }

  openEditForm(event: GameEvent) {
    this.isEditing.set(true)
    this.currentEditEvent.set(event);
    this.formEvent = { ...event };
    this.showForm.set(true)
  }

  cancelForm() {
    this.showForm.set(false);
    this.resetForm();
  }

  resetForm() {
    this.formEvent = {
      uuid: '',
      name: '',
      eventCode: '',
      fieldCount: 3,
      date: '',
      location: '',
      description: '',
      website: '',
      organizer: ''
    };
  }

  saveEvent() {
    if (this.isEditing()) {
      console.log('Updating event:', this.formEvent);
      this.eventService.updateEvent(this.formEvent).subscribe(() => {
        this.loadEvents();
        this.showForm.set(false)
        this.toastService.show('Event updated successfully', 'success');
      }, err => this.toastService.show('Failed to update event: ' + err.message, 'error'));
    } else {
      console.log('Creating event:', this.formEvent);
      this.formEvent.uuid = this.formEvent.eventCode;

      this.eventService.createEvent(this.formEvent).subscribe(() => {
        this.loadEvents();
        this.showForm.set(false)
        this.toastService.show('Event created successfully', 'success');
      }, err => this.toastService.show('Failed to create event: ' + err.message, 'error'));
    }
  }

  deleteEvent(event: GameEvent) {
    // Store event for potential undo
    const deletedEvent = { ...event };

    this.eventService.deleteEvent(event.eventCode).subscribe({
      next: () => {
        this.loadEvents();
        this.toastService.show(`Event "${deletedEvent.name}" deleted`, 'success', 15000, {
          label: 'Undo',
          onAction: () => {
            this.eventService.createEvent(deletedEvent).subscribe(() => {
              this.loadEvents();
              this.toastService.show('Event restored', 'success');
            });
          }
        });
      },
      error: (err) => {
        // Check if it's the "active event" error - show helpful message
        const errorMsg = err.error?.error || err.message || '';
        if (errorMsg.includes('current active event')) {
          this.toastService.show('Cannot delete the current active event. Please click "Clear Active Event" first, then try deleting again.', 'error', 6000);
        } else {
          this.toastService.show('Failed to delete event: ' + errorMsg, 'error');
        }
      }
    });
  }

  setSystemEvent(event: GameEvent) {
    this.eventService.setSystemEvent(event.eventCode).subscribe(() => {
      this.currentEventCode.set(event.eventCode);
      this.toastService.show(`Current event set to ${event.name}`, 'success');
    }, err => this.toastService.show('Failed to set system event: ' + err.message, 'error'));
  }

  clearCurrentEvent() {
    this.eventService.clearCurrentEvent().subscribe({
      next: (response) => {
        this.currentEventCode.set(null);
        this.toastService.show(response.message || 'Current event cleared. You can now delete the event.', 'success');
        this.loadEvents();
      },
      error: (err) => {
        // Check if it's just "no current event" - show as info, not error
        if (err.error?.message?.includes('No current event')) {
          this.toastService.show('No active event to clear.', 'info');
        } else {
          this.toastService.show('Failed to clear current event: ' + (err.error?.error || err.message), 'error');
        }
      }
    });
  }

  // ===== Account Management Methods =====

  loadAccounts() {
    this.authService.getAllAccounts().subscribe({
      next: (response: any) => {
        this.accounts = [...(response.accounts || [])];
      },
      error: (err) => {
        console.error('Error loading accounts:', err);
        this.toastService.show('Error loading accounts', 'error');
      }
    });
  }

  openAccountModal() {
    this.resetAccountForm();
    this.showAccountModal = true;
  }

  closeAccountModal() {
    this.showAccountModal = false;
    this.resetAccountForm();
  }

  resetAccountForm() {
    this.accountForm = {
      username: '',
      password: '',
      reEnterPassword: '',
      role: 0
    };
  }

  togglePasswordVisibility() {
    this.passwordVisible = !this.passwordVisible;
  }

  validatePasswords(password: string, confirmPassword: string): boolean {
    return password === confirmPassword;
  }

  onAccountSubmit() {
    this.handleAccountCreate();
  }

  handleAccountCreate() {
    if (this.validatePasswords(this.accountForm.password, this.accountForm.reEnterPassword)) {
      const credentials = {
        username: this.accountForm.username,
        password: this.accountForm.password,
        role: this.accountForm.role
      };
      this.authService.createAccount(credentials).subscribe({
        next: () => {
          this.toastService.show('Account created successfully', 'success');
          this.closeAccountModal();
          this.loadAccounts();
        },
        error: (err) => {
          this.toastService.show('Error creating account: ' + (err.error?.message || 'Unknown error'), 'error');
          console.error('Error creating account:', err);
        }
      });
    } else {
      this.toastService.show('Passwords do not match', 'error');
    }
  }

  deleteAccount(username: string) {
    this.authService.deleteAccount(username).subscribe({
      next: () => {
        this.toastService.show(`Account "${username}" deleted`, 'success');
        this.loadAccounts();
      },
      error: (err) => {
        this.toastService.show('Error deleting account: ' + (err.error?.message || 'Unknown error'), 'error');
        console.error('Error deleting account:', err);
      }
    });
  }
}
