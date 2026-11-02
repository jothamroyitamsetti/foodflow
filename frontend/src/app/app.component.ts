import { Component } from '@angular/core';
import { RouterLink, RouterOutlet, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterLink, RouterOutlet, RouterLinkActive, CommonModule],
  template: `
    <header>
      <div class="header-left">
        <a class="brand" routerLink="/">
          <span class="brand-logo">🍲</span>
          <span class="brand-name">FoodFlow</span>
        </a>
        <span class="badge-status">v1.0 Live</span>
      </div>

      <nav>
        <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
          Home & Menu
        </a>

        <!-- Protected Navigation Links (Only visible when logged in) -->
        <a routerLink="/orders" routerLinkActive="active" *ngIf="isLoggedIn() && (isCustomer() || isAdmin())">
          📦 My Orders
        </a>
        <a routerLink="/restaurant" routerLinkActive="active" *ngIf="isLoggedIn() && isRestaurant()">
          👨‍🍳 Restaurant Portal
        </a>
        <a routerLink="/admin" routerLinkActive="active" *ngIf="isLoggedIn() && isAdmin()">
          ⚙️ Admin Console
        </a>
        <a routerLink="/delivery" routerLinkActive="active" *ngIf="isLoggedIn() && isDelivery()">
          🛵 Delivery Dashboard
        </a>

        <!-- Guest Actions (When not logged in) -->
        <ng-container *ngIf="!isLoggedIn()">
          <a routerLink="/login" routerLinkActive="active" class="nav-login-link">Sign In</a>
          <a routerLink="/register" class="nav-register-btn">
            ✨ Register Free
          </a>
        </ng-container>

        <!-- User Profile Pill (When logged in) -->
        <div class="user-pill" *ngIf="isLoggedIn()">
          <span class="user-avatar">👤</span>
          <div class="user-details">
            <span class="user-name">{{ auth.userName() }}</span>
            <span class="user-role">{{ formatRole(auth.role()) }}</span>
          </div>
          <button class="link logout-btn" (click)="auth.logout()" title="Logout">
            Logout
          </button>
        </div>
      </nav>
    </header>

    <main>
      <router-outlet />
    </main>

    <footer>
      <div class="footer-inner">
        <p><strong>FoodFlow</strong><span *ngIf="!isLoggedIn()"> · Smart Full-Stack Food Ordering</span></p>
        <p class="footer-sub" *ngIf="!isLoggedIn()">
          Gateway (:8080) · Auth (:8081) · Catalog (:8082) · Orders (:8083) · Delivery (:8084)
        </p>
      </div>
    </footer>
  `
})
export class AppComponent {
  constructor(public auth: AuthService) {}

  isLoggedIn() {
    return !!this.auth.role();
  }

  isCustomer() {
    return this.auth.role() === 'CUSTOMER';
  }

  isAdmin() {
    return ['ADMIN'].includes(this.auth.role() ?? '');
  }

  isRestaurant() {
    return ['ADMIN', 'RESTAURANT_OWNER'].includes(this.auth.role() ?? '');
  }

  isDelivery() {
    return this.auth.role() === 'DELIVERY_PARTNER';
  }

  formatRole(role: string | null): string {
    if (!role) return '';
    return role.replace(/_/g, ' ');
  }
}
