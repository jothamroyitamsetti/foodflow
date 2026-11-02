import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService } from '../core/api.service';

@Component({
  standalone: true,
  selector: 'app-delivery',
  imports: [CommonModule],
  template: `
    <section>
      <div class="page-title-row">
        <div>
          <p class="eyebrow">DELIVERY PARTNER</p>
          <h1>Available & Assigned Deliveries</h1>
          <p class="muted">Status: {{ available ? 'Online and accepting deliveries' : 'Offline' }}</p>
        </div>
        <div class="actions">
          <button class="secondary" (click)="toggleAvailability()" [disabled]="updatingAvailability">
            {{ updatingAvailability ? 'Updating…' : (available ? 'Go offline' : 'Go online') }}
          </button>
          <button class="secondary" (click)="load()" [disabled]="loading">
            🔄 {{ loading ? 'Refreshing...' : 'Refresh Deliveries' }}
          </button>
        </div>
      </div>

      <div class="offline-banner" *ngIf="message">
        <div class="offline-text">
          <span class="offline-icon">⚠️</span>
          <span>{{ message }}</span>
        </div>
      </div>

      <div class="loading-state" *ngIf="loading">
        <div class="spinner"></div>
        <p>Loading your deliveries...</p>
      </div>

      <div class="empty-state" *ngIf="!deliveries.length && !loading">
        <span class="empty-icon">🛵</span>
        <h3>No deliveries available</h3>
        <p class="muted">Orders appear here when a restaurant marks them ready for pickup.</p>
      </div>

      <article class="order" *ngFor="let d of deliveries">
        <div class="order-header">
          <div>
            <strong>Delivery #{{ (d.id || 'd-001') | slice:0:8 }}…</strong>
            <span class="pill" [class]="'pill-' + (d.status ? d.status.toLowerCase() : 'pending')">
              {{ d.status }}
            </span>
          </div>
          <span class="muted">Order Ref: #{{ (d.orderId || 'ord-100') | slice:0:8 }}…</span>
        </div>

        <div class="delivery-address-box" *ngIf="d.deliveryAddress">
          <p class="muted">📍 Drop location: <strong>{{ d.deliveryAddress }}</strong></p>
        </div>
        <p class="muted">Payment: <strong>{{ d.paymentMethod === 'COD' ? 'Cash on delivery' : 'Paid online' }}</strong></p>

        <div class="actions">
          <button
            *ngIf="d.status === 'AVAILABLE'"
            (click)="claim(d.id)"
            [disabled]="processingId === d.id || !available">
            {{ processingId === d.id ? 'Claiming…' : '🛵 Accept Delivery' }}
          </button>
          <button
            *ngIf="d.status === 'ASSIGNED'"
            (click)="update(d.id, 'PICKED_UP')"
            [disabled]="processingId === d.id">
            📦 Confirm Picked Up from Kitchen
          </button>
          <button
            *ngIf="d.status === 'PICKED_UP'"
            (click)="update(d.id, 'DELIVERED')"
            [disabled]="processingId === d.id">
            ✅ {{ d.paymentMethod === 'COD' ? 'Collect cash and complete delivery' : 'Mark Order Delivered to Customer' }}
          </button>
          <div class="delivery-done" *ngIf="d.status === 'DELIVERED'">
            <span>🎉 Delivery completed successfully</span>
          </div>
        </div>
      </article>
    </section>
  `
})
export class DeliveryComponent implements OnInit {
  deliveries: any[] = [];
  message = '';
  loading = false;
  available = true;
  updatingAvailability = false;
  processingId: string | null = null;

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.loadAvailability();
    this.load();
  }

  loadAvailability() {
    this.api.partnerAvailability().subscribe({
      next: profile => this.available = profile.available,
      error: (error: unknown) => this.message = this.requestError(error, 'load your availability')
    });
  }

  toggleAvailability() {
    this.updatingAvailability = true;
    this.api.setPartnerAvailability(!this.available).subscribe({
      next: profile => {
        this.available = profile.available;
        this.updatingAvailability = false;
        this.message = profile.available
          ? 'You are online and can accept deliveries.'
          : 'You are offline. Existing assigned deliveries remain available to complete.';
      },
      error: (error: unknown) => {
        this.updatingAvailability = false;
        this.message = this.requestError(error, 'update your availability');
      }
    });
  }

  load() {
    this.loading = true;
    this.message = '';

    this.api.deliveries().subscribe({
      next: (r: any[]) => {
        this.deliveries = r ?? [];
        this.loading = false;
      },
      error: (error: unknown) => {
        this.loading = false;
        this.message = this.requestError(error, 'load deliveries');
      }
    });
  }

  claim(id: string) {
    this.processingId = id;
    this.message = '';
    this.api.claimDelivery(id).subscribe({
      next: () => {
        this.processingId = null;
        this.load();
      },
      error: (error: unknown) => {
        this.processingId = null;
        this.message = error instanceof HttpErrorResponse && error.status === 409
          ? 'Another delivery partner has already claimed this order. Refresh the list.'
          : this.requestError(error, 'accept this delivery');
      }
    });
  }

  update(id: string, status: string) {
    const delivery = this.deliveries.find(item => item.id === id);
    const cashCollected = status === 'DELIVERED' && delivery?.paymentMethod === 'COD';
    if (cashCollected && !window.confirm('Confirm that you collected the full order amount in cash from the customer.')) return;
    this.processingId = id;
    this.message = '';
    this.api.updateDelivery(id, status, cashCollected).subscribe({
      next: () => {
        this.processingId = null;
        this.load();
      },
      error: (error: unknown) => {
        this.processingId = null;
        this.message = this.requestError(error, 'update this delivery');
      }
    });
  }

  private requestError(error: unknown, action: string): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as { message?: unknown } | null;
      const detail = typeof body?.message === 'string' ? body.message : '';
      if (detail) return detail;
      if (error.status === 0) {
        return `Could not reach FoodFlow to ${action}. Check the gateway and services, then retry.`;
      }
      return `Could not ${action} (HTTP ${error.status}). Refresh and retry.`;
    }
    return `Could not ${action}. Refresh and retry.`;
  }
}
