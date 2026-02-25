import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Score } from '../../../../../core/models/score.model';
import { Team } from '../../../../../core/models/team.model';
import { ScoresheetConfig, FieldConfig } from '../scoresheet.config';
import { RefereeService } from '../../../../../core/services/referee.service';

@Component({
  selector: 'app-alliance-scoresheet',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="alliance-container st-container my-3">
      <!-- Submit message -->
      <div *ngIf="submitMessage" class="alert alert-info text-center py-2">
        {{ submitMessage }}
      </div>

      <!-- Title Header -->
      <div [class.bg-danger]="alliance === 'red'" [class.bg-primary]="alliance === 'blue'"
        class="d-flex justify-content-center align-items-center my-3 p-3 rounded-3">
        <span class="text-center fw-semibold fs-3 text-white">{{ alliance === 'red' ? 'Red Alliance' : 'Blue Alliance' }}</span>
      </div>

      <div class="match-info text-center mb-3 fw-bold" *ngIf="matchInfo">
        Match: <span class="text-decoration-underline">{{ matchInfo.matchCode }}</span>
        Field: <span class="text-decoration-underline">{{ matchInfo.fieldNumber }}</span>
      </div>

      <div class="text-center mb-3">
        <div class="d-flex justify-content-center gap-3 flex-wrap">
          <span *ngFor="let team of teams" class="badge bg-light text-dark fw-bold fs-5 px-3 py-2">
            {{ team.teamId }}
          </span>
        </div>
      </div>

      <div class="row g-4" *ngFor="let period of config?.periods">
        <div class="col-12" *ngFor="let section of period.sections">

          <!-- Fields Section -->
          <ng-container *ngIf="section.type === 'fields'">
            <!-- Ball Scoring Section -->
            <div class="section-card" *ngIf="section.title">
              <div class="section-title">
                <i class="bi bi-circle-half me-2"></i>{{ section.title }}
              </div>

              <div class="interactive-counter" *ngFor="let field of section.fields">
                <div class="counter-header">
                  <span class="counter-label">
                    <div class="counter-icon">
                      <ng-container [ngSwitch]="field.key">
                        <ng-container *ngSwitchCase="'whiteBallsScored'">⚪</ng-container>
                        <ng-container *ngSwitchCase="'goldenBallsScored'">🟡</ng-container>
                        <ng-container *ngSwitchCase="'barriersPushed'">🚧</ng-container>
                        <ng-container *ngSwitchCase="'partialParking'">📍</ng-container>
                        <ng-container *ngSwitchCase="'fullParking'">🏁</ng-container>
                        <ng-container *ngSwitchCase="'imbalanceCategory'">⚖️</ng-container>
                        <ng-container *ngSwitchDefault>🎯</ng-container>
                      </ng-container>
                    </div>
                    {{ field.label }}
                  </span>
                </div>
                <!-- Dropdown for enum fields (imbalanceCategory) -->
                <div class="enum-controls" *ngIf="editable && field.type === 'enum'">
                  <select class="form-select enum-dropdown"
                          [ngModel]="getValue(scoreData, field.key)"
                          (ngModelChange)="setValue(field.key, $event)">
                    <option *ngFor="let opt of field.options" [value]="opt.value">
                      {{ opt.label }} - {{ opt.description }}
                    </option>
                  </select>
                </div>
                <!-- Counter controls for number fields -->
                <div class="counter-controls" *ngIf="editable && field.type === 'number'">
                  <button class="btn-counter btn-counter-minus"
                          (click)="decrementValue(field.key)"
                          type="button">
                    <i class="bi bi-dash-lg"></i>
                  </button>
                  <div class="counter-display">
                    <span class="counter-value">{{ getValue(scoreData, field.key) || 0 }}</span>
                    <span class="counter-total" *ngIf="field.key.includes('Ball') && (getValue(scoreData, field.key) || 0) > 0">
                      {{ field.key === 'goldenBallsScored' ? (getValue(scoreData, field.key) || 0) * 3 : getValue(scoreData, field.key) || 0 }}
                    </span>
                    <span class="counter-total" *ngIf="field.key.includes('Parking') && (getValue(scoreData, field.key) || 0) > 0">
                      {{ (getValue(scoreData, field.key) || 0) * 5 }}
                    </span>
                    <span class="counter-total" *ngIf="field.key === 'barriersPushed' && (getValue(scoreData, field.key) || 0) > 0">
                      {{ (getValue(scoreData, field.key) || 0) * 10 }}
                    </span>
                  </div>
                  <button class="btn-counter btn-counter-plus"
                          (click)="incrementValue(field.key)"
                          type="button">
                    <i class="bi bi-plus-lg"></i>
                  </button>
                </div>
                <!-- Toggle button for boolean fields (barriersPushed, redCard) -->
                <div class="toggle-controls" *ngIf="editable && field.type === 'boolean'">
                  <button (click)="toggleBooleanValue(field.key)"
                          [class.active]="getValue(scoreData, field.key)"
                          class="btn btn-lg boolean-toggle-button">
                    <div class="boolean-toggle-content">
                      <span class="boolean-toggle-icon">{{ getValue(scoreData, field.key) ? '✓' : '○' }}</span>
                      <span class="boolean-toggle-label">{{ getValue(scoreData, field.key) ? field.label + ' Active' : 'Issue ' + field.label }}</span>
                    </div>
                  </button>
                </div>
                <!-- Readonly display for enum fields -->
                <div class="text-center" *ngIf="!editable && field.type === 'enum'">
                  <div class="counter-display">
                    <span class="counter-value">{{ getEnumLabel(field, getValue(scoreData, field.key)) }}</span>
                  </div>
                </div>
                <!-- Readonly display for number fields -->
                <div class="text-center" *ngIf="!editable && field.type === 'number'">
                  <div class="counter-display">
                    <span class="counter-value">{{ getValue(scoreData, field.key) || 0 }}</span>
                    <span class="counter-total" *ngIf="field.key.includes('Ball') && (getValue(scoreData, field.key) || 0) > 0">
                      {{ field.key === 'goldenBallsScored' ? (getValue(scoreData, field.key) || 0) * 3 : getValue(scoreData, field.key) || 0 }}
                    </span>
                    <span class="counter-total" *ngIf="field.key.includes('Parking') && (getValue(scoreData, field.key) || 0) > 0">
                      {{ (getValue(scoreData, field.key) || 0) * 5 }}
                    </span>
                    <span class="counter-total" *ngIf="field.key === 'barriersPushed' && (getValue(scoreData, field.key) || 0) > 0">
                      {{ (getValue(scoreData, field.key) || 0) * 10 }}
                    </span>
                  </div>
                </div>
                <!-- Readonly display for boolean fields -->
                <div class="text-center" *ngIf="!editable && field.type === 'boolean'">
                  <div class="counter-display">
                    <span class="counter-value">
                      <i class="bi" [class]="getValue(scoreData, field.key) ? 'bi-check-circle-fill text-success' : 'bi-x-circle-fill text-danger'"></i>
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </ng-container>

          <!-- Team Table Section -->
          <ng-container *ngIf="section.type === 'team-table'">
            <div class="section-card">
              <div class="section-title">
                <i class="bi bi-people me-2"></i>{{ section.title }}
              </div>

              <div class="table-responsive">
                <table class="table table-sm table-hover align-middle mb-0">
                  <thead class="table-light">
                    <tr>
                      <th class="fw-bold">Team</th>
                      <th *ngFor="let col of section.columns" class="fw-bold text-center">{{ col.label }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let team of teams" class="align-middle">
                      <td class="fw-bold fs-5">{{ team.teamId }}</td>
                      <td *ngFor="let col of section.columns" class="text-center">
                        <ng-container [ngSwitch]="col.type">
                          <div *ngSwitchCase="'checkbox'" class="form-check form-check-inline">
                            <input class="form-check-input" type="checkbox"
                                   [checked]="getTeamValue(scoreData, team.teamId, col.key)"
                                   [disabled]="!editable"
                                   (change)="setTeamValue(team.teamId, col.key, $any($event.target).checked)">
                          </div>
                          <ng-container *ngSwitchCase="'text'">
                            <span *ngIf="!editable" class="fw-semibold">{{ getTeamValue(scoreData, team.teamId, col.key) || 'None' }}</span>
                            <input *ngIf="editable" type="text" class="form-control form-control-sm text-center"
                                   style="width: 80px; margin: 0 auto;"
                                   [ngModel]="getTeamValue(scoreData, team.teamId, col.key)"
                                   (ngModelChange)="setTeamValue(team.teamId, col.key, $event)">
                          </ng-container>
                          <ng-container *ngSwitchDefault>
                            <div *ngIf="!editable" class="fw-bold">{{ getTeamValue(scoreData, team.teamId, col.key) || 0 }}</div>
                            <div *ngIf="editable" class="d-flex align-items-center justify-content-center">
                              <button class="btn btn-sm btn-outline-secondary me-1"
                                      (click)="decrementTeamValue(team.teamId, col.key)"
                                      type="button">-</button>
                              <span class="fw-bold mx-2" style="min-width: 30px;">{{ getTeamValue(scoreData, team.teamId, col.key) || 0 }}</span>
                              <button class="btn btn-sm btn-outline-secondary ms-1"
                                      (click)="incrementTeamValue(team.teamId, col.key)"
                                      type="button">+</button>
                            </div>
                          </ng-container>
                        </ng-container>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </ng-container>

        </div>
      </div>

      <!-- Action bar -->
      <div class="action-bar d-flex flex-wrap gap-3 justify-content-center mt-4" *ngIf="editable">
        <button (click)="submitScore()" [class.btn-danger]="alliance === 'red'" [class.btn-primary]="alliance === 'blue'"
          [disabled]="submitting" class="btn">
          <span *ngIf="!submitting">Submit Score</span>
          <span *ngIf="submitting">Submitting...</span>
        </button>
      </div>
    </div>
  `,
  styles: [`
    /* Shared styling for scoring components */
    .st-container{max-width:1200px;margin:0 auto}
    .section-card{background:#fff;border:2px solid #e6e9ef;border-radius:1rem;padding:1.5rem}
    .section-title{font-weight:700;color:#1e293b;margin-bottom:1.5rem;font-size:1.25rem;display:flex;align-items:center}
    .interactive-counter{background:#f8fafc;border:2px solid #e2e8f0;border-radius:.75rem;padding:1.25rem;margin-bottom:1rem}
    .counter-header{display:flex;align-items:center;margin-bottom:1rem}
    .counter-label{display:flex;align-items:center;font-weight:600;color:#374151;font-size:1rem}
    .counter-icon{width:32px;height:32px;display:flex;align-items:center;justify-content:center;font-size:1.2rem;margin-right:.75rem;background:#f1f5f9;border-radius:8px}
    .counter-controls{display:flex;align-items:center;gap:1rem}
    .btn-counter{width:48px;height:48px;border-radius:50%;border:2px solid;font-size:1.25rem;font-weight:700;cursor:pointer;display:flex;align-items:center;justify-content:center}
    .btn-counter:disabled{opacity:.5;cursor:not-allowed}
    .btn-counter-minus{background:#fee2e2;border-color:#fca5a5;color:#dc2626}
    .btn-counter-plus{background:#dbeafe;border-color:#93c5fd;color:#2563eb}
    .counter-display{display:flex;flex-direction:column;align-items:center;justify-content:center;background:#1e293b;color:#fff;border-radius:12px;padding:.75rem 2rem;min-width:80px}
    .counter-value{font-size:2rem;font-weight:800;line-height:1}
    .counter-total{font-size:.875rem;color:#cbd5e1;margin-top:.125rem}
    .fleet-bonus{background:#fef3c7;border:2px solid #f59e0b;border-radius:8px;padding:.75rem 1rem;margin-top:.75rem;display:flex;align-items:center;justify-content:center;font-size:.9rem}
    .red-card-section{margin-top:1.5rem}
    .red-card-button{width:100%;height:80px;border-radius:1rem;border:3px solid #dc3545;background:#dc2626;color:#fff;font-weight:700;font-size:1.1rem;display:flex;align-items:center;justify-content:center;padding:0}
    .red-card-content{display:flex;align-items:center;gap:1rem}
    .red-card-icon-large{font-size:2rem;display:flex;align-items:center;justify-content:center}
    .red-card-text{text-align:left}
    .red-card-title{font-size:1.1rem;font-weight:700;margin-bottom:.125rem}
    .red-card-subtitle{font-size:.85rem;opacity:.9}
    .penalties-section{background:#fef2f2;border-color:#fca5a5}

    /* Boolean Toggle Button Styles */
    .toggle-controls{display:flex;justify-content:center;width:100%}
    .boolean-toggle-button{width:100%;max-width:320px;height:60px;border-radius:.75rem;border:2px solid #f59e0b;background:#fbbf24;color:#fff;font-weight:600;font-size:1rem;display:flex;align-items:center;justify-content:center;padding:0;transition:all .2s ease}
    .boolean-toggle-button:hover{background:#f59e0b;border-color:#d97706}
    .boolean-toggle-button.active{background:#22c55e;border-color:#16a34a}
    .boolean-toggle-button.active:hover{background:#16a34a;border-color:#15803d}
    .boolean-toggle-content{display:flex;align-items:center;gap:.5rem}
    .boolean-toggle-icon{font-size:1.25rem;font-weight:700}
    .boolean-toggle-label{text-align:left}

    /* Enum Dropdown Styles */
    .enum-controls{display:flex;justify-content:center;width:100%}
    .enum-dropdown{width:100%;max-width:400px;padding:.75rem 1rem;border-radius:8px;border:2px solid #cbd5e1;background:#fff;font-weight:500;color:#374151;font-size:.95rem}

    /* Alliance-specific styles */
    .alliance-header h3 { font-weight: bold; }
    .match-info { font-size: 1.1rem; }
  `]
})
export class AllianceScoresheetComponent implements OnChanges {
  @Input() score: Score | undefined;
  @Input() teams: Team[] = [];
  @Input() config: ScoresheetConfig | undefined;
  @Input() alliance: 'red' | 'blue' = 'red';
  @Input() matchInfo: { matchCode: string, fieldNumber: number } | null = null;
  @Input() editable: boolean = false;
  @Output() scoreChange = new EventEmitter<any>();

  scoreData: any = {};
  submitting: boolean = false;
  submitMessage: string = '';

  private refereeService = inject(RefereeService);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['score'] && this.score) {
      this.scoreData = this.parseScore(this.score);
    }
  }

  private parseScore(score: Score | undefined): any {
    if (!score || !score.rawScoreData) return {};
    try {
      return JSON.parse(score.rawScoreData);
    } catch (e) {
      console.error('Failed to parse score data', e);
      return {};
    }
  }

  getValue(data: any, key: string): any {
    if (!data) return null;
    return key.split('.').reduce((acc, part) => acc && acc[part], data);
  }

  getTeamValue(data: any, teamId: string, key: string): any {
    if (data?.teams && data.teams[teamId]) {
      return data.teams[teamId][key];
    }
    return null;
  }

  setValue(key: string, value: any) {
    if (!this.scoreData) this.scoreData = {};

    // Handle nested keys if necessary, though simple assignment works for flat structure
    // For this implementation, we assume flat keys or simple object structure
    // If keys are like 'auto.parked', we need to split
    const parts = key.split('.');
    let current = this.scoreData;
    for (let i = 0; i < parts.length - 1; i++) {
      if (!current[parts[i]]) current[parts[i]] = {};
      current = current[parts[i]];
    }
    current[parts[parts.length - 1]] = value;

    console.log('AllianceScoresheet: Score changed', this.scoreData);
    this.scoreChange.emit(this.scoreData);
  }

  setTeamValue(teamId: string, key: string, value: any) {
    if (!this.scoreData) this.scoreData = {};
    if (!this.scoreData.teams) this.scoreData.teams = {};
    if (!this.scoreData.teams[teamId]) this.scoreData.teams[teamId] = {};

    this.scoreData.teams[teamId][key] = value;
    console.log('AllianceScoresheet: Team score changed', this.scoreData);
    this.scoreChange.emit(this.scoreData);
  }

  decrementValue(key: string) {
    const current = this.getValue(this.scoreData, key) || 0;
    this.setValue(key, Math.max(0, current - 1));
  }

  incrementValue(key: string) {
    const current = this.getValue(this.scoreData, key) || 0;
    const max = this.getMax(key);
    if (max !== undefined) {
      this.setValue(key, Math.min(max, current + 1));
    } else {
      this.setValue(key, current + 1);
    }
  }

  toggleBooleanValue(key: string) {
    const current = this.getValue(this.scoreData, key) || false;
    this.setValue(key, !current);
  }

  private getMax(key: string): number | undefined {
    const section = this.config?.periods[0]?.sections.find(s => s.type === 'fields');
    return section?.fields?.find(f => f.key === key)?.max;
  }

  getEnumLabel(field: FieldConfig, value: number): string {
    const option = field.options?.find(opt => opt.value === value);
    return option ? option.label : String(value);
  }

  decrementTeamValue(teamId: string, key: string) {
    const current = this.getTeamValue(this.scoreData, teamId, key) || 0;
    this.setTeamValue(teamId, key, Math.max(0, current - 1));
  }

  incrementTeamValue(teamId: string, key: string) {
    const current = this.getTeamValue(this.scoreData, teamId, key) || 0;
    this.setTeamValue(teamId, key, current + 1);
  }

  submitScore() {
    if (!this.matchInfo) {
      this.submitMessage = 'No match information available';
      setTimeout(() => this.submitMessage = '', 3000);
      return;
    }

    this.submitting = true;
    this.submitMessage = '';

    const allianceId = this.matchInfo.matchCode + (this.alliance === 'red' ? '_R' : '_B');

    this.refereeService.submitFinalScore(this.alliance, allianceId, JSON.stringify(this.scoreData)).subscribe({
      next: (res) => {
        this.submitting = false;
        this.submitMessage = 'Score submitted successfully';
        setTimeout(() => this.submitMessage = '', 4000);
      },
      error: (err) => {
        this.submitting = false;
        this.submitMessage = 'Failed to submit score: ' + (err?.error?.message || 'Unknown error');
        setTimeout(() => this.submitMessage = '', 6000);
      }
    });
  }
}
