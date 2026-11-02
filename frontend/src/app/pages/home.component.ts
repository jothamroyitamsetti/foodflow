import { Component, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { ApiService, CustomerAddress, MenuItem, PageResponse, Restaurant, RoleApplication } from '../core/api.service';
import { AuthService } from '../core/auth.service';

interface RazorpayCheckoutResult {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

interface RazorpayCheckoutOptions {
  key: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  order_id: string;
  handler: (result: RazorpayCheckoutResult) => void;
  modal: { ondismiss: () => void };
}

interface RazorpayCheckoutInstance {
  open(): void;
  on(event: 'payment.failed', handler: () => void): void;
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayCheckoutOptions) => RazorpayCheckoutInstance;
  }
}

@Component({
  standalone: true,
  selector: 'app-home',
  imports: [CommonModule, CurrencyPipe, FormsModule, RouterLink],
  template: `
    <!-- Registration Welcome Banner -->
    <div class="welcome-registered-banner" *ngIf="showRegistrationWelcome">
      <div class="welcome-text">
        <span class="welcome-icon">🎉</span>
        <div>
          <strong>Welcome to FoodFlow, {{ auth.userName() }}!</strong>
          <p>Your account is ready. Browse the menu below to place an order.</p>
        </div>
      </div>
      <button class="mini-btn primary" (click)="dismissWelcome()">Got it!</button>
    </div>

    <div class="offline-banner" *ngIf="applicationError">{{ applicationError }}</div>

    <div class="offline-banner" *ngFor="let application of myApplications">
      <div class="offline-text">
        <strong>{{ application.requestedRole }} application: {{ application.status }}</strong>
        <span *ngIf="application.status === 'PENDING'"> — an administrator is reviewing your application.</span>
        <span *ngIf="application.rejectionReason"> — Reason: {{ application.rejectionReason }}</span>
        <span *ngIf="application.status === 'APPROVED' && application.requestedRole !== auth.role()">
          — sign out and sign in again to refresh your permissions.
        </span>
        <ng-container *ngIf="application.status === 'APPROVED' && application.requestedRole === auth.role()">
          <span> — your access is active.</span>
          <a *ngIf="application.requestedRole === 'RESTAURANT_OWNER'" routerLink="/restaurant">Open Restaurant Portal</a>
          <a *ngIf="application.requestedRole === 'DELIVERY_PARTNER'" routerLink="/delivery">Open Delivery Dashboard</a>
        </ng-container>
      </div>
    </div>

    <section class="offline-banner" *ngIf="isCustomer()">
      <div class="offline-text">
        <strong>Apply for a FoodFlow role</strong>
        <span>Use your existing account. An administrator must approve your application before role access is granted.</span>
      </div>
      <ng-container *ngIf="!hasPendingApplication('RESTAURANT_OWNER') || !hasPendingApplication('DELIVERY_PARTNER')">
        <label>
          Role
          <select [(ngModel)]="roleApplicationRole">
            <option value="RESTAURANT_OWNER" *ngIf="!hasPendingApplication('RESTAURANT_OWNER')">Restaurant owner</option>
            <option value="DELIVERY_PARTNER" *ngIf="!hasPendingApplication('DELIVERY_PARTNER')">Delivery partner</option>
          </select>
        </label>
        <label *ngIf="roleApplicationRole === 'RESTAURANT_OWNER'">
          Restaurant name
          <input [(ngModel)]="roleApplicationRestaurantName" maxlength="100" placeholder="Your restaurant name">
        </label>
        <button
          class="secondary"
          (click)="submitRoleApplication()"
          [disabled]="submittingRoleApplication || hasPendingApplication(roleApplicationRole) || (roleApplicationRole === 'RESTAURANT_OWNER' && !roleApplicationRestaurantName.trim())">
          {{ submittingRoleApplication ? 'Submitting…' : 'Submit application' }}
        </button>
      </ng-container>
      <p *ngIf="hasPendingApplication('RESTAURANT_OWNER') && hasPendingApplication('DELIVERY_PARTNER')">
        You already have pending applications for both roles.
      </p>
      <p *ngIf="roleApplicationMessage" [class]="roleApplicationMessageIsError ? 'error-banner' : 'success-banner'">
        {{ roleApplicationMessage }}
      </p>
    </section>

    <!-- Unauthorized Role Banner -->
    <div class="offline-banner" *ngIf="showUnauthorizedRole">
      <div class="offline-text">
        <span class="offline-icon">🛡️</span>
        <div>
          <strong>Access Restricted:</strong>
          <span>Your current account role does not have permission for that administrative portal.</span>
        </div>
      </div>
      <button class="secondary mini" (click)="showUnauthorizedRole = false">✕ Dismiss</button>
    </div>

    <!-- ── Hero Section ── -->
    <section class="hero" *ngIf="!auth.role()">
      <div class="hero-content">
        <p class="eyebrow">FOODFLOW FULL-STACK ECOSYSTEM</p>
        <h1>Fresh Dishes. Instant Flow. Zero Hassle.</h1>
        <p class="hero-desc">
          FoodFlow is a distributed, full-stack food ordering platform connecting hungry food lovers with artisan kitchens and lightning-fast delivery partners.
        </p>

        <!-- Dynamic Action Buttons based on Auth State -->
        <div class="hero-actions" *ngIf="!auth.role()">
          <a routerLink="/register" class="hero-btn primary-hero-btn">
            ✨ Get Started — Create Free Account
          </a>
          <button class="hero-btn secondary-hero-btn" (click)="scrollToMenu()">
            🍽️ Explore Live Menu Below
          </button>
          <a routerLink="/login" class="hero-btn ghost-hero-btn">
            🔑 Existing User? Sign In
          </a>
        </div>

        <div class="hero-actions logged-in" *ngIf="auth.role()">
          <div class="user-greeting">
            <span>👋 Signed in as <strong>{{ auth.userName() }}</strong> ({{ auth.role() }})</span>
          </div>
          <button class="hero-btn primary-hero-btn" (click)="scrollToMenu()">
            🛒 Browse & Order Now
          </button>
          <a routerLink="/orders" class="hero-btn secondary-hero-btn" *ngIf="isCustomer()">
            📦 View My Orders
          </a>
          <a routerLink="/restaurant" class="hero-btn secondary-hero-btn" *ngIf="isRestaurant()">
            👨‍🍳 Kitchen Portal
          </a>
          <a routerLink="/delivery" class="hero-btn secondary-hero-btn" *ngIf="isDelivery()">
            🛵 Delivery Portal
          </a>
          <a routerLink="/admin" class="hero-btn secondary-hero-btn" *ngIf="isAdmin()">
            ⚙️ Admin Console
          </a>
        </div>
      </div>
    </section>

    <!-- ── What FoodFlow Does (4 Pillars) ── -->
    <section class="what-we-do-section" *ngIf="!auth.role()">
      <div class="section-heading">
        <p class="eyebrow">WHAT FOODFLOW DOES</p>
        <h2>A Complete Food Ordering & Logistics Platform</h2>
        <p class="muted">Built on Spring Cloud microservices, JWT security, and coordinated order and delivery workflows.</p>
      </div>

      <div class="features-grid">
        <div class="feature-card">
          <div class="feature-icon">🍛</div>
          <h3>Curated Artisan Kitchens</h3>
          <p>Discover fresh gourmet dishes prepared by vetted local chefs with transparent, real-time inventory counts.</p>
        </div>

        <div class="feature-card">
          <div class="feature-icon">🛡️</div>
          <h3>Zero-Trust JWT Security</h3>
          <p>Double-layer authentication with Spring Cloud Gateway and microservice filters ensuring role-protected endpoints.</p>
        </div>

        <div class="feature-card">
          <div class="feature-icon">🎟️</div>
          <h3>Smart Discount Coupons</h3>
          <p>Apply valid restaurant promotions at checkout when available.</p>
        </div>

        <div class="feature-card">
          <div class="feature-icon">⚡</div>
          <h3>Real-Time Event Logistics</h3>
          <p>Direct service coordination moves ready orders into the delivery partner queue.</p>
        </div>
      </div>
    </section>

    <!-- ── How It Works (3 Steps) ── -->
    <section class="how-it-works-section" *ngIf="!auth.role()">
      <div class="section-heading">
        <p class="eyebrow">SIMPLE 3-STEP JOURNEY</p>
        <h2>How FoodFlow Works</h2>
      </div>

      <div class="steps-container">
        <div class="step-card">
          <div class="step-badge">1</div>
          <h3>Browse & Discover</h3>
          <p>Explore our live chef menu below, check ingredients, portion sizes, and live kitchen availability.</p>
        </div>

        <div class="step-card highlighted">
          <div class="step-badge">2</div>
          <h3>Register in Seconds</h3>
          <p>Create your free account to unlock your personal cart, apply discount coupons, and initiate checkout.</p>
          <a routerLink="/register" class="step-action-link" *ngIf="!auth.role()">
            Create Account Now →
          </a>
        </div>

        <div class="step-card">
          <div class="step-badge">3</div>
          <h3>Track in Real-Time</h3>
          <p>Watch as your order gets confirmed by the kitchen, picked up by a rider, and delivered to your doorstep.</p>
        </div>
      </div>
    </section>

    <!-- ── Live Menu Section ── -->
    <section id="menu-section" class="menu-section">
      <div class="toolbar">
        <div>
          <p class="eyebrow">TODAY'S SPECIALS</p>
          <h2>{{ selectedRestaurant ? selectedRestaurant.name + ' Menu' : 'Choose a Restaurant' }}</h2>
          <span class="muted" *ngIf="items.length">{{ items.length }} items available for ordering</span>
        </div>

        <div class="page-controls" *ngIf="selectedRestaurant">
          <button class="secondary" (click)="backToRestaurants()" [disabled]="loading">All restaurants</button>
          <button class="secondary" (click)="load(page - 1)" [disabled]="page === 0 || loading">
            ← Previous
          </button>
          <span class="page-info">Page {{ page + 1 }}</span>
          <button (click)="load(page + 1)" [disabled]="isLastPage || items.length === 0 || loading">
            Next →
          </button>
        </div>
      </div>

      <div class="grid" *ngIf="!selectedRestaurant && restaurants.length">
        <article class="food restaurant-card" *ngFor="let restaurant of restaurants">
          <div class="food-icon">🍽️</div>
          <h3>{{ restaurant.name }}</h3>
          <button (click)="openRestaurant(restaurant)">View menu</button>
        </article>
      </div>

      <!-- Guest Notice Banner above menu -->
      <div class="guest-menu-notice" *ngIf="!auth.role()">
        <span class="guest-notice-icon">💡</span>
        <div>
          <strong>Public Menu:</strong> You are browsing as a guest.
          <a routerLink="/register" class="link-bold">Register free</a> or <a routerLink="/login" class="link-bold">sign in</a> to add dishes to your cart and place orders.
        </div>
      </div>

      <!-- Loading State -->
      <div class="loading-state" *ngIf="loading">
        <div class="spinner"></div>
        <p>Fetching fresh dishes from FoodFlow...</p>
      </div>

      <div class="offline-banner" *ngIf="error && !loading">
        <div class="offline-text">
          <span class="offline-icon">⚠️</span>
          <div>
            <strong>Backend API Offline:</strong>
            <span>{{ error }}</span>
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <div class="empty-state" *ngIf="!loading && !error && !restaurants.length && !selectedRestaurant">
        <span class="empty-icon">🍽️</span>
        <h3>No restaurants available</h3>
        <p class="muted">This database currently has no active restaurants accepting orders. A restaurant owner can apply below; an administrator must approve the application before it appears here.</p>
        <a routerLink="/register" [queryParams]="{ applicationRole: 'RESTAURANT_OWNER' }" class="hero-btn primary-hero-btn">
          Apply as a restaurant owner
        </a>
      </div>
      <div class="empty-state" *ngIf="!loading && !error && selectedRestaurant && items.length === 0">
        <span class="empty-icon">🍽️</span>
        <h3>No menu items available</h3>
        <p class="muted">This restaurant has not published any available dishes yet.</p>
      </div>

      <!-- Menu Cards Grid -->
      <div class="grid" *ngIf="!loading && selectedRestaurant && items.length > 0">
        <article class="food" *ngFor="let item of items">
          <div class="food-icon">{{ item.emoji || '🍽️' }}</div>
          <p class="restaurant-tag">{{ item.restaurantName }}</p>
          <h3>{{ item.name }}</h3>
          <p class="item-desc">{{ item.description }}</p>

          <div class="food-bottom">
            <strong class="item-price">{{ item.price | currency:'INR' }}</strong>
            <span class="stock-badge" [class.out-of-stock]="item.availableQuantity < 1">
              {{ item.availableQuantity < 1 ? 'Out of stock' : item.availableQuantity + ' left' }}
            </span>
          </div>

          <button
            (click)="handleAddToCart(item)"
            [disabled]="item.availableQuantity < 1 || !item.available">
            {{ item.availableQuantity < 1 ? 'Unavailable' : 'Add to cart 🛒' }}
          </button>
        </article>
      </div>
    </section>

    <!-- ── Guest Prompt Modal (Triggered when guest clicks Add to Cart) ── -->
    <div class="modal-backdrop" *ngIf="showAuthPromptModal" (click)="showAuthPromptModal = false">
      <div class="modal-card" (click)="$event.stopPropagation()">
        <span class="modal-icon">🌟</span>
        <h2>Join FoodFlow to Order</h2>
        <p class="modal-desc">
          You must have a FoodFlow account to add items to your cart and place orders.
        </p>

        <div class="modal-actions">
          <a routerLink="/register" class="modal-btn primary-btn" (click)="showAuthPromptModal = false">
            🚀 Create Free Account in 10s
          </a>
          <a routerLink="/login" class="modal-btn secondary-btn" (click)="showAuthPromptModal = false">
            🔑 Sign In to Existing Account
          </a>
          <button class="modal-btn ghost-btn" (click)="showAuthPromptModal = false">
            Continue Browsing Menu
          </button>
        </div>
      </div>
    </div>

    <!-- ── Cart Section (Only active when logged in or has items) ── -->
    <section class="cart" *ngIf="auth.role() || cart.length > 0">
      <div class="cart-header">
        <div>
          <h2>Your Cart</h2>
          <p class="muted">{{ cartTotalCount }} item(s) in cart</p>
        </div>
        <button class="link danger-link" *ngIf="cart.length > 0" (click)="clearCart()">Clear Cart</button>
      </div>

      <div class="coupon-row" *ngIf="isCustomer()">
        <label>
          Coupon code
          <input [(ngModel)]="couponCode" (ngModelChange)="couponInputChanged()"
            [disabled]="!!pendingOrderId" maxlength="30" placeholder="Enter coupon code">
        </label>
        <button type="button" class="secondary" (click)="applyCoupon()"
          [disabled]="applyingCoupon || !couponCode.trim() || !cart.length || !!pendingOrderId">
          {{ applyingCoupon ? 'Checking…' : 'Apply coupon' }}
        </button>
        <button type="button" class="link" *ngIf="appliedCouponCode && !pendingOrderId" (click)="removeCoupon()">
          Remove
        </button>
      </div>
      <p class="muted" *ngIf="couponMessage" [class.error-banner]="couponMessageIsError">
        {{ couponMessage }}
      </p>

      <label class="coupon-row" *ngIf="isCustomer()">
        Payment method
        <select [(ngModel)]="paymentMethod" [disabled]="!!pendingOrderId">
          <option value="RAZORPAY">Pay online (Razorpay)</option>
          <option value="COD">Cash on delivery</option>
        </select>
      </label>

      <label class="coupon-row" *ngIf="isCustomer() && cart.length">
        Delivery address
        <textarea [(ngModel)]="deliveryAddress" maxlength="500" rows="3" required
          placeholder="Enter your complete delivery address"></textarea>
      </label>
      <div class="coupon-row" *ngIf="isCustomer() && cart.length">
        <label>
          Save this address as
          <input [(ngModel)]="addressLabel" maxlength="80" placeholder="Home, Work, etc.">
        </label>
        <button type="button" class="secondary" (click)="saveCustomerAddress()"
          [disabled]="savingAddress || !addressLabel.trim() || !deliveryAddress.trim()">
          {{ savingAddress ? 'Saving…' : 'Save address' }}
        </button>
      </div>
      <div class="coupon-row" *ngIf="isCustomer() && savedAddresses.length">
        <label>
          Saved addresses
          <select [(ngModel)]="selectedAddressId" (ngModelChange)="selectSavedAddress($event)">
            <option value="">Choose a saved address or enter one above</option>
            <option *ngFor="let address of savedAddresses" [value]="address.id">
              {{ address.label }} — {{ address.address }}
            </option>
          </select>
        </label>
        <button type="button" class="danger" *ngIf="selectedAddressId" (click)="deleteSavedAddress()">
          Remove selected saved address
        </button>
      </div>
      <p class="muted" *ngIf="addressMessage">{{ addressMessage }}</p>

      <div *ngIf="cart.length === 0" class="empty-cart-msg">
        <p class="muted">Your cart is currently empty. Browse the live menu above and add something delicious!</p>
      </div>

      <div *ngFor="let line of cart" class="cart-line">
        <div class="cart-line-info">
          <span class="cart-line-name">{{ line.item.name }}</span>
          <span class="cart-line-unit-price">{{ line.item.price | currency:'INR' }} each</span>
        </div>

        <div class="cart-qty-controls">
          <button class="qty-btn" (click)="decrease(line.item.id)" title="Decrease quantity">−</button>
          <span class="qty-num">{{ line.quantity }}</span>
          <button class="qty-btn" (click)="increase(line.item)" [disabled]="line.quantity >= line.item.availableQuantity" title="Increase quantity">+</button>
        </div>

        <span class="cart-line-price">{{ (line.item.price * line.quantity) | currency:'INR' }}</span>
        <button class="link delete-btn" (click)="remove(line.item.id)" title="Remove item">✕</button>
      </div>

      <div class="cart-total" *ngIf="cart.length > 0">
        <div class="total-row">
          <span>Subtotal:</span>
          <strong>{{ cartTotal | currency:'INR' }}</strong>
        </div>
        <div class="total-row" *ngIf="appliedCouponCode">
          <span>Coupon {{ appliedCouponCode }} ({{ couponDiscountPercent }}%):</span>
          <strong>−{{ couponDiscountAmount | currency:'INR' }}</strong>
        </div>
        <div class="total-row" *ngIf="appliedCouponCode">
          <span>Estimated total:</span>
          <strong>{{ estimatedTotal | currency:'INR' }}</strong>
        </div>
      </div>

      <!-- Login reminder before checkout if not logged in -->
      <div class="auth-notice" *ngIf="cart.length > 0 && !auth.role()">
        <span>🔒 Create an account or sign in to complete checkout:</span>
        <a routerLink="/register" class="mini-btn primary">Create Free Account</a>
        <span class="or-divider">or</span>
        <a routerLink="/login" class="link-btn">Sign In</a>
      </div>

      <!-- Role notice if logged in as non-customer -->
      <div class="offline-banner" *ngIf="cart.length > 0 && auth.role() && !isCustomer()">
        <div class="offline-text">
          <span>ℹ️ You are signed in as <strong>{{ auth.role() }}</strong>. Only customer accounts can place orders.</span>
        </div>
      </div>

      <button
        class="checkout-btn"
        (click)="checkout()"
        [disabled]="cart.length === 0 || placingOrder || (!isCustomer() && !!auth.role()) || (isCustomer() && !deliveryAddress.trim()) || (!!couponCode.trim() && !appliedCouponCode)">
        {{ placingOrder ? 'Processing order…' : (paymentMethod === 'COD' ? 'Place COD order (' : 'Pay securely (') + ((appliedCouponCode ? estimatedTotal : cartTotal) | currency:'INR') + ')' }}
      </button>

      <p *ngIf="message" [class]="messageIsError ? 'error-banner' : 'success-banner'">
        {{ message }}
      </p>
      <a *ngIf="sessionExpired" routerLink="/login" class="link-btn">Sign in again</a>
    </section>
  `
})
export class HomeComponent implements OnInit {
  items: MenuItem[] = [];
  restaurants: Restaurant[] = [];
  myApplications: RoleApplication[] = [];
  roleApplicationRole: 'RESTAURANT_OWNER' | 'DELIVERY_PARTNER' = 'RESTAURANT_OWNER';
  roleApplicationRestaurantName = '';
  roleApplicationMessage = '';
  roleApplicationMessageIsError = false;
  submittingRoleApplication = false;
  savedAddresses: CustomerAddress[] = [];
  applicationError = '';
  addressLabel = '';
  selectedAddressId = '';
  addressMessage = '';
  savingAddress = false;
  selectedRestaurant: Restaurant | null = null;
  paymentMethod: 'RAZORPAY' | 'COD' = 'RAZORPAY';
  deliveryAddress = '';
  page = 0;
  isLastPage = false;
  loading = false;
  error = '';
  message = '';
  messageIsError = false;
  sessionExpired = false;
  couponCode = '';
  appliedCouponCode = '';
  couponDiscountPercent = 0;
  couponMessage = '';
  couponMessageIsError = false;
  applyingCoupon = false;
  cart: { item: MenuItem; quantity: number }[] = [];
  placingOrder = false;
  pendingOrderId: string | null = null;

  showRegistrationWelcome = false;
  showUnauthorizedRole = false;
  showAuthPromptModal = false;

  constructor(
    private api: ApiService,
    public auth: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit() {
    this.showRegistrationWelcome = this.route.snapshot.queryParams['registered'] === 'true';
    this.showUnauthorizedRole = this.route.snapshot.queryParams['unauthorizedRole'] === 'true';

    if (this.auth.role() && this.auth.role() !== 'ADMIN') {
      this.api.myApplications().subscribe({
        next: applications => this.myApplications = applications ?? [],
        error: () => this.applicationError = 'Could not load your role application status.'
      });
    }
    if (this.isCustomer()) {
      this.loadSavedAddresses();
    }
    this.loadRestaurants();
  }

  private loadSavedAddresses() {
    this.api.addresses().subscribe({
      next: addresses => this.savedAddresses = addresses ?? [],
      error: () => this.addressMessage = 'Could not load saved delivery addresses.'
    });
  }

  hasPendingApplication(role: string): boolean {
    return this.myApplications.some(application =>
      application.requestedRole === role && application.status === 'PENDING');
  }

  submitRoleApplication() {
    if (this.submittingRoleApplication
        || this.hasPendingApplication(this.roleApplicationRole)
        || (this.roleApplicationRole === 'RESTAURANT_OWNER' && !this.roleApplicationRestaurantName.trim())) {
      return;
    }

    this.submittingRoleApplication = true;
    this.roleApplicationMessage = '';
    this.api.applyForRole(
      this.roleApplicationRole,
      this.roleApplicationRole === 'RESTAURANT_OWNER' ? this.roleApplicationRestaurantName.trim() : undefined
    ).subscribe({
      next: application => {
        this.myApplications = [application, ...this.myApplications];
        this.roleApplicationMessage = 'Application submitted. You can use customer features while an administrator reviews it.';
        this.roleApplicationMessageIsError = false;
        this.roleApplicationRestaurantName = '';
        this.submittingRoleApplication = false;
      },
      error: (error: HttpErrorResponse) => {
        const body = error.error as { message?: unknown } | null;
        this.roleApplicationMessage = typeof body?.message === 'string'
          ? body.message
          : `Could not submit application (HTTP ${error.status || 'unknown'}).`;
        this.roleApplicationMessageIsError = true;
        this.submittingRoleApplication = false;
      }
    });
  }

  saveCustomerAddress() {
    const label = this.addressLabel.trim();
    const address = this.deliveryAddress.trim();
    if (!label || !address || this.savingAddress) return;
    this.savingAddress = true;
    this.addressMessage = '';
    this.api.saveAddress(label, address).subscribe({
      next: saved => {
        this.savedAddresses = [saved, ...this.savedAddresses];
        this.selectedAddressId = saved.id;
        this.addressLabel = '';
        this.addressMessage = 'Delivery address saved to your account.';
        this.savingAddress = false;
      },
      error: () => {
        this.addressMessage = 'Could not save this address. Check the Order Service and retry.';
        this.savingAddress = false;
      }
    });
  }

  selectSavedAddress(addressId: string) {
    this.selectedAddressId = addressId;
    const selected = this.savedAddresses.find(address => address.id === addressId);
    if (selected) this.deliveryAddress = selected.address;
  }

  deleteSavedAddress() {
    const addressId = this.selectedAddressId;
    if (!addressId || !window.confirm('Remove this saved address from your account?')) return;
    this.api.deleteAddress(addressId).subscribe({
      next: () => {
        this.savedAddresses = this.savedAddresses.filter(address => address.id !== addressId);
        this.selectedAddressId = '';
        this.addressMessage = 'Saved address removed.';
      },
      error: () => this.addressMessage = 'Could not remove this saved address. Retry.'
    });
  }

  dismissWelcome() {
    this.showRegistrationWelcome = false;
  }

  isCustomer(): boolean {
    return this.auth.role() === 'CUSTOMER';
  }

  isAdmin(): boolean {
    return this.auth.role() === 'ADMIN';
  }

  isRestaurant(): boolean {
    return ['ADMIN', 'RESTAURANT_OWNER'].includes(this.auth.role() ?? '');
  }

  isDelivery(): boolean {
    return this.auth.role() === 'DELIVERY_PARTNER';
  }

  scrollToMenu() {
    const el = document.getElementById('menu-section');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  }

  get cartTotal(): number {
    return this.cart.reduce((sum, line) => sum + line.item.price * line.quantity, 0);
  }

  get cartTotalCount(): number {
    return this.cart.reduce((sum, line) => sum + line.quantity, 0);
  }

  get couponDiscountAmount(): number {
    return Math.round(this.cartTotal * this.couponDiscountPercent) / 100;
  }

  get estimatedTotal(): number {
    return Math.max(0, this.cartTotal - this.couponDiscountAmount);
  }

  load(p: number) {
    if (p < 0 || !this.selectedRestaurant) return;
    this.loading = true;
    this.error = '';

    this.api.restaurantMenu(this.selectedRestaurant.id, p, 20).subscribe({
      next: (r: PageResponse<MenuItem>) => {
        this.items = r.content ?? [];
        this.page = r.number ?? p;
        this.isLastPage = r.last;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.error = 'Could not connect to backend gateway at http://localhost:8080.';
      }
    });
  }

  loadRestaurants() {
    this.loading = true;
    this.error = '';
    this.api.restaurants().subscribe({
      next: restaurants => {
        this.restaurants = restaurants ?? [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.error = 'Could not load approved restaurants from the FoodFlow catalog.';
      }
    });
  }

  openRestaurant(restaurant: Restaurant) {
    this.selectedRestaurant = restaurant;
    this.items = [];
    this.page = 0;
    this.load(0);
  }

  backToRestaurants() {
    this.selectedRestaurant = null;
    this.items = [];
    this.page = 0;
  }

  handleAddToCart(item: MenuItem) {
    // If user is not logged in, prompt them to register/sign in
    if (!this.auth.role()) {
      this.showAuthPromptModal = true;
      return;
    }
    this.add(item);
  }

  add(item: MenuItem) {
    const existingRestaurant = this.cart[0]?.item.restaurantId ?? this.cart[0]?.item.restaurantName;
    const requestedRestaurant = item.restaurantId ?? item.restaurantName;
    if (this.cart.length && existingRestaurant !== requestedRestaurant) {
      this.message = 'Your cart can contain dishes from one restaurant at a time. Complete or clear this cart before choosing another restaurant.';
      this.messageIsError = true;
      return;
    }
    const line = this.cart.find(x => x.item.id === item.id);
    if (line) {
      if (line.quantity < item.availableQuantity) {
        line.quantity++;
      }
    } else {
      this.cart.push({ item, quantity: 1 });
    }
    this.message = `Added "${item.name}" to cart.`;
    this.messageIsError = false;
  }

  increase(item: MenuItem) {
    const line = this.cart.find(x => x.item.id === item.id);
    if (line && line.quantity < item.availableQuantity) {
      line.quantity++;
    }
  }

  decrease(id: string) {
    const line = this.cart.find(x => x.item.id === id);
    if (line) {
      if (line.quantity > 1) {
        line.quantity--;
      } else {
        this.remove(id);
      }
    }
  }

  remove(id: string) {
    this.cart = this.cart.filter(x => x.item.id !== id);
  }

  clearCart() {
    this.cart = [];
    this.message = '';
    this.removeCoupon();
  }

  couponInputChanged() {
    if (this.appliedCouponCode && this.couponCode.trim().toUpperCase() !== this.appliedCouponCode) {
      this.appliedCouponCode = '';
      this.couponDiscountPercent = 0;
      this.couponMessage = 'Coupon removed because the code changed. Apply it again to continue.';
      this.couponMessageIsError = false;
    }
  }

  applyCoupon() {
    const code = this.couponCode.trim().toUpperCase();
    if (!code || !this.cart.length || this.applyingCoupon) return;
    this.applyingCoupon = true;
    this.couponMessage = '';
    this.api.validateCoupon(code).subscribe({
      next: coupon => {
        this.appliedCouponCode = coupon.code;
        this.couponCode = coupon.code;
        this.couponDiscountPercent = coupon.discountPercent;
        this.couponMessage = `${coupon.code} applied: ${coupon.discountPercent}% off. The final amount is confirmed when the order is created.`;
        this.couponMessageIsError = false;
        this.applyingCoupon = false;
      },
      error: error => {
        const detail = error.error?.message;
        this.couponMessage = typeof detail === 'string'
          ? detail
          : `Could not validate this coupon (HTTP ${error.status || 'unknown'}). Check the code and retry.`;
        this.couponMessageIsError = true;
        this.appliedCouponCode = '';
        this.couponDiscountPercent = 0;
        this.applyingCoupon = false;
      }
    });
  }

  removeCoupon() {
    this.couponCode = '';
    this.appliedCouponCode = '';
    this.couponDiscountPercent = 0;
    this.couponMessage = '';
    this.couponMessageIsError = false;
  }

  checkout() {
    if (this.cart.length === 0 || this.placingOrder) return;

    if (!this.auth.role()) {
      this.showAuthPromptModal = true;
      return;
    }

    this.placingOrder = true;
    this.message = '';

    if (this.pendingOrderId) {
      this.beginPayment(this.pendingOrderId);
      return;
    }
    const items = this.cart.map(line => ({ menuItemId: line.item.id, quantity: line.quantity }));
    this.api.createOrder(items, this.deliveryAddress.trim(), this.appliedCouponCode || undefined, this.paymentMethod).subscribe({
      next: order => {
        if (this.paymentMethod === 'COD') {
          this.cart = [];
          this.removeCoupon();
          this.pendingOrderId = null;
          this.placingOrder = false;
          this.message = `Cash-on-delivery order ${order.id.slice(0, 8)} placed. Pay the delivery partner when your order arrives.`;
          this.messageIsError = false;
          return;
        }
        this.pendingOrderId = order.id;
        this.beginPayment(order.id);
      },
      error: error => this.showOrderError(error, 'order')
    });
  }

  private beginPayment(orderId: string) {
    this.api.createPaymentOrder(orderId).subscribe({
      next: payment => {
        if (!window.Razorpay) {
          this.placingOrder = false;
          this.message = 'Razorpay Checkout did not load. Check your connection and retry payment.';
          this.messageIsError = true;
          return;
        }
        const checkout = new window.Razorpay({
          key: payment.keyId,
          amount: Math.round(payment.amount * 100),
          currency: payment.currency,
          name: 'FoodFlow',
          description: `Order ${payment.orderId}`,
          order_id: payment.razorpayOrderId,
          handler: result => this.confirmPayment(orderId, result),
          modal: {
            ondismiss: () => {
              this.placingOrder = false;
              this.message = 'Payment was not completed. Your order is still pending payment; retry securely to continue.';
              this.messageIsError = true;
            }
          }
        });
        checkout.on('payment.failed', () => {
          this.placingOrder = false;
          this.message = 'Razorpay could not complete the payment. Your order is pending; please retry.';
          this.messageIsError = true;
        });
        checkout.open();
      },
      error: error => this.showOrderError(error, 'payment')
    });
  }

  private confirmPayment(
    orderId: string,
    result: { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }
  ) {
    this.api.verifyPayment(orderId, {
      razorpayOrderId: result.razorpay_order_id,
      razorpayPaymentId: result.razorpay_payment_id,
      razorpaySignature: result.razorpay_signature
    }).subscribe({
      next: () => {
        this.cart = [];
        this.removeCoupon();
        this.pendingOrderId = null;
        this.placingOrder = false;
        this.message = 'Payment confirmed. Your order is now with the restaurant.';
        this.messageIsError = false;
      },
      error: error => this.showOrderError(error, 'confirmation')
    });
  }

  private showOrderError(error: unknown, stage: 'order' | 'payment' | 'confirmation') {
    this.placingOrder = false;
    this.sessionExpired = error instanceof HttpErrorResponse && error.status === 401;
    if (this.sessionExpired) {
      this.message = stage === 'order'
        ? 'Your sign-in expired or is no longer valid. Sign in again, then retry; the order was not created.'
        : stage === 'payment'
          ? 'Your sign-in expired or is no longer valid. Sign in again, then retry payment from Orders; the pending order may already exist.'
          : 'Your sign-in expired or is no longer valid. Sign in again and check your order status before retrying payment.';
      this.messageIsError = true;
      return;
    }
    if (error instanceof HttpErrorResponse) {
      const serverMessage = this.readServerMessage(error.error);
      const action = stage === 'order'
        ? 'create the order'
        : stage === 'payment'
          ? 'start Razorpay checkout'
          : 'confirm the payment';
      this.message = serverMessage || (error.status === 0
        ? `Could not reach FoodFlow to ${action}. Check that the gateway and services are running, then retry.`
        : `Could not ${action} (HTTP ${error.status}). Check the service response and retry.`);
    } else {
      this.message = stage === 'confirmation'
        ? 'Could not confirm the payment. Your order remains pending payment; retry payment from the same order.'
        : stage === 'payment'
          ? 'Could not start payment for this order. Retry payment from the same order.'
          : 'Could not create the order. Check the cart and retry.';
    }
    this.messageIsError = true;
  }

  private readServerMessage(body: unknown): string | null {
    if (typeof body === 'string') {
      const trimmed = body.trim();
      if (trimmed.startsWith('{')) {
        try {
          return this.readServerMessage(JSON.parse(trimmed));
        } catch {
          return null;
        }
      }
      return trimmed && !trimmed.startsWith('<') && trimmed.length <= 400 ? trimmed : null;
    }
    if (!body || typeof body !== 'object') return null;

    const response = body as Record<string, unknown>;
    for (const key of ['message', 'detail']) {
      const value = response[key];
      if (typeof value === 'string' && value.trim()) return value.trim();
    }
    return this.readServerMessage(response['error']);
  }
}
