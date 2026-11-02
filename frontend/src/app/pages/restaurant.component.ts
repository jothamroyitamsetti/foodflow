import { Component, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, MenuItem, PageResponse, Restaurant } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  selector: 'app-restaurant',
  imports: [CommonModule, CurrencyPipe, DatePipe, FormsModule],
  template: `
    <section *ngIf="isOwner" class="form-card restaurant-menu-card">
      <div class="page-title-row">
        <div>
          <p class="eyebrow">RESTAURANT OWNER PORTAL</p>
          <h1>{{ restaurant?.name || 'Your Restaurant' }} Menu</h1>
          <p class="muted" *ngIf="restaurant">
            {{ restaurant.acceptingOrders ? 'Accepting new orders' : 'Not accepting new orders' }}
            · {{ restaurant.status }}
          </p>
        </div>
        <button class="secondary" *ngIf="restaurant" (click)="toggleAvailability()" [disabled]="updatingAvailability">
          {{ updatingAvailability ? 'Updating…' : (restaurant.acceptingOrders ? 'Pause new orders' : 'Accept new orders') }}
        </button>
      </div>
      <p class="error-banner" *ngIf="menuMessage">{{ menuMessage }}</p>
      <div class="loading-state" *ngIf="loadingMenu">
        <div class="spinner"></div>
        <p>Loading your menu…</p>
      </div>

      <h2>{{ editingItemId ? 'Edit menu item' : 'Add a menu item' }}</h2>
      <div class="menu-editor">
        <label>
          Item name
          <input [(ngModel)]="name" maxlength="120" placeholder="e.g. Paneer Butter Masala">
        </label>
        <label>
          Description
          <input [(ngModel)]="description" maxlength="1000" placeholder="Describe the dish">
        </label>
        <label>
          Price (INR)
          <input [(ngModel)]="price" type="number" min="0.01" step="0.01" placeholder="199.00">
        </label>
        <label>
          Available quantity
          <input [(ngModel)]="quantity" type="number" min="0" step="1" placeholder="10">
        </label>
        <div class="actions">
          <button (click)="saveMenuItem()" [disabled]="savingItem || !restaurant || !name.trim() || price == null || price <= 0 || quantity == null || quantity < 0">
            {{ savingItem ? 'Saving…' : (editingItemId ? 'Save changes' : 'Add item') }}
          </button>
          <button class="secondary" *ngIf="editingItemId" (click)="resetEditor()" [disabled]="savingItem">Cancel edit</button>
        </div>
      </div>

      <div class="page-title-row">
        <h2>Menu items</h2>
        <button class="secondary" (click)="loadMenu(menuPage)" [disabled]="loadingMenu">
          {{ loadingMenu ? 'Loading…' : 'Refresh menu' }}
        </button>
      </div>
      <p class="muted" *ngIf="!menuItems.length && !loadingMenu">No menu items yet. Add your first dish above.</p>
      <article class="order" *ngFor="let item of menuItems">
        <div class="order-header">
          <div>
            <strong>{{ item.name }}</strong>
            <span class="pill" [class.pill-pending]="!item.available">
              {{ item.available ? 'AVAILABLE' : 'UNAVAILABLE' }}
            </span>
          </div>
          <strong>{{ item.price | currency:'INR' }}</strong>
        </div>
        <p class="muted" *ngIf="item.description">{{ item.description }}</p>
        <p class="muted">Stock: {{ item.availableQuantity }}</p>
        <div class="actions">
          <button class="secondary" (click)="editMenuItem(item)" [disabled]="savingItem">Edit item / price / stock</button>
          <button class="danger" (click)="deleteMenuItem(item)" [disabled]="savingItem">Delete item</button>
        </div>
      </article>
      <div class="actions" *ngIf="menuItems.length">
        <button class="secondary" *ngIf="menuPage > 0" (click)="loadMenu(menuPage - 1)" [disabled]="loadingMenu">Previous items</button>
        <button class="secondary" *ngIf="hasMoreMenu" (click)="loadMenu(menuPage + 1)" [disabled]="loadingMenu">Next items</button>
      </div>
    </section>

    <section>
      <div class="page-title-row">
        <div>
          <p class="eyebrow">{{ isOwner ? 'KITCHEN' : 'RESTAURANT PORTAL' }}</p>
          <h1>Incoming Kitchen Orders</h1>
        </div>
        <button class="secondary" (click)="loadOrders()" [disabled]="loadingOrders">
          🔄 {{ loadingOrders ? 'Refreshing...' : 'Refresh Orders' }}
        </button>
      </div>

      <div class="offline-banner" *ngIf="orderMessage">
        <div class="offline-text">
          <span class="offline-icon">ℹ️</span>
          <span>{{ orderMessage }}</span>
        </div>
      </div>

      <div class="loading-state" *ngIf="loadingOrders">
        <div class="spinner"></div>
        <p>Loading kitchen orders...</p>
      </div>

      <div class="empty-state" *ngIf="!orders.length && !loadingOrders">
        <span class="empty-icon">🍳</span>
        <h3>No active orders</h3>
        <p class="muted">No orders are waiting for kitchen action right now.</p>
      </div>

      <article class="order" *ngFor="let o of orders">
        <div class="order-header">
          <div>
            <strong>Order #{{ (o.id || '9001') | slice:0:8 }}…</strong>
            <span class="pill" [class]="'pill-' + (o.status ? o.status.toLowerCase() : 'pending')">
              {{ o.status }}
            </span>
          </div>
          <span class="muted">{{ (o.createdAt || now) | date:'shortTime' }}</span>
        </div>

        <p class="muted">Bill Total: <strong>{{ o.totalAmount | currency:'INR' }}</strong></p>
        <p class="muted">Payment: <strong>{{ o.paymentMethod === 'COD' ? 'Cash on delivery — collect at delivery' : o.paymentStatus }}</strong></p>

        <div *ngIf="o.items?.length" class="order-items">
          <p class="muted item-row" *ngFor="let item of o.items">
            <span>{{ item.menuItemName || item.menuItemId || 'Food Item' }}</span>
            <span class="qty-badge">× {{ item.quantity }}</span>
          </p>
        </div>

        <div class="actions">
          <button (click)="update(o.id, 'ACCEPTED')" [disabled]="o.status !== 'PENDING'">✅ Accept Order</button>
          <button class="secondary" (click)="update(o.id, 'PREPARING')" [disabled]="o.status !== 'ACCEPTED'">🍳 Start Preparing</button>
          <button class="secondary" (click)="update(o.id, 'READY_FOR_PICKUP')" [disabled]="o.status !== 'PREPARING'">📦 Ready for Pickup</button>
          <button class="danger" *ngIf="['PENDING', 'ACCEPTED'].includes(o.status)"
            (click)="reject(o)" [disabled]="processingId === o.id">
            {{ processingId === o.id ? 'Rejecting…' : '✕ Reject order' }}
          </button>
        </div>
      </article>
    </section>
  `
})
export class RestaurantComponent implements OnInit {
  orders: any[] = [];
  menuItems: MenuItem[] = [];
  restaurant: Restaurant | null = null;
  orderMessage = '';
  menuMessage = '';
  loadingOrders = false;
  loadingMenu = false;
  processingId: string | null = null;
  savingItem = false;
  updatingAvailability = false;
  now = new Date();
  menuPage = 0;
  hasMoreMenu = false;
  editingItemId: string | null = null;
  name = '';
  description = '';
  price: number | null = null;
  quantity: number | null = null;
  readonly isOwner: boolean;

  constructor(private api: ApiService, auth: AuthService) {
    this.isOwner = auth.role() === 'RESTAURANT_OWNER';
  }

  ngOnInit() {
    this.loadOrders();
    if (this.isOwner) {
      this.loadRestaurant();
    }
  }

  loadOrders() {
    this.loadingOrders = true;
    this.orderMessage = '';
    this.api.managementOrders().subscribe({
      next: (response: PageResponse<any>) => {
        this.orders = response.content ?? [];
        this.loadingOrders = false;
      },
      error: () => {
        this.loadingOrders = false;
        this.orderMessage = 'Could not load orders. Check the order service and try again.';
      }
    });
  }

  private loadRestaurant() {
    this.api.myRestaurant().subscribe({
      next: restaurant => {
        this.restaurant = restaurant;
        this.loadMenu();
      },
      error: () => this.repairRestaurantLink()
    });
  }

  private repairRestaurantLink() {
    this.menuMessage = 'Restoring your approved restaurant profile…';
    this.api.ensureOwnerRestaurant().subscribe({
      next: () => {
        this.api.myRestaurant().subscribe({
          next: restaurant => {
            this.restaurant = restaurant;
            this.menuMessage = '';
            this.loadMenu();
          },
          error: () => {
            this.menuMessage = 'Your approved restaurant profile could not be loaded. Contact the administrator.';
          }
        });
      },
      error: error => {
        this.menuMessage = error.error?.message
          || `Could not restore your restaurant profile (HTTP ${error.status || 'unknown'}). Contact the administrator.`;
      }
    });
  }

  loadMenu(page = 0) {
    this.loadingMenu = true;
    this.menuMessage = '';
    this.api.myRestaurantMenu(page).subscribe({
      next: response => {
        this.menuItems = response.content ?? [];
        this.menuPage = response.number;
        this.hasMoreMenu = !response.last;
        this.loadingMenu = false;
      },
      error: () => {
        this.loadingMenu = false;
        this.menuMessage = 'Could not load your menu. Check Catalog Service and confirm your owner account is approved.';
      }
    });
  }

  saveMenuItem() {
    if (!this.restaurant || !this.name.trim() || this.price == null || this.price <= 0
        || this.quantity == null || this.quantity < 0) {
      return;
    }
    const item = {
      restaurantName: this.restaurant.name,
      name: this.name.trim(),
      description: this.description.trim(),
      price: this.price,
      availableQuantity: this.quantity
    };
    this.savingItem = true;
    this.menuMessage = '';
    const request = this.editingItemId
      ? this.api.updateMenuItem(this.editingItemId, item)
      : this.api.createMenuItem(item);
    request.subscribe({
      next: () => {
        this.savingItem = false;
        this.resetEditor();
        this.loadMenu(this.menuPage);
      },
      error: error => {
        this.savingItem = false;
        this.menuMessage = error.error?.message
          || `Could not ${this.editingItemId ? 'update' : 'add'} menu item (HTTP ${error.status || 'unknown'}).`;
      }
    });
  }

  editMenuItem(item: MenuItem) {
    this.editingItemId = item.id;
    this.name = item.name;
    this.description = item.description ?? '';
    this.price = item.price;
    this.quantity = item.availableQuantity;
  }

  deleteMenuItem(item: MenuItem) {
    if (!window.confirm(`Delete "${item.name}" from your menu?`)) {
      return;
    }
    this.savingItem = true;
    this.menuMessage = '';
    this.api.deleteMenuItem(item.id).subscribe({
      next: () => {
        this.savingItem = false;
        if (this.editingItemId === item.id) {
          this.resetEditor();
        }
        this.loadMenu(this.menuPage);
      },
      error: error => {
        this.savingItem = false;
        this.menuMessage = error.error?.message
          || `Could not delete menu item (HTTP ${error.status || 'unknown'}).`;
      }
    });
  }

  resetEditor() {
    this.editingItemId = null;
    this.name = '';
    this.description = '';
    this.price = null;
    this.quantity = null;
  }

  toggleAvailability() {
    if (!this.restaurant) {
      return;
    }
    this.updatingAvailability = true;
    this.api.setRestaurantAvailability(!this.restaurant.acceptingOrders).subscribe({
      next: restaurant => {
        this.restaurant = restaurant;
        this.updatingAvailability = false;
      },
      error: error => {
        this.updatingAvailability = false;
        this.menuMessage = error.error?.message || 'Could not update restaurant availability.';
      }
    });
  }

  update(id: string, status: string) {
    this.api.updateOrder(id, status).subscribe({
      next: () => this.loadOrders(),
      error: () => this.orderMessage = 'Could not update the order. Verify its current status and try again.'
    });
  }

  reject(order: any) {
    if (!window.confirm('Reject this order? Reserved stock will be restored, and any captured Razorpay payment will be refunded.')) {
      return;
    }
    this.processingId = order.id;
    this.orderMessage = '';
    this.api.rejectOrder(order.id).subscribe({
      next: () => {
        this.processingId = null;
        this.orderMessage = 'Order rejected. The customer can see the update in Order History.';
        this.loadOrders();
      },
      error: error => {
        this.processingId = null;
        const message = error.error?.message;
        this.orderMessage = typeof message === 'string'
          ? message
          : `Could not reject this order (HTTP ${error.status || 'unknown'}). Refresh and retry.`;
      }
    });
  }
}
