import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EventService } from '../../core/services/event.service';
import { Event } from '../../core/models/event.model';
import { Observable } from 'rxjs';
import { RouterModule } from '@angular/router';
import { RequireRoleDirective } from '../../core/directives/require-role.directive';
import { AccountRoleType } from '../../core/define/AccounRoleType';

@Component({
    selector: 'app-home.component',
    standalone: true,
    imports: [CommonModule, RouterModule, RequireRoleDirective],
    templateUrl: './home.html',
    styleUrl: './home.css'
})
export class Home implements OnInit {
    currentEvent$: Observable<Event | null>;
    // Expose role constants to the template so *appRequireRole bindings
    // stay symbolic (e.g. "roles.EVENT_ADMIN") instead of magic numbers.
    readonly roles = AccountRoleType;

    constructor(private eventService: EventService) {
        this.currentEvent$ = this.eventService.currentEvent$;
    }

    ngOnInit() {
        this.eventService.getCurrentEvent().subscribe();
    }
}
