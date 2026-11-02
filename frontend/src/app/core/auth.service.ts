import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { API_CONFIG } from './config';

export interface AuthResponse {
  token: string;
  tokenType: string;
  userId: string;
  fullName: string;
  email: string;
  role: string;
}

export interface RegistrationResponse {
  userId: string;
  fullName: string;
  email: string;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly base = API_CONFIG.authUrl;

  readonly role = signal<string | null>(sessionStorage.getItem('foodflow_role'));
  readonly userName = signal<string | null>(sessionStorage.getItem('foodflow_name'));

  constructor(private http: HttpClient, private router: Router) {}

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/login`, { email, password })
      .pipe(tap(r => this.save(r)));
  }

  register(fullName: string, email: string, password: string, applicationRole = 'CUSTOMER', restaurantName?: string): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(`${this.base}/register`, {
      fullName, email, password, applicationRole, restaurantName
    });
  }

  private save(r: AuthResponse) {
    sessionStorage.setItem('foodflow_token', r.token);
    sessionStorage.setItem('foodflow_role', r.role);
    sessionStorage.setItem('foodflow_user_id', r.userId);
    sessionStorage.setItem('foodflow_name', r.fullName);
    this.role.set(r.role);
    this.userName.set(r.fullName);
  }

  logout() {
    sessionStorage.removeItem('foodflow_token');
    sessionStorage.removeItem('foodflow_role');
    sessionStorage.removeItem('foodflow_user_id');
    sessionStorage.removeItem('foodflow_name');
    this.role.set(null);
    this.userName.set(null);
    this.router.navigateByUrl('/login');
  }

  token(): string | null {
    return sessionStorage.getItem('foodflow_token');
  }

}
