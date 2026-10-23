import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { CartService } from './core/cart.service';

@Component({
  selector: 'ff-root',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterOutlet],
  template: `
    <header class="topbar">
      <a class="brand" routerLink="/">FoodFlow</a>
      <nav *ngIf="auth.isAuthenticated">
        <a routerLink="/">Restaurants</a>
        <a routerLink="/orders">My orders</a>
        <a routerLink="/manage">Manage</a>
        <span class="muted">{{ auth.user?.fullName }} - {{ auth.user?.role }}</span>
        <button class="button secondary small" (click)="auth.logout()">Sign out</button>
      </nav>
      <a *ngIf="!auth.isAuthenticated" routerLink="/login">Sign in</a>
    </header>
    <main class="container"><router-outlet /></main>
    <footer>FoodFlow - Local food, delivered.</footer>
  `
})
export class AppComponent {
  constructor(readonly auth: AuthService, readonly cart: CartService) {}
}
