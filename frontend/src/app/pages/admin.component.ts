import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminUser, ApiService, Coupon, CouponCreateRequest, Restaurant, RoleApplication } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  selector: 'app-admin',
  imports: [CommonModule, FormsModule],
  template: `
    <section class="form-card admin-dashboard" *ngIf="isAdmin">
      <p class="eyebrow">PROMOTIONS</p>
      <h2>Create a coupon</h2>
      <p class="muted">Customers can apply active coupons in their cart. Codes are case-insensitive.</p>
      <div class="coupon-row">
        <label>Code
          <input [(ngModel)]="couponDraft.code" maxlength="30" placeholder="e.g. WELCOME10">
        </label>
        <label>Discount (%)
          <input type="number" [(ngModel)]="couponDraft.discountPercent" min="0.01" max="100" step="0.01">
        </label>
        <label>Maximum uses
          <input type="number" [(ngModel)]="couponDraft.maxUses" min="1" step="1">
        </label>
        <label>Expires at
          <input type="datetime-local" [(ngModel)]="couponDraft.expiresAt">
        </label>
        <button type="button" (click)="createCoupon()" [disabled]="creatingCoupon">
          {{ creatingCoupon ? 'Creating…' : 'Create coupon' }}
        </button>
      </div>
      <p class="muted" *ngIf="couponMessage">{{ couponMessage }}</p>
      <article class="order" *ngFor="let coupon of coupons">
        <strong>{{ coupon.code }} — {{ coupon.discountPercent }}% off</strong>
        <p>Uses: {{ coupon.usedCount }}/{{ coupon.maxUses }} · Expires {{ coupon.expiresAt | date:'medium' }}</p>
        <span class="pill">{{ couponIsUsable(coupon) ? 'ACTIVE' : 'UNAVAILABLE' }}</span>
      </article>
      <p class="muted" *ngIf="!coupons.length">No coupons created yet.</p>

      <p class="eyebrow">ADMIN CONSOLE</p>
      <h1>Role Applications</h1>
      <p class="muted">Approve applicants before granting restaurant-owner or delivery-partner access.</p>
      <p class="muted" *ngIf="!applications.length">No applications are waiting for review.</p>
      <article class="order" *ngFor="let application of applications">
        <strong>{{ application.fullName }} — {{ application.requestedRole }}</strong>
        <p>{{ application.email }}<span *ngIf="application.restaurantName"> · {{ application.restaurantName }}</span></p>
        <p class="muted" *ngIf="application.rejectionReason">Rejection reason: {{ application.rejectionReason }}</p>
        <div class="actions" *ngIf="application.status === 'PENDING'">
          <button (click)="review(application, true)" [disabled]="processingId === application.id">Approve</button>
          <button class="danger" (click)="review(application, false)" [disabled]="processingId === application.id">Reject</button>
        </div>
        <span class="pill">{{ application.status }}</span>
      </article>
      <div class="page-title-row">
        <h2>Registered users</h2>
        <button class="secondary" (click)="loadAdminUsers()" [disabled]="loadingUsers">
          {{ loadingUsers ? 'Loading…' : 'Refresh users' }}
        </button>
      </div>
      <p class="muted" *ngIf="userPage">Page {{ userPage + 1 }} · {{ totalUsers }} accounts</p>
      <article class="order" *ngFor="let user of adminUsers">
        <strong>{{ user.fullName }} — {{ user.role }}</strong>
        <p>{{ user.email }}</p>
        <p class="muted">Registered {{ user.createdAt | date:'medium' }}</p>
      </article>
      <div class="actions">
        <button class="secondary" *ngIf="userPage > 0" (click)="loadAdminUsers(userPage - 1)" [disabled]="loadingUsers">Previous users</button>
        <button class="secondary" *ngIf="hasMoreUsers" (click)="loadAdminUsers(userPage + 1)" [disabled]="loadingUsers">Next users</button>
      </div>
      <h2>Restaurant status</h2>
      <article class="order" *ngFor="let item of restaurants">
        <strong>{{ item.name }}</strong>
        <span class="pill">{{ item.status }}{{ item.acceptingOrders ? ' · ACCEPTING ORDERS' : '' }}</span>
        <div class="actions">
          <button class="secondary" (click)="changeRestaurantStatus(item, item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE')">
            {{ item.status === 'ACTIVE' ? 'Pause restaurant' : 'Activate restaurant' }}
          </button>
          <button class="danger" *ngIf="item.status !== 'SUSPENDED'" (click)="changeRestaurantStatus(item, 'SUSPENDED')">
            Suspend
          </button>
        </div>
      </article>
      <div class="page-title-row">
        <h2>Orders and payments</h2>
        <button class="secondary" (click)="loadAdminOrders()" [disabled]="loadingOrders">
          {{ loadingOrders ? 'Loading…' : 'Refresh orders' }}
        </button>
      </div>
      <p class="muted">Most recent {{ adminOrders.length }} orders: review payment method, payment state, amount, and eligible refunds.</p>
      <article class="order" *ngFor="let order of adminOrders">
        <strong>Order #{{ order.id | slice:0:8 }}</strong>
        <span class="pill">{{ order.status }}</span>
        <p>Payment: {{ order.paymentMethod }} · {{ order.paymentStatus }} · {{ order.totalAmount | currency:'INR' }}</p>
        <p class="muted">Placed {{ order.createdAt | date:'medium' }}</p>
        <button class="danger"
          *ngIf="order.paymentMethod === 'RAZORPAY' && order.paymentStatus === 'PAID' && ['PENDING', 'ACCEPTED'].includes(order.status)"
          (click)="refund(order)" [disabled]="processingId === order.id">
          {{ processingId === order.id ? 'Processing…' : 'Refund and cancel' }}
        </button>
        <button class="secondary"
          *ngIf="order.paymentStatus === 'REFUND_PENDING'"
          (click)="refreshRefund(order)" [disabled]="processingId === order.id">
          Check refund status
        </button>
      </article>
      <p class="muted" *ngIf="!loadingOrders && !adminOrders.length">No orders have been placed.</p>
      <p class="muted" *ngIf="reviewMessage">{{ reviewMessage }}</p>
    </section>

  `
})
export class AdminComponent implements OnInit {
  restaurants: Restaurant[] = [];
  applications: RoleApplication[] = [];
  adminOrders: any[] = [];
  adminUsers: AdminUser[] = [];
  userPage = 0;
  totalUsers = 0;
  hasMoreUsers = false;
  reviewMessage = '';
  couponMessage = '';
  coupons: Coupon[] = [];
  creatingCoupon = false;
  couponDraft: CouponCreateRequest = {
    code: '',
    discountPercent: 10,
    maxUses: 100,
    expiresAt: ''
  };
  processingId: string | null = null;
  loadingOrders = false;
  loadingUsers = false;
  readonly isAdmin: boolean;

  constructor(private api: ApiService, auth: AuthService) {
    this.isAdmin = auth.role() === 'ADMIN';
  }

  ngOnInit() {
    if (this.isAdmin) {
      this.loadApplications();
      this.loadAdminRestaurants();
      this.loadAdminOrders();
      this.loadAdminUsers();
      this.loadCoupons();
    }
  }

  createCoupon() {
    const request = {
      ...this.couponDraft,
      code: this.couponDraft.code.trim().toUpperCase()
    };
    if (!request.code || !request.expiresAt || request.discountPercent < 0.01
        || request.discountPercent > 100 || request.maxUses < 1) {
      this.couponMessage = 'Enter a code, a discount from 0.01 to 100, a positive use limit, and a future expiry.';
      return;
    }
    this.creatingCoupon = true;
    this.couponMessage = '';
    this.api.createCoupon(request).subscribe({
      next: coupon => {
        this.couponMessage = `Coupon ${coupon.code} created.`;
        this.couponDraft = { code: '', discountPercent: 10, maxUses: 100, expiresAt: '' };
        this.creatingCoupon = false;
        this.loadCoupons();
      },
      error: error => {
        const message = error.error?.message;
        this.couponMessage = typeof message === 'string'
          ? message
          : `Could not create coupon (HTTP ${error.status || 'unknown'}). Check the code and expiry.`;
        this.creatingCoupon = false;
      }
    });
  }

  private loadCoupons() {
    this.api.coupons().subscribe({
      next: coupons => this.coupons = coupons ?? [],
      error: () => this.couponMessage = 'Could not load coupons. Check Coupon Service.'
    });
  }

  couponIsUsable(coupon: Coupon): boolean {
    return coupon.active
      && coupon.usedCount < coupon.maxUses
      && new Date(coupon.expiresAt).getTime() > Date.now();
  }

  private loadApplications() {
    this.api.applications().subscribe({
      next: applications => this.applications = applications ?? [],
      error: () => this.reviewMessage = 'Could not load role applications.'
    });
  }

  review(application: RoleApplication, approve: boolean) {
    const reason = approve ? undefined : window.prompt('Please provide a reason for rejecting this application:')?.trim();
    if (!approve && !reason) return;
    this.processingId = application.id;
    this.reviewMessage = '';
    this.api.reviewApplication(application.id, approve, reason).subscribe({
      next: () => {
        this.reviewMessage = approve ? 'Application approved. The applicant must sign in again to receive the new role.' : 'Application rejected.';
        this.loadApplications();
        if (approve && application.requestedRole === 'RESTAURANT_OWNER') {
          this.loadAdminRestaurants();
        }
        this.processingId = null;
      },
      error: error => {
        this.processingId = null;
        this.reviewMessage = error.status === 409 ? 'This application has already been reviewed.' : 'Could not review the application. Confirm the services are running and retry.';
      }
    });
  }

  private loadAdminRestaurants() {
    this.api.adminRestaurants().subscribe({
      next: restaurants => this.restaurants = restaurants ?? [],
      error: () => this.reviewMessage = 'Could not load restaurant profiles. Check Catalog Service.'
    });
  }

  loadAdminOrders() {
    this.loadingOrders = true;
    this.api.managementOrders(0, 50).subscribe({
      next: page => {
        this.adminOrders = page.content ?? [];
        this.loadingOrders = false;
      },
      error: () => {
        this.loadingOrders = false;
        this.reviewMessage = 'Could not load orders. Check Order Service.';
      }
    });
  }

  loadAdminUsers(page = this.userPage) {
    this.loadingUsers = true;
    this.api.adminUsers(page).subscribe({
      next: page => {
        this.adminUsers = page.content ?? [];
        this.userPage = page.number;
        this.totalUsers = page.totalElements;
        this.hasMoreUsers = !page.last;
        this.loadingUsers = false;
      },
      error: () => {
        this.loadingUsers = false;
        this.reviewMessage = 'Could not load registered users. Check Auth Service.';
      }
    });
  }

  refund(order: any) {
    if (!window.confirm(`Refund ${order.totalAmount} INR and cancel this order?`)) return;
    this.processingId = order.id;
    this.reviewMessage = '';
    this.api.refundOrder(order.id).subscribe({
      next: result => {
        this.reviewMessage = `Refund ${result.status.toLowerCase()}${result.refundId ? ` (provider ref ${result.refundId})` : ''}.`;
        this.processingId = null;
        this.loadAdminOrders();
      },
      error: error => {
        this.processingId = null;
        this.reviewMessage = error.error?.message || `Could not refund this order (HTTP ${error.status || 'unknown'}).`;
      }
    });
  }

  refreshRefund(order: any) {
    this.processingId = order.id;
    this.api.refundStatus(order.id).subscribe({
      next: result => {
        this.reviewMessage = `Refund status: ${result.status}.`;
        this.processingId = null;
        this.loadAdminOrders();
      },
      error: error => {
        this.processingId = null;
        this.reviewMessage = error.error?.message || `Could not check refund status (HTTP ${error.status || 'unknown'}).`;
      }
    });
  }

  changeRestaurantStatus(
    restaurant: Restaurant,
    status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'
  ) {
    this.api.setRestaurantStatus(restaurant.id, status).subscribe({
      next: updated => {
        this.restaurants = this.restaurants.map(item => item.id === updated.id ? updated : item);
      },
      error: () => this.reviewMessage = 'Could not change restaurant status. Retry.'
    });
  }
}
