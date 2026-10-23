import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { Delivery, FoodItem, FoodOrder, Page, Restaurant } from '../core/models';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="page-heading"><div><p class="eyebrow">FOODFLOW OPERATIONS</p><h1>Manage</h1></div></div>
    <p class="error" *ngIf="error">{{ error }}</p><p class="success" *ngIf="message">{{ message }}</p>
    <section *ngIf="auth.user?.role === 'DELIVERY_PARTNER'">
      <h2>My deliveries</h2>
      <article class="card order-card" *ngFor="let delivery of deliveries">
        <strong>Order {{ delivery.orderId | slice:0:8 }}</strong>
        <span class="pill">{{ delivery.status }}</span>
        <button class="button small" *ngIf="delivery.status === 'ASSIGNED'" (click)="updateDelivery(delivery, 'PICKED_UP')">Mark picked up</button>
        <button class="button small" *ngIf="delivery.status === 'PICKED_UP'" (click)="updateDelivery(delivery, 'DELIVERED')">Mark delivered</button>
      </article>
      <p *ngIf="!deliveries.length">No deliveries assigned.</p>
    </section>
    <section *ngIf="auth.user?.role !== 'DELIVERY_PARTNER'">
      <div class="columns ops-columns">
        <div>
          <h2>Your restaurants</h2>
          <button class="restaurant card" *ngFor="let restaurant of restaurants"
                  [class.selected]="selected?.id === restaurant.id" (click)="select(restaurant)">
            <strong>{{ restaurant.name }}</strong><span>{{ restaurant.address }}</span>
          </button>
          <h3 *ngIf="auth.user?.role === 'RESTAURANT_OWNER'">Add restaurant</h3>
          <form *ngIf="auth.user?.role === 'RESTAURANT_OWNER'" [formGroup]="restaurantForm" (ngSubmit)="createRestaurant()">
            <label>Name<input formControlName="name"></label>
            <label>Address<input formControlName="address"></label>
            <button class="button" [disabled]="restaurantForm.invalid">Create restaurant</button>
          </form>
        </div>
        <div *ngIf="selected">
          <h2>{{ selected.name }} - menu</h2>
          <form class="inline-form" [formGroup]="itemForm" (ngSubmit)="saveItem()">
            <label>Item<input formControlName="name"></label>
            <label>Description<input formControlName="description"></label>
            <label>Price<input type="number" min="0.01" step="0.01" formControlName="price"></label>
            <label>Stock<input type="number" min="0" formControlName="stockQuantity"></label>
            <button class="button" [disabled]="itemForm.invalid">{{ editingItem ? 'Save changes' : 'Add item' }}</button>
            <button *ngIf="editingItem" type="button" class="button secondary" (click)="resetItemForm()">Cancel edit</button>
          </form>
          <article class="item card" *ngFor="let item of items">
            <span>{{ item.name }} - {{ item.price | currency }} - stock {{ item.stockQuantity }}</span>
            <div><button class="link-button" (click)="editItem(item)">Edit</button>
              <button class="link-button" (click)="deleteItem(item)">Delete</button></div>
          </article>
          <h2>Restaurant orders</h2>
          <article class="card order-card" *ngFor="let order of orders">
            <strong>Order {{ order.id | slice:0:8 }}</strong><span class="pill">{{ order.status }}</span>
            <p>{{ order.total | currency }} - {{ order.items.length }} items</p>
            <button class="button small" *ngIf="order.status === 'PAID'" (click)="advanceOrder(order, 'ACCEPTED')">Accept</button>
            <button class="button small" *ngIf="order.status === 'ACCEPTED'" (click)="advanceOrder(order, 'PREPARING')">Start preparing</button>
            <button class="button small" *ngIf="order.status === 'PREPARING'" (click)="advanceOrder(order, 'READY')">Mark ready</button>
            <button class="button secondary small" *ngIf="order.status === 'READY' && auth.user?.role === 'ADMIN'"
                    (click)="assign(order)">Assign delivery</button>
          </article>
        </div>
      </div>
    </section>
  `
})
export class OperationsComponent implements OnInit {
  restaurants: Restaurant[] = [];
  items: FoodItem[] = [];
  orders: FoodOrder[] = [];
  deliveries: Delivery[] = [];
  selected: Restaurant | null = null;
  editingItem: FoodItem | null = null;
  error = '';
  message = '';
  restaurantForm = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    address: ['', [Validators.required, Validators.maxLength(300)]]
  });
  itemForm = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    description: [''],
    price: [1, [Validators.required, Validators.min(0.01)]],
    stockQuantity: [1, [Validators.required, Validators.min(0)]]
  });

  constructor(readonly auth: AuthService, private readonly fb: FormBuilder, private readonly http: HttpClient) {}

  ngOnInit(): void {
    if (this.auth.user?.role === 'DELIVERY_PARTNER') {
      this.loadDeliveries();
      return;
    }
    this.http.get<Page<Restaurant>>('/api/catalog/restaurants?page=0&size=100').subscribe({
      next: page => {
        this.restaurants = page.content.filter(item =>
          this.auth.user?.role === 'ADMIN' || item.ownerId === this.auth.user?.id);
      },
      error: error => this.showError(error)
    });
  }

  select(restaurant: Restaurant): void {
    this.selected = restaurant;
    this.loadItems();
    this.loadOrders();
  }

  createRestaurant(): void {
    if (this.restaurantForm.invalid) return;
    this.http.post<Restaurant>('/api/catalog/restaurants', this.restaurantForm.getRawValue()).subscribe({
      next: restaurant => { this.restaurants = [...this.restaurants, restaurant]; this.restaurantForm.reset(); this.message = 'Restaurant created.'; },
      error: error => this.showError(error)
    });
  }

  saveItem(): void {
    if (!this.selected || this.itemForm.invalid) return;
    const value = this.itemForm.getRawValue();
    const request = { ...value, restaurantId: this.selected.id };
    const save = this.editingItem
      ? this.http.put<FoodItem>(`/api/catalog/items/${this.editingItem.id}`, request)
      : this.http.post<FoodItem>('/api/catalog/items', request);
    save.subscribe({
      next: item => {
        this.items = this.editingItem
          ? this.items.map(existing => existing.id === item.id ? item : existing)
          : [...this.items, item];
        this.resetItemForm();
        this.message = 'Menu item saved.';
      },
      error: error => this.showError(error)
    });
  }

  editItem(item: FoodItem): void {
    this.editingItem = item;
    this.itemForm.patchValue({
      name: item.name,
      description: item.description,
      price: item.price,
      stockQuantity: item.stockQuantity
    });
  }

  resetItemForm(): void {
    this.editingItem = null;
    this.itemForm.reset({ name: '', description: '', price: 1, stockQuantity: 1 });
  }

  deleteItem(item: FoodItem): void {
    this.http.delete(`/api/catalog/items/${item.id}`).subscribe({
      next: () => this.items = this.items.filter(entry => entry.id !== item.id),
      error: error => this.showError(error)
    });
  }

  advanceOrder(order: FoodOrder, status: string): void {
    this.http.patch<FoodOrder>(`/api/orders/${order.id}/status`, { status }).subscribe({
      next: updated => { this.orders = this.orders.map(item => item.id === updated.id ? updated : item); this.message = `Order is ${status.toLowerCase()}.`; },
      error: error => this.showError(error)
    });
  }

  assign(order: FoodOrder): void {
    this.http.post<Delivery>(`/api/deliveries/orders/${order.id}/assign`, {}).subscribe({
      next: () => { this.message = 'Delivery partner assigned.'; this.loadOrders(); },
      error: error => this.showError(error)
    });
  }

  updateDelivery(delivery: Delivery, status: 'PICKED_UP' | 'DELIVERED'): void {
    this.http.patch<Delivery>(`/api/deliveries/${delivery.id}/status`, { status }).subscribe({
      next: updated => this.deliveries = this.deliveries.map(item => item.id === updated.id ? updated : item),
      error: error => this.showError(error)
    });
  }

  private loadItems(): void {
    if (!this.selected) return;
    this.http.get<Page<FoodItem>>(`/api/catalog/restaurants/${this.selected.id}/items?page=0&size=100`).subscribe({
      next: page => this.items = page.content,
      error: error => this.showError(error)
    });
  }

  private loadOrders(): void {
    if (!this.selected) return;
    this.http.get<Page<FoodOrder>>(`/api/orders/restaurant/${this.selected.id}?page=0&size=100&sort=createdAt,desc`).subscribe({
      next: page => this.orders = page.content,
      error: error => this.showError(error)
    });
  }

  private loadDeliveries(): void {
    this.http.get<Page<Delivery>>('/api/deliveries/mine?page=0&size=100').subscribe({
      next: page => this.deliveries = page.content,
      error: error => this.showError(error)
    });
  }

  private showError(error: { error?: { message?: string } }): void {
    this.error = error.error?.message ?? 'The request could not be completed.';
    this.message = '';
  }
}
