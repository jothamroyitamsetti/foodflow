import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, PageResponse, ReviewStatus } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { EMPTY, Subject, catchError, exhaustMap, filter, interval, takeUntil } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-orders',
  imports: [CommonModule, CurrencyPipe, DatePipe, FormsModule],
  template: `
    <section>
      <div class="page-title-row">
        <div>
          <p class="eyebrow">YOUR FOODFLOW</p>
          <h1>Order History</h1>
          <p class="muted">Order statuses refresh automatically every 10 seconds while this page is open.</p>
        </div>
        <button class="secondary" (click)="load()" [disabled]="loading">
          🔄 {{ loading ? 'Refreshing...' : 'Refresh Orders' }}
        </button>
      </div>

      <div class="offline-banner" *ngIf="error && !loading">
        <div class="offline-text">
          <span class="offline-icon">ℹ️</span>
          <span>{{ error }}</span>
        </div>
      </div>

      <div class="loading-state" *ngIf="loading">
        <div class="spinner"></div>
        <p>Fetching your orders...</p>
      </div>

      <div class="empty-state" *ngIf="!orders.length && !error && !loading">
        <span class="empty-icon">📦</span>
        <h3>No orders yet</h3>
        <p class="muted">You haven't placed any orders yet. Visit the Menu to get started!</p>
      </div>

      <article class="order" *ngFor="let o of orders">
        <div class="order-header">
          <div>
            <strong>Order #{{ (o.id || '1001') | slice:0:8 }}…</strong>
            <span class="pill" [class]="'pill-' + (o.status ? o.status.toLowerCase() : 'pending')">
              {{ o.status || 'PENDING' }}
            </span>
          </div>
          <div class="muted" *ngIf="o.statusHistory?.length">
            <strong>Order updates:</strong>
            <span *ngFor="let change of o.statusHistory; let last = last">
              {{ change.status }} ({{ change.changedAt | date:'short' }})<span *ngIf="!last"> → </span>
            </span>
          </div>
          <span class="order-date muted">{{ (o.createdAt || now) | date:'medium' }}</span>
        </div>

        <div class="order-pricing">
          <h3 class="order-total">{{ o.totalAmount | currency:'INR' }}</h3>
        </div>
        <p class="muted">Payment: <strong>{{ o.paymentMethod === 'COD' ? 'Cash on delivery' : 'Razorpay' }}</strong>
          <span *ngIf="o.paymentMethod === 'COD'"> · {{ o.paymentStatus === 'CASH_COLLECTED' ? 'Cash collected' : 'Pay the delivery partner on arrival' }}</span>
          <span *ngIf="o.paymentMethod !== 'COD'"> · {{ o.paymentStatus }}</span>
        </p>
        <p class="offline-banner" *ngIf="o.status === 'REJECTED'">
          The restaurant rejected this order.
          <span *ngIf="o.paymentStatus === 'REFUNDED'">Your Razorpay payment was refunded.</span>
          <span *ngIf="o.paymentStatus === 'REFUND_PENDING'">Your Razorpay refund is being processed.</span>
        </p>
        <button class="danger" *ngIf="canCancel(o)" (click)="cancel(o)" [disabled]="processingId === o.id">
          {{ processingId === o.id ? 'Cancelling…' : 'Cancel order' }}
        </button>

        <div *ngIf="o.items?.length" class="order-items">
          <p class="muted item-row" *ngFor="let item of o.items">
            <span>{{ item.itemName || item.menuItemId || 'Food Item' }}</span>
            <span class="qty-badge">× {{ item.quantity }}</span>
          </p>
        </div>

        <section class="form-card" *ngIf="o.status === 'DELIVERED' && reviewStatuses[o.id]">
          <div *ngIf="reviewStatuses[o.id].reviewed">
            <strong>Your review · {{ reviewStatuses[o.id].rating }}/5</strong>
            <p class="muted" *ngIf="reviewStatuses[o.id].comment">{{ reviewStatuses[o.id].comment }}</p>
          </div>
          <div *ngIf="!reviewStatuses[o.id].reviewed">
            <h3>How was your order?</h3>
            <p class="muted">Share a rating and optional comment for the restaurant.</p>
            <div class="actions" role="group" aria-label="Choose a rating from 1 to 5">
              <button type="button" class="secondary" *ngFor="let star of ratings"
                [attr.aria-pressed]="reviewDrafts[o.id].rating === star"
                (click)="setRating(o.id, star)">
                {{ star }} ★
              </button>
            </div>
            <textarea [(ngModel)]="reviewDrafts[o.id].comment" maxlength="1000" rows="3"
              placeholder="Optional feedback"></textarea>
            <p class="error-banner" *ngIf="reviewErrors[o.id]">{{ reviewErrors[o.id] }}</p>
            <button type="button" (click)="submitReview(o)" [disabled]="processingReviewId === o.id">
              {{ processingReviewId === o.id ? 'Submitting…' : 'Submit review' }}
            </button>
          </div>
        </section>
      </article>
    </section>
  `
})
export class OrdersComponent implements OnInit, OnDestroy {
  orders: any[] = [];
  error = '';
  loading = false;
  now = new Date();
  processingId: string | null = null;
  processingReviewId: string | null = null;
  reviewStatuses: Record<string, ReviewStatus> = {};
  reviewDrafts: Record<string, { rating: number; comment: string }> = {};
  reviewErrors: Record<string, string> = {};
  readonly ratings = [1, 2, 3, 4, 5];
  private readonly destroyed$ = new Subject<void>();
  private readonly reviewStatusLoading = new Set<string>();

  constructor(private api: ApiService, public auth: AuthService) {}

  ngOnInit() {
    this.load();
    interval(10_000).pipe(
      takeUntil(this.destroyed$),
      filter(() => !this.loading),
      exhaustMap(() => this.api.orders().pipe(
        catchError(() => {
          this.error = 'Live order updates are temporarily unavailable. Your displayed order status may be out of date.';
          return EMPTY;
        })
      ))
    ).subscribe(response => {
      this.error = '';
      this.updateOrders(response);
    });
  }

  ngOnDestroy() {
    this.destroyed$.next();
    this.destroyed$.complete();
  }

  load() {
    this.loading = true;
    this.error = '';

    this.api.orders().subscribe({
      next: (r: PageResponse<any>) => {
        this.loading = false;
        this.updateOrders(r);
      },
      error: () => {
        this.loading = false;
        this.error = 'Could not load your orders. Check the order service and try again.';
      }
    });
  }

  canCancel(order: any): boolean {
    return ['PENDING', 'PENDING_PAYMENT'].includes(order.status)
      && !['PAID', 'CASH_COLLECTED'].includes(order.paymentStatus);
  }

  cancel(order: any) {
    if (!window.confirm('Cancel this order? The reserved items will be returned to stock.')) return;
    this.processingId = order.id;
    this.api.cancelOrder(order.id).subscribe({
      next: () => {
        this.processingId = null;
        this.load();
      },
      error: error => {
        this.processingId = null;
        const message = error.error?.message;
        this.error = typeof message === 'string'
          ? message
          : `Could not cancel order (HTTP ${error.status || 'unknown'}). Refresh and retry.`;
      }
    });
  }

  private loadReviewStatuses() {
    for (const order of this.orders.filter(item => item.status === 'DELIVERED')) {
      this.reviewDrafts[order.id] ??= { rating: 5, comment: '' };
      if (this.reviewStatuses[order.id] || this.reviewStatusLoading.has(order.id)) continue;
      this.reviewStatusLoading.add(order.id);
      this.api.reviewStatus(order.id).subscribe({
        next: status => {
          this.reviewStatuses[order.id] = status;
          this.reviewStatusLoading.delete(order.id);
        },
        error: () => {
          this.reviewErrors[order.id] = 'Could not load review status. Refresh and retry.';
          this.reviewStatusLoading.delete(order.id);
        }
      });
    }
  }

  private updateOrders(response: PageResponse<any>) {
    this.orders = response.content ?? [];
    this.loadReviewStatuses();
  }

  setRating(orderId: string, rating: number) {
    const draft = this.reviewDrafts[orderId];
    if (draft) draft.rating = rating;
  }

  submitReview(order: any) {
    if (!order.restaurantId) {
      this.reviewErrors[order.id] = 'This order is not linked to a restaurant, so it cannot be reviewed.';
      return;
    }
    const draft = this.reviewDrafts[order.id];
    if (!draft || this.processingReviewId) return;
    this.processingReviewId = order.id;
    this.reviewErrors[order.id] = '';
    this.api.submitReview({
      orderId: order.id,
      restaurantId: order.restaurantId,
      rating: draft.rating,
      comment: draft.comment.trim()
    }).subscribe({
      next: () => {
        this.reviewStatuses[order.id] = {
          orderId: order.id,
          reviewed: true,
          rating: draft.rating,
          comment: draft.comment.trim() || null
        };
        this.processingReviewId = null;
      },
      error: error => {
        const message = error.error?.message;
        this.reviewErrors[order.id] = typeof message === 'string'
          ? message
          : `Could not submit review (HTTP ${error.status || 'unknown'}). Retry.`;
        this.processingReviewId = null;
      }
    });
  }
}
