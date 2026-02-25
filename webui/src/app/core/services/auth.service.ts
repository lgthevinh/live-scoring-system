import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {BehaviorSubject, map, Observable} from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import {environment} from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiUrl = environment.apiBaseUrl + '/api/auth';
  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.hasToken());

  constructor(private http: HttpClient) {
    console.log(this.apiUrl);
  }

  login(credentials: { username: string; password: string }): Observable<any> {
    return this.http.post(`${this.apiUrl}/login`, credentials).pipe(
      tap((response: any) => {
        if (response && response.token) {
          localStorage.setItem('authToken', response.token);
          this.isAuthenticatedSubject.next(true);
        }
      }),
      catchError(error => {
        this.isAuthenticatedSubject.next(false);
        throw error;
      })
    );
  }

  logout() {
    localStorage.removeItem('authToken');
    this.isAuthenticatedSubject.next(false);
  }

  isAuthenticated(): Observable<boolean> {
    return this.isAuthenticatedSubject.asObservable();
  }

  createAccount(credentials: { username: string; password: string, role: number }): Observable<any> {
    return this.http.post(`${this.apiUrl}/create-account`, credentials);
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
    return !!localStorage.getItem('authToken');
  }
}
