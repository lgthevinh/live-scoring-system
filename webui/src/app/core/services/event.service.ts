import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { map, tap, catchError } from 'rxjs/operators';
import { Event } from '../models/event.model';
import { environment } from '../../../environments/environment';

@Injectable({
    providedIn: 'root'
})
export class EventService {
    private apiUrl = environment.apiBaseUrl + '/api/event';
    private currentEventSubject = new BehaviorSubject<Event | null>(null);
    public currentEvent$ = this.currentEventSubject.asObservable();

    constructor(private http: HttpClient) { }

    listEvents(): Observable<Event[]> {
        return this.http.get<Event[]>(`${this.apiUrl}/`);
    }

    getEvent(eventCode: string): Observable<Event> {
        return this.http.get<Event>(`${this.apiUrl}/${eventCode}`);
    }

    getCurrentEvent(): Observable<Event | null> {
        // Reduce API calls by checking if we already have the current event
        if (this.currentEventSubject.value) {
            return of(this.currentEventSubject.value);
        }
        console.log('Fetching current event from API');
        return this.http.get<{ currentEvent: Event }>(`${this.apiUrl}/current`).pipe(
            map(response => response.currentEvent),
            tap(event => this.currentEventSubject.next(event)),
            catchError(error => {
                if (error.status === 404) {
                    this.currentEventSubject.next(null);
                    return of(null);
                }
                throw error;
            })
        );
    }

    createEvent(event: Event): Observable<Event> {
        return this.http.post<Event>(`${this.apiUrl}/create`, event);
    }

    updateEvent(event: Event): Observable<boolean> {
        return this.http.post<boolean>(`${this.apiUrl}/update`, event);
    }

    deleteEvent(eventCode: string, cleanDelete: boolean = true): Observable<any> {
        return this.http.post(`${this.apiUrl}/delete`, { eventCode, cleanDelete });
    }

    setSystemEvent(eventCode: string): Observable<Event> {
        return this.http.post<Event>(`${this.apiUrl}/set`, { eventCode }).pipe(
            tap(event => this.currentEventSubject.next(event))
        );
    }

    clearCurrentEvent(): Observable<{ message: string }> {
        return this.http.post<{ message: string }>(`${this.apiUrl}/clear-current`, {}).pipe(
            tap(() => this.currentEventSubject.next(null))
        );
    }
}
