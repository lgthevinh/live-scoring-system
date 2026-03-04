import {Component, OnInit, WritableSignal, signal, computed} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RankService } from '../../core/services/rank.service';
import { RankingEntry } from '../../core/models/rank.model';

@Component({
    selector: 'app-rankings',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './rankings.html',
    styleUrls: ['./rankings.css']
})
export class Rankings implements OnInit {
    rankings: WritableSignal<RankingEntry[]> = signal([]);
    isLoading: WritableSignal<boolean> = signal(false);
    error: WritableSignal<string | null> = signal(null);

    sortColumn: WritableSignal<'rank' | 'teamId' | 'totalScore' | 'averageScore' | 'highestScore' | 'matchesPlayed'> = signal('rank');
    sortDirection: WritableSignal<'asc' | 'desc'> = signal('asc');

    sortedRankings = computed(() => {
        const rankings = [...this.rankings()];
        const column = this.sortColumn();
        const direction = this.sortDirection();

        // First, calculate actual competition rank based on average score (desc), total score (desc), highest score (desc)
        const rankingsWithRank = rankings.map(r => ({ ...r })); // Clone to avoid mutating original
        rankingsWithRank.sort((a, b) => {
            // Primary: average score (descending)
            if (b.averageScore !== a.averageScore) {
                return b.averageScore - a.averageScore;
            }
            // Secondary: total score (descending)
            if (b.totalScore !== a.totalScore) {
                return b.totalScore - a.totalScore;
            }
            // Tertiary: highest score (descending)
            return b.highestScore - a.highestScore;
        });

        // Assign rank based on position in the sorted array
        rankingsWithRank.forEach((rank, index) => {
            rank.rank = index + 1;
        });

        // Now apply user's sort preference for display (but keep the calculated rank)
        rankingsWithRank.sort((a, b) => {
            let comparison = 0;
            switch (column) {
                case 'rank':
                    comparison = (a.rank || 0) - (b.rank || 0);
                    break;
                case 'teamId':
                    comparison = a.teamId.localeCompare(b.teamId);
                    break;
                case 'totalScore':
                    comparison = a.totalScore - b.totalScore;
                    break;
                case 'averageScore':
                    comparison = a.averageScore - b.averageScore;
                    break;
                case 'highestScore':
                    comparison = a.highestScore - b.highestScore;
                    break;
                case 'matchesPlayed':
                    comparison = a.matchesPlayed - b.matchesPlayed;
                    break;
            }
            return direction === 'asc' ? comparison : -comparison;
        });

        return rankingsWithRank;
    });

    constructor(private rankService: RankService) { }

    ngOnInit(): void {
        this.loadRankings();
    }

    loadRankings(): void {
        this.error.set(null);
        this.isLoading.set(true)
        this.rankService.getRankStatus().subscribe({
            next: (data) => {
                this.rankings.set(data);
                this.isLoading.set(false);
            },
            error: (err) => {
                console.error('Failed to load rankings', err);
                this.error.set('Failed to load rankings. Please try again.');
                this.isLoading.set(false);
            }
        });
    }

    recalculate(): void {
        this.isLoading.set(true)
        this.rankService.recalculateRankings().subscribe({
            next: () => {
                this.loadRankings();
            },
            error: (err) => {
                console.error('Failed to recalculate rankings', err);
                this.error.set('Failed to recalculate rankings. Please try again.');
                this.isLoading.set(false);
            }
        });
    }

    sortBy(column: 'rank' | 'teamId' | 'totalScore' | 'averageScore' | 'highestScore' | 'matchesPlayed'): void {
        if (this.sortColumn() === column) {
            this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
        } else {
            this.sortColumn.set(column);
            this.sortDirection.set(column === 'teamId' || column === 'rank' ? 'asc' : 'desc');
        }
    }
}
