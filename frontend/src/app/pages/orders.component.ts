import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FoodOrder, Page } from '../core/models';

@Component({
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-heading"><div><p class="eyebrow">YOUR FOOD JOURNEY</p><h1>My orders</h1></div></div>
    <p *ngIf="loading">Loading orders...</p><p class="error" *ngIf="error">{{ error }}</p>
    <article class="card order-card" *ngFor="let order of orders">
      <div class="order-title"><div><strong>Order {{ order.id | slice:0:8 }}</strong>
        <p class="muted">{{ order.createdAt | date:'medium' }}</p></div>
        <span class="pill">{{ order.status }}</span></div>
      <p *ngFor="let line of order.items">{{ line.quantity }} x {{ line.itemName }} - {{ line.unitPrice | currency }}</p>
      <strong>Total: {{ order.total | currency }}</strong>
      <button *ngIf="order.status === 'PAID'" class="button secondary small" (click)="cancel(order)">Cancel order</button>
      <p *ngIf="order.statusHistory.length" class="muted">Status history:
        {{ formatHistory(order) }}</p>
    </article>
    <p *ngIf="!loading && !orders.length">You have not placed an order yet.</p>
  `
})
export class OrdersComponent implements OnInit {
  orders: FoodOrder[] = [];
  loading = true;
  error = '';

  constructor(private readonly http: HttpClient) {}

  ngOnInit(): void {
    this.load();
  }

  cancel(order: FoodOrder): void {
    this.http.post<FoodOrder>(`/api/orders/${order.id}/cancel`, {}).subscribe({
      next: updated => this.orders = this.orders.map(item => item.id === updated.id ? updated : item),
      error: error => this.error = error.error?.message ?? 'Could not cancel order.'
    });
  }

  formatHistory(order: FoodOrder): string {
    return order.statusHistory.map(event => event.status).join(' -> ');
  }

  private load(): void {
    this.http.get<Page<FoodOrder>>('/api/orders/mine?page=0&size=25&sort=createdAt,desc').subscribe({
      next: page => { this.orders = page.content; this.loading = false; },
      error: error => { this.error = error.error?.message ?? 'Could not load orders.'; this.loading = false; }
    });
  }
}
