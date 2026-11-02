import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  selector: 'app-register',
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="register-page-container">
      <div class="register-info-panel">
        <p class="eyebrow">WHY JOIN FOODFLOW</p>
        <h2>Everything you need for seamless food ordering.</h2>
        <p class="info-desc">
        Create a customer account, then sign in to place and track your orders.
        </p>

        <div class="perks-list">
          <div class="perk-item">
            <span class="perk-icon">🍽️</span>
            <div>
              <strong>Instant Menu Ordering</strong>
              <p>Add chef-crafted dishes to your personal cart with real-time stock reservation.</p>
            </div>
          </div>

          <div class="perk-item">
            <span class="perk-icon">⚡</span>
            <div>
              <strong>Live Delivery Tracking</strong>
              <p>Follow your order through restaurant preparation and delivery.</p>
            </div>
          </div>

          <div class="perk-item">
            <span class="perk-icon">🛡️</span>
            <div>
              <strong>Protected Zero-Trust Security</strong>
              <p>Sign in after registration to receive your secure access token and place orders.</p>
            </div>
          </div>
        </div>
      </div>

      <section class="form-card register-card">
        <p class="eyebrow">GET STARTED IN SECONDS</p>
        <h1>Create Account</h1>
        <p class="muted">Register your customer account to unlock live ordering and coupons.</p>

        <label>
          Full Name
          <input [(ngModel)]="name" placeholder="e.g. Maya Patel" required minlength="2" maxlength="80">
        </label>

        <label>
          Email Address
          <input [(ngModel)]="email" type="email" placeholder="e.g. maya@example.com" required>
        </label>

        <label>
          Password
          <input [(ngModel)]="password" type="password" placeholder="8-72 characters" required minlength="8" maxlength="72">
          <span class="input-hint">Must be between 8 and 72 characters long</span>
        </label>

        <label>
          Account application
          <select [(ngModel)]="applicationRole">
            <option value="CUSTOMER">Customer</option>
            <option value="RESTAURANT_OWNER">Restaurant owner (admin approval required)</option>
            <option value="DELIVERY_PARTNER">Delivery partner (admin approval required)</option>
          </select>
        </label>

        <label *ngIf="applicationRole === 'RESTAURANT_OWNER'">
          Restaurant name
          <input [(ngModel)]="restaurantName" placeholder="Your restaurant name" required maxlength="100">
        </label>

        <button
          class="register-btn"
          (click)="submit()"
          [disabled]="loading || name.trim().length < 2 || !email.includes('@') || password.length < 8 || password.length > 72 || (applicationRole === 'RESTAURANT_OWNER' && !restaurantName.trim())">
          {{ loading ? 'Creating Your Account…' : 'Create Free Account & Start Ordering 🚀' }}
        </button>

        <div class="error-box" *ngIf="error">
          <span>⚠️ {{ error }}</span>
        </div>

        <p class="form-footer">
          Already have an account? <a routerLink="/login">Sign in here</a> or <a routerLink="/">Browse what we do</a>
        </p>
      </section>
    </div>
  `
})
export class RegisterComponent implements OnInit {
  name = '';
  email = '';
  password = '';
  applicationRole = 'CUSTOMER';
  restaurantName = '';
  error = '';
  loading = false;

  constructor(private auth: AuthService, private router: Router, private route: ActivatedRoute) {}

  ngOnInit() {
    const requestedRole = this.route.snapshot.queryParams['applicationRole'];
    if (requestedRole === 'RESTAURANT_OWNER' || requestedRole === 'DELIVERY_PARTNER') {
      this.applicationRole = requestedRole;
    }
  }

  submit() {
    if (this.name.trim().length < 2
        || !this.email.includes('@')
        || this.password.length < 8
        || this.password.length > 72
        || (this.applicationRole === 'RESTAURANT_OWNER' && !this.restaurantName.trim())) return;
    this.loading = true;
    this.error = '';

    this.auth.register(
      this.name.trim(),
      this.email.trim().toLowerCase(),
      this.password,
      this.applicationRole,
      this.restaurantName.trim() || undefined
    ).subscribe({
      next: () => {
        this.router.navigate(['/login'], { queryParams: { registered: 'true', application: this.applicationRole !== 'CUSTOMER' ? 'pending' : undefined } });
      },
      error: (e) => {
        this.loading = false;
        if (e instanceof HttpErrorResponse && e.status === 409) {
          this.error = 'This email is already registered. Please sign in instead.';
        } else if (e instanceof HttpErrorResponse && e.status === 0) {
          this.error = 'Cannot reach backend server at http://localhost:8080.';
        } else if (e instanceof HttpErrorResponse && e.status === 400) {
          const response = e.error as { message?: unknown } | null;
          this.error = typeof response?.message === 'string'
            ? response.message
            : 'Please check that your name, email, password, and application details are valid.';
        } else if (e instanceof HttpErrorResponse && e.status === 404) {
          this.error = 'Registration is unavailable through the API Gateway. Restart the gateway and try again.';
        } else if (e instanceof HttpErrorResponse && (e.status === 502 || e.status === 503)) {
          this.error = 'The Auth Service is unavailable. Check that it is running and registered with Eureka.';
        } else {
          this.error = 'Registration failed because of a server error. Check the Auth Service logs and try again.';
        }
      }
    });
  }

}
