import {Component, OnInit, WritableSignal, signal} from '@angular/core';
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
}
