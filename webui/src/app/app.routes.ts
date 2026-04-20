import { Routes } from '@angular/router';
import { Home } from './features/home/home';
import { Auth } from './features/auth/auth';
import { Schedule } from './features/schedule/schedule';
import { MatchControl } from './features/match-control/match-control';
import { EventDashboard } from './features/event-dashboard/event-dashboard';
import { CreateAccount } from './features/event-dashboard/create-account/create-account';
import { ManageTeam } from './features/event-dashboard/manage-team/manage-team';
import { GenerateSchedule } from './features/event-dashboard/generate-schedule/generate-schedule';
import { ScoringDisplay } from './features/scoring-display/scoring-display';
import { BlueAlliance } from './features/referee/match-selection/blue-alliance/blue-alliance';
import { RedAlliance } from './features/referee/match-selection/red-alliance/red-alliance';
import { ScoreTracking } from './features/referee/score-tracking/score-tracking';
import { MatchResults } from './features/match-results/match-results';
import { Rankings } from './features/rankings/rankings';
import { authGuard, roleGuard } from './core/guards/auth.guard';
import { AccountRoleType } from './core/define/AccounRoleType';

export const routes: Routes = [
  // Public routes: login screen, plus read-only pages for spectators,
  // field displays, and unattended ranking boards. These must NOT require
  // a token; the corresponding backend endpoints are whitelisted in
  // AuthFilter's PUBLIC_READ_PREFIXES.
  { path: 'auth', component: Auth },
  { path: 'schedule', component: Schedule, data: { title: 'Qualification Schedule', matchType: 1 } },
  { path: 'rankings', component: Rankings },
  { path: 'playoffs', component: Schedule, data: { title: 'Playoff Schedule', matchType: 2 } },
  { path: 'results', component: MatchResults },
  { path: 'display', component: ScoringDisplay },

  // Any logged-in user can see the home page.
  { path: '', component: Home, canActivate: [authGuard] },

  // Referee score tracking. Scoring referees + head referees + scorekeepers
  // + admins all pass the SCORING_REFEREE threshold.
  { path: 'ref/blue', component: BlueAlliance, canActivate: [roleGuard(AccountRoleType.SCORING_REFEREE)] },
  { path: 'ref/red', component: RedAlliance, canActivate: [roleGuard(AccountRoleType.SCORING_REFEREE)] },
  { path: 'ref/:color/:matchId', component: ScoreTracking, canActivate: [roleGuard(AccountRoleType.SCORING_REFEREE)] },

  // Match control — scorekeepers and up.
  { path: 'match-control', component: MatchControl, canActivate: [roleGuard(AccountRoleType.SCOREKEEPER)] },

  // Event administration — event admins only.
  { path: 'event-dashboard', component: EventDashboard, canActivate: [roleGuard(AccountRoleType.EVENT_ADMIN)] },
  { path: 'event-dashboard/create-account', component: CreateAccount, canActivate: [roleGuard(AccountRoleType.EVENT_ADMIN)] },
  { path: 'event-dashboard/manage-team', component: ManageTeam, canActivate: [roleGuard(AccountRoleType.EVENT_ADMIN)] },
  { path: 'event-dashboard/generate-schedule', component: GenerateSchedule, canActivate: [roleGuard(AccountRoleType.EVENT_ADMIN)] },
];
