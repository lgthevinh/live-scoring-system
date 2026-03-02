import { Component, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatchDetailDto } from '../../core/models/match.model';
import { MatchService, MockMatchService, ProdMatchService } from '../../core/services/match.service';
import { environment } from '../../../environments/environment';
import { ScoresheetComponent } from './components/scoresheet/scoresheet.component';

@Component({
  selector: 'app-match-results',
  standalone: true,
  imports: [CommonModule, ScoresheetComponent],
  providers: [
    {
      provide: MatchService,
      useClass: environment.useFakeData ? MockMatchService : ProdMatchService
    }
  ],
  templateUrl: './match-results.html',
  styleUrl: './match-results.css'
})
export class MatchResults implements OnInit {
  loading: WritableSignal<boolean> = signal(true);
  error: WritableSignal<string | null> = signal(null);
  matches: WritableSignal<MatchDetailDto[]> = signal([]);

  // UI-only toggles (keep backend/data code untouched)
  condensed = false;
  keepBackgroundOnPrint = false;

  selectedMatch: WritableSignal<MatchDetailDto | null> = signal(null);
  selectedAlliance: WritableSignal<'red' | 'blue' | null> = signal(null);

  constructor(private matchService: MatchService) { }

  ngOnInit(): void {
    this.fetchMatchResults();
  }

  fetchMatchResults() {
    this.loading.set(true);
    this.error.set(null);

    // keep existing backend/data handling via MatchService
    this.matchService.getMatches(1, true).subscribe({
      next: (matchResults) => {
        this.matches.set(matchResults || []);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Failed to fetch match results.');
        this.loading.set(false);
      },
    });
  }

  viewScoresheet(match: MatchDetailDto, event: Event, alliance: 'red' | 'blue' | null = null) {
    event.preventDefault();
    this.loading.set(true);

    const redAllianceId = match.match.matchCode + '_R';
    const blueAllianceId = match.match.matchCode + '_B';

    // Fetch detailed scores for both alliances
    const redScore$ = this.matchService.getScore(redAllianceId);
    const blueScore$ = this.matchService.getScore(blueAllianceId);

    import('rxjs').then(({ forkJoin }) => {
      forkJoin([redScore$, blueScore$]).subscribe({
        next: ([redScore, blueScore]) => {
          // Create a copy of the match with updated scores containing raw data
          const updatedMatch = {
            ...match,
            redScore: redScore,
            blueScore: blueScore
          };
          this.selectedMatch.set(updatedMatch);
          this.selectedAlliance.set(alliance);
          this.loading.set(false);
        },
        error: (err) => {
          console.error('Failed to fetch detailed scores', err);
          this.error.set('Failed to load detailed scores.');
          this.loading.set(false);
        }
      });
    });
  }

  closeScoresheet() {
    this.selectedMatch.set(null);
    this.selectedAlliance.set(null);
  }

  formatResult(match: MatchDetailDto): string {
    const redApproved = match?.redScore?.approved;
    const blueApproved = match?.blueScore?.approved;
    const redScore = redApproved ? (match?.redScore?.totalScore ?? 0) : 0;
    const blueScore = blueApproved ? (match?.blueScore?.totalScore ?? 0) : 0;
    if (!redApproved || !blueApproved) return 'Pending';
    if (redScore > blueScore) return `${redScore} - ${blueScore} R`;
    if (blueScore > redScore) return `${redScore} - ${blueScore} B`;
    return `${redScore}-${blueScore}`;
  }

  // Unplayed = no start time (kept logic)
  isUnplayed(match: MatchDetailDto): boolean {
    return !match?.match?.matchEndTime;
  }

  winner(match: MatchDetailDto): 'red' | 'blue' | null {
    if (this.isUnplayed(match)) return null;
    const redApproved = match?.redScore?.approved;
    const blueApproved = match?.blueScore?.approved;
    if (!redApproved || !blueApproved) return null;
    const redScore = match?.redScore?.totalScore ?? 0;
    const blueScore = match?.blueScore?.totalScore ?? 0;
    if (redScore > blueScore) return 'red';
    if (blueScore > redScore) return 'blue';
    return null;
  }

  isTie(match: MatchDetailDto): boolean {
    if (this.isUnplayed(match)) return false;
    const redApproved = match?.redScore?.approved;
    const blueApproved = match?.blueScore?.approved;
    if (!redApproved || !blueApproved) return false;
    const redScore = match?.redScore?.totalScore ?? 0;
    const blueScore = match?.blueScore?.totalScore ?? 0;
    return redScore === blueScore;
  }

  // Helper to render team IDs list elsewhere if needed
  getTeamIds(teamList: any[]): string {
    return (teamList || []).map(team => team.teamId).join(', ');
  }

  // Row helpers to render multi-line team lists (FTC-like layout)
  teamRowCount(match: MatchDetailDto): number {
    const r = match?.redTeams?.length ?? 0;
    const b = match?.blueTeams?.length ?? 0;
    return Math.max(r, b, 1);
  }

  rows(n: number): number[] {
    return Array.from({ length: n }, (_, i) => i);
  }

  teamIdAt(teams: any[] | undefined, index: number): string {
    if (!teams || index >= teams.length) return '';
    return teams[index]?.teamId ?? '';
  }

  printPage() {
    window.print();
  }

  isSurrogate(match: MatchDetailDto, teamId: string | undefined): boolean {
    if (!match || !match.surrogateMap || !teamId) {
      return false;
    }
    return !!match.surrogateMap[teamId];
  }
}
