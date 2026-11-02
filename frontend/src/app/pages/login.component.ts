import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  selector: 'app-login',
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <section class="form-card">
      <div class="auth-required-notice" *ngIf="needAuth">
        <span class="auth-notice-icon">🔒</span>
        <div>
          <strong>Authentication Required</strong>
          <p>Please sign in or create an account to access that protected page.</p>
        </div>
      </div>
      <div class="success-banner" *ngIf="registered">
        Account created. Sign in with your email and password to continue.
      </div>
      <div class="success-banner" *ngIf="applicationPending">
        Your role application was submitted. You can use customer features while an administrator reviews it.
      </div>

      <p class="eyebrow">WELCOME BACK TO FOODFLOW</p>
      <h1>Sign In</h1>
      <p class="muted">Access your personalized customer account, live orders, and coupons.</p>

      <div class="first-time-box">
        <span>✨ First time visiting FoodFlow?</span>
        <a routerLink="/register" class="first-time-link">Create your free account →</a>
      </div>

      <label>
        Email Address
        <input [(ngModel)]="email" type="email" placeholder="e.g. alex@example.com" required>
      </label>

      <label>
        Password
        <input [(ngModel)]="password" type="password" placeholder="••••••••" required minlength="8">
      </label>

      <button (click)="submit()" [disabled]="loading || !email || password.length < 8">
        {{ loading ? 'Signing in…' : 'Sign In to FoodFlow' }}
      </button>

      <div class="error-box" *ngIf="error">
        <span>⚠️ {{ error }}</span>
        <p class="mini-hint" *ngIf="showOfflineHint">Backend server at http://localhost:8080 is unavailable. Please try again when it is running.</p>
      </div>

      <p class="form-footer">
        Don't have an account yet? <a routerLink="/register">Register here</a> or <a routerLink="/">Browse what we do</a>
      </p>
    </section>
  `
})
export class LoginComponent implements OnInit {
  email = '';
  password = '';
  error = '';
  showOfflineHint = false;
  loading = false;
  needAuth = false;
  registered = false;
  applicationPending = false;
  returnUrl = '';

  constructor(
    private auth: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit() {
    this.needAuth = this.route.snapshot.queryParams['needAuth'] === 'true';
    this.registered = this.route.snapshot.queryParams['registered'] === 'true';
    this.applicationPending = this.route.snapshot.queryParams['application'] === 'pending';
    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '';
  }

  submit() {
    if (!this.email || this.password.length < 8) return;
    this.loading = true;
    this.error = '';
    this.showOfflineHint = false;

    this.auth.login(this.email, this.password).subscribe({
      next: () => {
        if (this.returnUrl) {
          this.router.navigateByUrl('/' + this.returnUrl);
        } else if (this.auth.role() === 'RESTAURANT_OWNER') {
          this.router.navigateByUrl('/restaurant');
        } else if (this.auth.role() === 'DELIVERY_PARTNER') {
          this.router.navigateByUrl('/delivery');
        } else if (this.auth.role() === 'ADMIN') {
          this.router.navigateByUrl('/admin');
        } else {
          this.router.navigateByUrl('/');
        }
      },
      error: (e) => {
        this.loading = false;
        if (e.status === 0) {
          this.error = 'Backend server (port 8080) is not reachable.';
          this.showOfflineHint = true;
        } else if (e.status === 401 || e.status === 403) {
          this.error = 'Invalid email or password.';
        } else {
          this.error = 'Could not sign in. Please verify your credentials or check the backend.';
        }
      }
    });
  }
}
