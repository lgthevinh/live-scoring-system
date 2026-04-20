import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { tap, catchError, map, switchMap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

/**
 * Auth state + role source of truth for the UI.
 *
 * Role semantics mirror the backend ({@code org.thingai.app.scoringservice
 * .define.AccountRole}): lower number = higher privilege. A user passes a
 * threshold check {@link #hasRole} when {@code currentRole <= minRole}.
 *
 * Persistence: the token lives in {@code localStorage[authToken]} and the
 * role (integer) in {@code localStorage[authRole]}. Both are cleared on
 * logout. On service construction, if a token is present, the service
 * hydrates {@code role$} from localStorage immediately (fast path) and
 * then refreshes it via GET /api/auth/me (authoritative).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiUrl = environment.apiBaseUrl + '/api/auth';
  private readonly TOKEN_KEY = 'authToken';
  private readonly ROLE_KEY = 'authRole';

  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.hasToken());
  private roleSubject = new BehaviorSubject<number | null>(this.readStoredRole());

  constructor(private http: HttpClient) {
    console.log(this.apiUrl);
    if (this.hasToken()) {
      // Re-validate role against the server on startup so a stale
      // localStorage role doesn't mislead the UI.
      this.refreshMe().subscribe({ error: () => this.logout() });
    }
  }

  login(credentials: { username: string; password: string }): Observable<any> {
    return this.http.post(`${this.apiUrl}/login`, credentials).pipe(
      tap((response: any) => {
        if (response && response.token) {
          localStorage.setItem(this.TOKEN_KEY, response.token);
          this.isAuthenticatedSubject.next(true);
        }
      }),
      // After the token is persisted, fetch role via /me so the full auth
      // state is populated before the caller navigates away from /auth.
      switchMap((response: any) => this.refreshMe().pipe(
        map(() => response),
        catchError(() => of(response)),
      )),
      catchError(error => {
        this.isAuthenticatedSubject.next(false);
        throw error;
      })
    );
  }

  logout() {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.ROLE_KEY);
    this.isAuthenticatedSubject.next(false);
    this.roleSubject.next(null);
  }

  isAuthenticated(): Observable<boolean> {
    return this.isAuthenticatedSubject.asObservable();
  }

  /** Current role as a stream (null when not authenticated or unknown). */
  role$(): Observable<number | null> {
    return this.roleSubject.asObservable();
  }

  /** Synchronous role snapshot — useful for guards. */
  currentRole(): number | null {
    return this.roleSubject.getValue();
  }

  /**
   * True when the current user satisfies the given role threshold.
   * Lower number = higher privilege; pass e.g. {@code AccountRoleType.REFEREE}
   * to allow referees and everyone more privileged than them.
   */
  hasRole(minRole: number): Observable<boolean> {
    return this.roleSubject.asObservable().pipe(
      map(r => r !== null && r <= minRole),
    );
  }

  /** Fetch /api/auth/me and update role state. */
  refreshMe(): Observable<{ username: string; role: number }> {
    return this.http.get<{ username: string; role: number }>(`${this.apiUrl}/me`).pipe(
      tap(me => {
        if (me && typeof me.role === 'number') {
          localStorage.setItem(this.ROLE_KEY, String(me.role));
          this.roleSubject.next(me.role);
        }
      }),
    );
  }

  createAccount(credentials: { username: string; password: string, role: number }): Observable<any> {
    return this.http.post(`${this.apiUrl}/create-account`, credentials);
  }

  getAllUsers(): Observable<{ username: string; role: number }[]> {
    return this.http.get<{ username: string; role: number }[]>(`${this.apiUrl}/users`);
  }

  getAllAccounts(): Observable<{ accounts: { username: string; role: number }[] }> {
    return this.http.get<{ accounts: { username: string; role: number }[] }>(`${this.apiUrl}/accounts`);
  }

  updateAccount(username: string, credentials: { password?: string, role: number }): Observable<any> {
    return this.http.put(`${this.apiUrl}/accounts/${username}`, credentials);
  }

  deleteAccount(username: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/accounts/${username}`);
  }

  getLocalIp(): Observable<string> {
    return this.http.get<string>(`${this.apiUrl}/local-ip`).pipe(map((response: any) => response.localIp));
  }

  private hasToken(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }

  private readStoredRole(): number | null {
    const raw = localStorage.getItem(this.ROLE_KEY);
    if (!raw) return null;
    const n = parseInt(raw, 10);
    return Number.isFinite(n) ? n : null;
  }
}
