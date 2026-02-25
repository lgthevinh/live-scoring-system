export interface ScoresheetConfig {
    periods: PeriodConfig[];
}

export interface PeriodConfig {
    id: string;
    title: string;
    sections: SectionConfig[];
}

export interface SectionConfig {
    id: string;
    title?: string;
    type: 'fields' | 'team-table';
    fields?: FieldConfig[];
    columns?: ColumnConfig[];
}

export interface FieldConfig {
    key: string; // Path in JSON, e.g., "auto.samples.highBasket"
    label: string;
    type: 'number' | 'boolean' | 'text' | 'enum';
    min?: number;
    max?: number;
    options?: { value: number; label: string; description: string }[];
}

export interface ColumnConfig {
    key: string; // Path relative to team entry, e.g., "robot"
    label: string;
    type: 'checkbox' | 'text' | 'enum';
    options?: string[];
}

export const CUSTOM_SEASON_CONFIG: ScoresheetConfig = {
    periods: [
        {
            id: 'main',
            title: 'Match Score',
            sections: [
                {
                    id: 'ball-scoring',
                    title: 'Ball Scoring',
                    type: 'fields',
                    fields: [
                        { key: 'whiteBallsScored', label: 'White Balls Scored by Human', type: 'number', min: 0, max: 100 },
                        { key: 'goldenBallsScored', label: 'Golden Balls Scored by Robot', type: 'number', min: 0, max: 100 }
                    ]
                },
                {
                    id: 'barriers-movement',
                    title: 'Barriers & Movement',
                    type: 'fields',
                    fields: [
                        { key: 'barriersPushed', label: 'Barrier Pushed Away', type: 'boolean' }
                    ]
                },
                {
                    id: 'end-game',
                    title: 'End Game',
                    type: 'fields',
                    fields: [
                        { key: 'partialParking', label: 'Partial Parking', type: 'number', min: 0 },
                        { key: 'fullParking', label: 'Full Parking', type: 'number', min: 0 }
                    ]
                },
                {
                    id: 'imbalance-penalties',
                    title: 'Imbalance & Penalties',
                    type: 'fields',
                    fields: [
                        {
                            key: 'imbalanceCategory',
                            label: 'Ball Imbalance Category',
                            type: 'enum',
                            options: [
                                { value: 0, label: 'Balanced', description: '2.0x bonus - 0-1 ball difference' },
                                { value: 1, label: 'Medium', description: '1.5x bonus - 2-3 balls difference' },
                                { value: 2, label: 'Large', description: '1.3x bonus - 4+ balls difference' }
                            ]
                        },
                        { key: 'penaltyCount', label: 'Minor Penalty Violations', type: 'number', min: 0 },
                        { key: 'yellowCardCount', label: 'Yellow Card Violations', type: 'number', min: 0 },
                        { key: 'redCard', label: 'Red Card', type: 'boolean' }
                    ]
                }
            ]
        }
    ]
}
