import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { User } from './models';

interface TokenResponse {
  accessToken: string;
  tokenType: string;
  user: User;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly userSubject = new BehaviorSubject<User | null>(this.readUser());
  readonly user$ = this.userSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  get user(): User | null {
    return this.userSubject.value;
  }

  get token(): string | null {
    return localStorage.getItem('foodflow.token');
  }

  get isAuthenticated(): boolean {
    return !!this.token;
  }

  login(email: string, password: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/auth/login', { email, password }).pipe(
      tap(response => {
        localStorage.setItem('foodflow.token', response.accessToken);
        localStorage.setItem('foodflow.user', JSON.stringify(response.user));
        this.userSubject.next(response.user);
      })
    );
  }

  register(fullName: string, email: string, password: string): Observable<User> {
    return this.http.post<User>('/api/auth/register', { fullName, email, password });
  }

  logout(): void {
    localStorage.removeItem('foodflow.token');
    localStorage.removeItem('foodflow.user');
    this.userSubject.next(null);
  }

  private readUser(): User | null {
    const raw = localStorage.getItem('foodflow.user');
    if (!raw) return null;
    try {
      return JSON.parse(raw) as User;
    } catch {
      localStorage.removeItem('foodflow.user');
      return null;
    }
  }
}
