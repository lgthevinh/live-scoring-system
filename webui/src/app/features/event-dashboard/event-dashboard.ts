import {Component, OnInit, signal, WritableSignal} from '@angular/core';
import { CommonModule } from '@angular/common';
import { EventService } from '../../core/services/event.service';
import { AuthService } from '../../core/services/auth.service';
import { Event as GameEvent } from '../../core/models/event.model';
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
  tabs: string[] = ['All Events', 'Event Tools', 'Account Management'];
  activeTab: string = 'All Events';

events: WritableSignal<GameEvent[]> = signal([]);
  isEditing: WritableSignal<boolean> = signal(false);
  currentEditEvent: WritableSignal<GameEvent | null> = signal(null);
  showForm: WritableSignal<boolean> = signal(false);
  users: WritableSignal<UserAccount[]> = signal([]);

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

constructor(
    private eventService: EventService,
    private authService: AuthService
  ) { }

  ngOnInit() {
    this.loadEvents();
  }

  loadUsers() {
    this.authService.getAllUsers().subscribe({
      next: (users) => this.users.set(users),
      error: (err) => {
        console.error('Failed to load users', err);
        alert('Failed to load users: ' + err.message);
      }
    });
  }

  getRoleName(role: number): string {
    switch (role) {
      case 1: return 'Event Admin';
      case 10: return 'Scorekeeper';
      case 20: return 'Head Referee';
      case 21: return 'Referee';
      case 30: return 'GA/EMCEE';
      default: return 'Unknown';
    }
  }

  loadEvents() {
    this.eventService.listEvents().subscribe(events => {
      this.events.set(events);
    });
  }

setActiveTab(tab: string) {
    this.activeTab = tab;
    if (tab === 'Account Management') {
      this.loadUsers();
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
        alert('Event updated successfully');
      }, err => alert('Failed to update event: ' + err.message));
    } else {
      console.log('Creating event:', this.formEvent);
      this.formEvent.uuid = this.formEvent.eventCode;

      this.eventService.createEvent(this.formEvent).subscribe(() => {
        this.loadEvents();
        this.showForm.set(false)
        alert('Event created successfully');
      }, err => alert('Failed to create event: ' + err.message));
    }
  }

  deleteEvent(event: GameEvent) {
    if (confirm(`Are you sure you want to delete event "${event.name}"?`)) {
      this.eventService.deleteEvent(event.eventCode).subscribe({
        next: () => {
          this.loadEvents();
        },
        error: (err) => {
          // Check if it's the "active event" error - show helpful message
          const errorMsg = err.error?.error || err.message || '';
          if (errorMsg.includes('current active event')) {
            alert('Cannot delete the current active event. Please click "Clear Active Event" first, then try deleting again.');
          } else {
            alert('Failed to delete event: ' + errorMsg);
          }
        }
      });
    }
  }

  setSystemEvent(event: GameEvent) {
    if (confirm(`Set "${event.name}" as the current system event?`)) {
      this.eventService.setSystemEvent(event.eventCode).subscribe(() => {
        alert(`Current event set to ${event.name}`);
      }, err => alert('Failed to set system event: ' + err.message));
    }
  }

  clearCurrentEvent() {
    this.eventService.clearCurrentEvent().subscribe({
      next: (response) => {
        alert(response.message || 'Current event cleared. You can now delete the event.');
        this.loadEvents();
      },
      error: (err) => {
        // Check if it's just "no current event" - show as info, not error
        if (err.error?.message?.includes('No current event')) {
          alert('No active event to clear.');
        } else {
          alert('Failed to clear current event: ' + (err.error?.error || err.message));
        }
      }
    });
  }
}
