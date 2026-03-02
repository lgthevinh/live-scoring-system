import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatchDetailDto } from '../../../../core/models/match.model';
import { Team } from '../../../../core/models/team.model';
import { CUSTOM_SEASON_CONFIG, ScoresheetConfig } from './scoresheet.config';
import { AllianceScoresheetComponent } from './alliance-scoresheet/alliance-scoresheet.component';

@Component({
    selector: 'app-scoresheet',
    standalone: true,
    imports: [CommonModule, AllianceScoresheetComponent],
    templateUrl: './scoresheet.component.html',
    styleUrls: ['./scoresheet.component.css']
})
export class ScoresheetComponent {
    @Input() match: MatchDetailDto | null = null;
    @Input() alliance: 'red' | 'blue' | null = null;
    @Input() editable: boolean = false;
    @Input() redScoreData: any = null;
    @Input() blueScoreData: any = null;

    @Output() redScoreChange = new EventEmitter<any>();
    @Output() blueScoreChange = new EventEmitter<any>();

    config: ScoresheetConfig = CUSTOM_SEASON_CONFIG;

    // Helper to get team IDs for the table
    getTeams(alliance: 'red' | 'blue'): Team[] {
        if (!this.match) return [];
        return alliance === 'red' ? this.match.redTeams : this.match.blueTeams;
    }

    getMatchInfo() {
        if (!this.match) return null;
        return {
            matchCode: this.match.match.matchCode,
            fieldNumber: this.match.match.fieldNumber
        };
    }
}
