import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Score } from '../../../../../core/models/score.model';
import { Team } from '../../../../../core/models/team.model';
import { ScoresheetConfig, FieldConfig } from '../scoresheet.config';
import { RefereeService } from '../../../../../core/services/referee.service';
import { ScorekeeperService } from '../../../../../core/services/scorekeeper.service';
import { ToastService } from '../../../../../core/services/toast.service';

@Component({
  selector: 'app-alliance-scoresheet',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="alliance-container st-container my-3">
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

      <!-- Calculated Score Preview -->
      <div class="score-preview-card" *ngIf="editable">
        <div class="d-flex justify-content-between align-items-center">
          <div class="calculated-score">
            <span class="score-label">Calculated Score:</span>
            <span class="score-value" [class.zero-score]="calculatedScore() === 0" [class.positive]="calculatedScore() > 0">
              {{ calculatedScore() }}
            </span>
          </div>
          <button class="btn btn-outline-secondary btn-sm" (click)="resetScores()" title="Reset all scores to 0">
            <i class="bi bi-arrow-counterclockwise me-1"></i>Reset
          </button>
        </div>
        <div class="score-breakdown text-muted small mt-2" *ngIf="!redCardValue()">
          <span *ngIf="biologicalPoints() > 0">Biological: {{ biologicalPoints() }} pts</span>
          <span *ngIf="barrierPoints() > 0">• Barriers: {{ barrierPoints() }} pts</span>
          <span *ngIf="endGamePoints() > 0">• End Game: {{ endGamePoints() }} pts</span>
          <span *ngIf="fleetBonus() > 0" class="fleet-bonus-text">• Fleet Bonus: +{{ fleetBonus() }} pts</span>
          <span *ngIf="penaltyPoints() > 0" class="penalty-text">• Penalties: -{{ penaltyPoints() }} pts</span>
          <span *ngIf="coefficient() !== 1">• Coefficient: {{ coefficient() }}x</span>
        </div>
        <div class="red-card-warning" *ngIf="redCardValue()">
          <i class="bi bi-exclamation-triangle-fill text-danger me-2"></i>
          <span class="text-danger fw-bold">Red Card Active - Score will be zero!</span>
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
                        <ng-container *ngSwitchCase="'allianceBarrierPushed'">🚧</ng-container>
                        <ng-container *ngSwitchCase="'opponentBarrierPushed'">🎯</ng-container>
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
                <!-- Counter controls for number fields with direct input -->
                <div class="counter-controls enhanced" *ngIf="editable && field.type === 'number'">
                  <button class="btn-counter btn-counter-minus"
                          (click)="decrementValue(field.key, field.min)"
                          [disabled]="isAtMin(field.key, field.min)"
                          type="button">
                    <i class="bi bi-dash-lg"></i>
                  </button>
                  <div class="counter-display-with-input">
                    <input type="number"
                           class="counter-input"
                           [value]="getValue(scoreData, field.key) || 0"
                           (change)="setNumberValue(field.key, $event, field.min, field.max)"
                           [min]="field.min || 0"
                           [max]="field.max"
                           step="1">
                    <span class="counter-points" *ngIf="getPointsForField(field.key) > 0">
                      = {{ getPointsForField(field.key) }} pts
                    </span>
                  </div>
                  <button class="btn-counter btn-counter-plus"
                          (click)="incrementValue(field.key, field.max)"
                          [disabled]="isAtMax(field.key, field.max)"
                          type="button">
                    <i class="bi bi-plus-lg"></i>
                  </button>
                </div>
                <!-- Limit indicator for number fields -->
                <div class="limit-indicator" *ngIf="editable && field.type === 'number' && (field.max !== undefined || field.min !== undefined)">
                  <small class="text-muted">
                    <span *ngIf="field.min !== undefined">Min: {{ field.min }}</span>
                    <span *ngIf="field.min !== undefined && field.max !== undefined"> • </span>
                    <span *ngIf="field.max !== undefined">Max: {{ field.max }}</span>
                    <span *ngIf="field.key === 'fullParking' && getValue(scoreData, field.key) >= 2" class="fleet-bonus-badge">
                      <i class="bi bi-trophy-fill text-warning me-1"></i>Fleet Bonus Unlocked!
                    </span>
                  </small>
                </div>
                <!-- Toggle button for boolean fields (allianceBarrierPushed, opponentBarrierPushed, redCard) -->
                <div [ngClass]="{'bg-danger': (field.key === 'allianceBarrierPushed' && alliance === 'red') || (field.key === 'opponentBarrierPushed' && alliance === 'blue'), 'bg-primary': (field.key === 'allianceBarrierPushed' && alliance === 'blue') || (field.key === 'opponentBarrierPushed' && alliance === 'red')}" class="toggle-controls rounded-3 p-3" *ngIf="editable && field.type === 'boolean'">
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
                    <span class="counter-total" *ngIf="getPointsForField(field.key) > 0">
                      {{ getPointsForField(field.key) }} pts
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
                              <input type="number" class="form-control form-control-sm text-center mx-1"
                                     style="width: 60px;"
                                     [value]="getTeamValue(scoreData, team.teamId, col.key) || 0"
                                     (change)="setTeamNumberValue(team.teamId, col.key, $event)">
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
          class="btn btn-submit">
          <span class="btn-text">Submit Score</span>
          <span class="btn-icon">→</span>
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
    
    /* Enhanced counter controls with input */
    .counter-controls{display:flex;align-items:center;gap:1rem}
    .counter-controls.enhanced{gap:0.5rem}
    .btn-counter{width:48px;height:48px;border-radius:50%;border:2px solid;font-size:1.25rem;font-weight:700;cursor:pointer;display:flex;align-items:center;justify-content:center;transition:all 0.2s}
    .btn-counter:disabled{opacity:.3;cursor:not-allowed}
    .btn-counter-minus{background:#fee2e2;border-color:#fca5a5;color:#dc2626}
    .btn-counter-minus:hover:not(:disabled){background:#fecaca;border-color:#f87171}
    .btn-counter-plus{background:#dbeafe;border-color:#93c5fd;color:#2563eb}
    .btn-counter-plus:hover:not(:disabled){background:#bfdbfe;border-color:#60a5fa}
    
    /* Counter display with input */
    .counter-display-with-input{display:flex;flex-direction:column;align-items:center;background:#1e293b;color:#fff;border-radius:12px;padding:.5rem 1rem;min-width:100px}
    .counter-input{width:80px;text-align:center;background:transparent;border:none;color:#fff;font-size:1.75rem;font-weight:800;line-height:1;outline:none;-moz-appearance:textfield}
    .counter-input::-webkit-outer-spin-button,.counter-input::-webkit-inner-spin-button{-webkit-appearance:none;margin:0}
    .counter-input:focus{background:rgba(255,255,255,0.1);border-radius:6px}
    .counter-points{font-size:.8rem;color:#86efac;margin-top:.25rem}
    
    /* Legacy display styles */
    .counter-display{display:flex;flex-direction:column;align-items:center;justify-content:center;background:#1e293b;color:#fff;border-radius:12px;padding:.75rem 2rem;min-width:80px}
    .counter-value{font-size:2rem;font-weight:800;line-height:1}
    .counter-total{font-size:.875rem;color:#cbd5e1;margin-top:.125rem}
    
    /* Limit indicator */
    .limit-indicator{margin-top:0.5rem;text-align:center}
    .fleet-bonus-badge{color:#f59e0b;font-weight:600}
    
    /* Score preview card */
    .score-preview-card{background:linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);border:2px solid #0ea5e9;border-radius:1rem;padding:1.25rem;margin-bottom:1.5rem}
    .calculated-score{display:flex;align-items:center;gap:1rem}
    .score-label{font-weight:600;color:#0369a1;font-size:1.1rem}
    .score-value{font-size:2.5rem;font-weight:800;color:#0284c7;line-height:1}
    .score-value.zero-score{color:#94a3b8}
    .score-value.positive{color:#16a34a}
    .score-breakdown{display:flex;flex-wrap:wrap;gap:0.5rem;justify-content:center}
    .fleet-bonus-text{color:#f59e0b;font-weight:600}
    .penalty-text{color:#dc2626}
    .red-card-warning{margin-top:0.5rem;padding:0.75rem;background:#fef2f2;border-radius:8px;text-align:center}
    
    /* Fleet bonus */
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

    /* Button Animation Styles */
    .btn-submit {
      position: relative;
      overflow: hidden;
      transition: all 0.2s ease;
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 24px;
      font-weight: 600;
      border: none;
      cursor: pointer;
    }
    .btn-submit:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0,0,0,0.2);
    }
    .btn-submit.clicked {
      transform: scale(0.95);
    }
    .btn-submit:active {
      transform: scale(0.92);
    }
    .btn-icon {
      transition: transform 0.2s ease;
    }
    .btn-submit:hover .btn-icon {
      transform: translateX(4px);
    }
    .btn-submit::after {
      content: '';
      position: absolute;
      top: 50%;
      left: 50%;
      width: 0;
      height: 0;
      background: rgba(255,255,255,0.3);
      border-radius: 50%;
      transform: translate(-50%, -50%);
      transition: width 0.3s ease, height 0.3s ease;
    }
    .btn-submit:active::after {
      width: 200px;
      height: 200px;
    }
  `]
})
export class AllianceScoresheetComponent implements OnChanges {
  @Input() score: Score | undefined;
  @Input() initialScoreData: any = null;
  @Input() teams: Team[] = [];
  @Input() config: ScoresheetConfig | undefined;
  @Input() alliance: 'red' | 'blue' = 'red';
  @Input() matchInfo: { matchCode: string, fieldNumber: number } | null = null;
  @Input() matchId: string | null = null;
  @Input() editable: boolean = false;
  @Output() scoreChange = new EventEmitter<any>();

  scoreData: any = {};
  submitMessage: string = '';

  private refereeService = inject(RefereeService);
  private scorekeeperService = inject(ScorekeeperService);
  private toastService = inject(ToastService);

  // Computed signals for calculated score
  whiteBalls = signal(0);
  goldenBalls = signal(0);
  allianceBarrier = signal(false);
  opponentBarrier = signal(false);
  partialPark = signal(0);
  fullPark = signal(0);
  imbalanceCat = signal(2);
  penalties = signal(0);
  yellowCards = signal(0);
  redCardValue = signal(false);

  // Calculated values
  biologicalPoints = computed(() => (this.goldenBalls() * 3) + this.whiteBalls());
  barrierPoints = computed(() => (this.allianceBarrier() ? 10 : 0) + (this.opponentBarrier() ? 10 : 0));
  endGamePoints = computed(() => (this.partialPark() * 5) + (this.fullPark() * 10));
  fleetBonus = computed(() => this.fullPark() >= 2 ? 10 : 0);
  penaltyPoints = computed(() => (this.penalties() * 5) + (this.yellowCards() * 10));
  coefficient = computed(() => {
    const imbalanceMultipliers = [2.0, 1.5, 1.3];
    let coeff = imbalanceMultipliers[this.imbalanceCat()] || 1.3;
    if (!this.allianceBarrier()) {
      coeff -= 0.2;
    }
    return Math.max(0, coeff);
  });
  calculatedScore = computed(() => {
    if (this.redCardValue()) {
      return 0;
    }
    const baseScore = (this.biologicalPoints() + this.barrierPoints()) * this.coefficient();
    const totalScore = Math.round(baseScore + this.endGamePoints() + this.fleetBonus() - this.penaltyPoints());
    return Math.max(0, totalScore);
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['initialScoreData'] && this.initialScoreData && Object.keys(this.initialScoreData).length > 0) {
      this.scoreData = { ...this.initialScoreData };
      this.updateComputedValues();
    } else if (changes['score'] && this.score) {
      this.scoreData = this.parseScore(this.score);
      this.updateComputedValues();
    }
  }

  private updateComputedValues() {
    this.whiteBalls.set(this.getValue(this.scoreData, 'whiteBallsScored') || 0);
    this.goldenBalls.set(this.getValue(this.scoreData, 'goldenBallsScored') || 0);
    this.allianceBarrier.set(this.getValue(this.scoreData, 'allianceBarrierPushed') || false);
    this.opponentBarrier.set(this.getValue(this.scoreData, 'opponentBarrierPushed') || false);
    this.partialPark.set(this.getValue(this.scoreData, 'partialParking') || 0);
    this.fullPark.set(this.getValue(this.scoreData, 'fullParking') || 0);
    this.imbalanceCat.set(this.getValue(this.scoreData, 'imbalanceCategory') ?? 2);
    this.penalties.set(this.getValue(this.scoreData, 'penaltyCount') || 0);
    this.yellowCards.set(this.getValue(this.scoreData, 'yellowCardCount') || 0);
    this.redCardValue.set(this.getValue(this.scoreData, 'redCard') || false);
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

    const parts = key.split('.');
    let current = this.scoreData;
    for (let i = 0; i < parts.length - 1; i++) {
      if (!current[parts[i]]) current[parts[i]] = {};
      current = current[parts[i]];
    }
    current[parts[parts.length - 1]] = value;

    this.updateComputedValues();
    console.log('AllianceScoresheet: Score changed', this.scoreData);
    this.scoreChange.emit(this.scoreData);
  }

  setNumberValue(key: string, event: Event, min?: number, max?: number) {
    const input = event.target as HTMLInputElement;
    let value = parseInt(input.value, 10);
    
    if (isNaN(value)) value = 0;
    if (min !== undefined && value < min) value = min;
    if (max !== undefined && value > max) value = max;
    
    // Special handling for parking limits (total cannot exceed 2)
    if (key === 'partialParking' || key === 'fullParking') {
      const otherKey = key === 'partialParking' ? 'fullParking' : 'partialParking';
      const otherValue = this.getValue(this.scoreData, otherKey) || 0;
      if (value + otherValue > 2) {
        value = 2 - otherValue;
      }
    }
    
    this.setValue(key, value);
  }

  setTeamValue(teamId: string, key: string, value: any) {
    if (!this.scoreData) this.scoreData = {};
    if (!this.scoreData.teams) this.scoreData.teams = {};
    if (!this.scoreData.teams[teamId]) this.scoreData.teams[teamId] = {};

    this.scoreData.teams[teamId][key] = value;
    console.log('AllianceScoresheet: Team score changed', this.scoreData);
    this.scoreChange.emit(this.scoreData);
  }

  setTeamNumberValue(teamId: string, key: string, event: Event) {
    const input = event.target as HTMLInputElement;
    let value = parseInt(input.value, 10);
    if (isNaN(value) || value < 0) value = 0;
    this.setTeamValue(teamId, key, value);
  }

  decrementValue(key: string, min?: number) {
    const current = this.getValue(this.scoreData, key) || 0;
    const newValue = Math.max(min || 0, current - 1);
    this.setValue(key, newValue);
  }

  incrementValue(key: string, max?: number) {
    const current = this.getValue(this.scoreData, key) || 0;
    
    // Special handling for parking limits
    if (key === 'partialParking' || key === 'fullParking') {
      const otherKey = key === 'partialParking' ? 'fullParking' : 'partialParking';
      const otherValue = this.getValue(this.scoreData, otherKey) || 0;
      if (current + otherValue >= 2) {
        return; // Cannot exceed total of 2
      }
    }
    
    let newValue = current + 1;
    if (max !== undefined && newValue > max) {
      newValue = max;
    }
    this.setValue(key, newValue);
  }

  toggleBooleanValue(key: string) {
    const current = this.getValue(this.scoreData, key) || false;
    this.setValue(key, !current);
  }

  isAtMin(key: string, min?: number): boolean {
    const current = this.getValue(this.scoreData, key) || 0;
    return current <= (min || 0);
  }

  isAtMax(key: string, max?: number): boolean {
    const current = this.getValue(this.scoreData, key) || 0;
    
    // Special handling for parking limits
    if (key === 'partialParking' || key === 'fullParking') {
      const otherKey = key === 'partialParking' ? 'fullParking' : 'partialParking';
      const otherValue = this.getValue(this.scoreData, otherKey) || 0;
      if (current + otherValue >= 2) {
        return true;
      }
    }
    
    if (max !== undefined && current >= max) {
      return true;
    }
    return false;
  }

  getPointsForField(key: string): number {
    const value = this.getValue(this.scoreData, key) || 0;
    switch (key) {
      case 'whiteBallsScored': return value * 1;
      case 'goldenBallsScored': return value * 3;
      case 'partialParking': return value * 5;
      case 'fullParking': return value * 10 + (value >= 2 ? 10 : 0);
      case 'allianceBarrierPushed': return value ? 10 : 0;
      case 'opponentBarrierPushed': return value ? 10 : 0;
      default: return 0;
    }
  }

  resetScores() {
    this.scoreData = {
      whiteBallsScored: 0,
      goldenBallsScored: 0,
      allianceBarrierPushed: false,
      opponentBarrierPushed: false,
      partialParking: 0,
      fullParking: 0,
      imbalanceCategory: 2,
      penaltyCount: 0,
      yellowCardCount: 0,
      redCard: false
    };
    this.updateComputedValues();
    this.scoreChange.emit(this.scoreData);
    this.toastService.show('All scores reset to 0', 'info');
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
      this.toastService.show('No match information available', 'error', 3000);
      return;
    }

    this.submitMessage = '';

    const matchIdentifier = this.matchId || this.matchInfo.matchCode;
    const allianceId = matchIdentifier + (this.alliance === 'red' ? '_R' : '_B');

    const allianceName = this.alliance === 'red' ? 'Red' : 'Blue';
    this.toastService.show(`Submitting ${allianceName} Alliance score for ${matchIdentifier}...`, 'info', 4000);

    if (this.editable && this.matchId) {
      this.scorekeeperService.overrideScore(allianceId, JSON.stringify(this.scoreData)).subscribe({
        next: (res) => {
          this.submitMessage = 'Score submitted successfully';
          this.toastService.show(`${allianceName} Alliance score for ${matchIdentifier} saved successfully!`, 'success', 4000);
        },
        error: (err) => {
          this.submitMessage = 'Failed to submit score: ' + (err?.error?.message || 'Unknown error');
          this.toastService.show(`Failed to save ${allianceName} Alliance score: ${err?.error?.message || 'Unknown error'}`, 'error', 6000);
        }
      });
    } else {
      this.refereeService.submitFinalScore(this.alliance, allianceId, JSON.stringify(this.scoreData)).subscribe({
        next: (res) => {
          this.submitMessage = 'Score submitted successfully';
          this.toastService.show(`${allianceName} Alliance score for ${matchIdentifier} submitted successfully!`, 'success', 4000);
        },
        error: (err) => {
          this.submitMessage = 'Failed to submit score: ' + (err?.error?.message || 'Unknown error');
          this.toastService.show(`Failed to submit ${allianceName} Alliance score: ${err?.error?.message || 'Unknown error'}`, 'error', 6000);
        }
      });
    }
  }
}
