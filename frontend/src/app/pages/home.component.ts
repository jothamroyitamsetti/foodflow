import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CartService } from '../core/cart.service';
import { FoodItem, Page, Restaurant } from '../core/models';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-heading">
      <div><p class="eyebrow">GOOD FOOD, ON ITS WAY</p><h1>Find your next favorite</h1></div>
      <span class="pill">{{ cart.entries.length }} items - {{ cart.total | currency }}</span>
    </div>
    <p class="error" *ngIf="error">{{ error }}</p>
    <div class="columns">
      <section>
        <h2>Restaurants</h2>
        <p *ngIf="loading">Loading restaurants...</p>
        <button class="restaurant card" *ngFor="let restaurant of restaurants"
                [class.selected]="selected?.id === restaurant.id" (click)="select(restaurant)">
          <strong>{{ restaurant.name }}</strong><span>{{ restaurant.address }}</span>
        </button>
      </section>
      <section class="menu">
        <h2>{{ selected?.name ?? 'Choose a restaurant' }}</h2>
        <p *ngIf="selected && !items.length && !loading">No available menu items.</p>
        <article class="item card" *ngFor="let item of items">
          <div><strong>{{ item.name }}</strong><p class="muted">{{ item.description }}</p>
            <small>{{ item.stockQuantity }} in stock</small></div>
          <div class="item-action"><b>{{ item.price | currency }}</b>
            <button class="button small" [disabled]="!item.available || item.stockQuantity < 1" (click)="addItem(item)">Add</button>
          </div>
        </article>
      </section>
      <aside class="cart card">
        <h2>Your cart</h2>
        <p *ngIf="!cart.entries.length" class="muted">Add a menu item to begin.</p>
        <div class="cart-line" *ngFor="let line of cart.entries">
          <span>{{ line.quantity }} x {{ line.item.name }}</span>
          <button class="link-button" (click)="cart.remove(line.item.id)">Remove</button>
        </div>
        <hr><strong>Total <span class="right">{{ cart.total | currency }}</span></strong>
        <p class="error" *ngIf="checkoutError">{{ checkoutError }}</p>
        <button class="button full" [disabled]="!cart.entries.length || submitting" (click)="checkout()">
          {{ submitting ? 'Placing order...' : 'Place order' }}
        </button>
      </aside>
    </div>
  `
})
export class HomeComponent implements OnInit {
  restaurants: Restaurant[] = [];
  items: FoodItem[] = [];
  selected: Restaurant | null = null;
  loading = false;
  submitting = false;
  error = '';
  checkoutError = '';

  constructor(private readonly http: HttpClient, readonly cart: CartService, private readonly router: Router) {}

  ngOnInit(): void {
    this.loading = true;
    this.http.get<Page<Restaurant>>('/api/catalog/restaurants?page=0&size=50').subscribe({
      next: page => { this.restaurants = page.content; this.loading = false; },
      error: error => { this.error = error.error?.message ?? 'Could not load restaurants.'; this.loading = false; }
    });
  }

  select(restaurant: Restaurant): void {
    this.selected = restaurant;
    this.loading = true;
    this.http.get<Page<FoodItem>>(`/api/catalog/restaurants/${restaurant.id}/items?page=0&size=100`).subscribe({
      next: page => { this.items = page.content; this.loading = false; },
      error: error => { this.error = error.error?.message ?? 'Could not load the menu.'; this.loading = false; }
    });
  }

  addItem(item: FoodItem): void {
    if (this.cart.entries.some(line => line.item.restaurantId !== item.restaurantId)) {
      this.checkoutError = 'A cart can contain items from one restaurant only.';
      return;
    }
    this.checkoutError = '';
    this.cart.add(item);
  }

  checkout(): void {
    if (!this.cart.entries.length) return;
    this.submitting = true;
    this.checkoutError = '';
    this.http.post('/api/orders', {
      items: this.cart.entries.map(line => ({ itemId: line.item.id, quantity: line.quantity }))
    }, { headers: { 'Idempotency-Key': crypto.randomUUID() } }).subscribe({
      next: () => {
        this.cart.clear();
        this.submitting = false;
        void this.router.navigate(['/orders']);
      },
      error: error => {
        this.checkoutError = error.error?.message ?? 'Order failed. Please retry.';
        this.submitting = false;
      }
    });
  }
}
