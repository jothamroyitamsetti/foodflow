import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_CONFIG, generateSafeUUID } from './config';

export interface MenuItem {
  id: string;
  restaurantId?: string;
  restaurantName: string;
  name: string;
  description: string;
  price: number;
  availableQuantity: number;
  available: boolean;
  category?: string;
  emoji?: string;
}

export interface RefundResult {
  orderId: string;
  refundId: string | null;
  amount: number;
  status: string;
  updatedAt: string;
}

export interface Restaurant {
  id: string;
  name: string;
  status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
  acceptingOrders: boolean;
}

export interface RoleApplication {
  id: string;
  userId: string;
  fullName: string;
  email: string;
  requestedRole: string;
  restaurantName?: string;
  status: string;
  rejectionReason?: string;
}

export interface PageResponse<T> {
  content: T[];
  number: number;
  totalPages: number;
  totalElements: number;
  last: boolean;
  first: boolean;
  size: number;
}

export interface PaymentOrder {
  orderId: string;
  razorpayOrderId: string;
  keyId: string;
  amount: number;
  currency: string;
}

export interface PartnerAvailability {
  partnerId: string;
  available: boolean;
}

export interface CustomerAddress {
  id: string;
  label: string;
  address: string;
}

export interface AdminUser {
  id: string;
  fullName: string;
  email: string;
  role: string;
  createdAt: string;
}

export interface Coupon {
  id: string;
  code: string;
  discountPercent: number;
  maxUses: number;
  usedCount: number;
  expiresAt: string;
  active: boolean;
}

export interface CouponCreateRequest {
  code: string;
  discountPercent: number;
  maxUses: number;
  expiresAt: string;
}

export interface ReviewStatus {
  orderId: string;
  reviewed: boolean;
  rating: number | null;
  comment: string | null;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private base = API_CONFIG.baseUrl;

  constructor(private http: HttpClient) {}

  menu(page = 0, size = 8): Observable<PageResponse<MenuItem>> {
    return this.http.get<PageResponse<MenuItem>>(`${this.base}/catalog/menu`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  restaurants(): Observable<Restaurant[]> {
    return this.http.get<Restaurant[]>(`${this.base}/catalog/restaurants`);
  }

  adminRestaurants(): Observable<Restaurant[]> {
    return this.http.get<Restaurant[]>(`${this.base}/catalog/restaurants/admin`);
  }

  restaurantMenu(id: string, page = 0, size = 20): Observable<PageResponse<MenuItem>> {
    return this.http.get<PageResponse<MenuItem>>(`${this.base}/catalog/restaurants/${id}/menu`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  myRestaurant(): Observable<Restaurant> {
    return this.http.get<Restaurant>(`${this.base}/catalog/restaurants/mine`);
  }

  myRestaurantMenu(page = 0, size = 50): Observable<PageResponse<MenuItem>> {
    return this.http.get<PageResponse<MenuItem>>(`${this.base}/catalog/restaurants/mine/menu`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  setRestaurantAvailability(acceptingOrders: boolean): Observable<Restaurant> {
    return this.http.put<Restaurant>(`${this.base}/catalog/restaurants/mine/availability`, null, {
      params: { acceptingOrders }
    });
  }

  setRestaurantStatus(id: string, status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'): Observable<Restaurant> {
    return this.http.put<Restaurant>(`${this.base}/catalog/restaurants/${id}/status`, null, {
      params: { status }
    });
  }

  applications(): Observable<RoleApplication[]> {
    return this.http.get<RoleApplication[]>(`${API_CONFIG.authUrl}/applications`);
  }

  adminUsers(page = 0): Observable<PageResponse<AdminUser>> {
    return this.http.get<PageResponse<AdminUser>>(`${API_CONFIG.authUrl}/admin/users`, {
      params: new HttpParams().set('page', page).set('size', 50)
    });
  }

  myApplications(): Observable<RoleApplication[]> {
    return this.http.get<RoleApplication[]>(`${API_CONFIG.authUrl}/applications/mine`);
  }

  applyForRole(requestedRole: 'RESTAURANT_OWNER' | 'DELIVERY_PARTNER', restaurantName?: string): Observable<RoleApplication> {
    return this.http.post<RoleApplication>(`${API_CONFIG.authUrl}/applications/mine`, {
      requestedRole,
      restaurantName
    });
  }

  ensureOwnerRestaurant(): Observable<void> {
    return this.http.post<void>(`${API_CONFIG.authUrl}/applications/mine/restaurant`, {});
  }

  reviewApplication(id: string, approve: boolean, reason?: string): Observable<RoleApplication> {
    return this.http.post<RoleApplication>(
      `${API_CONFIG.authUrl}/applications/${id}/${approve ? 'approve' : 'reject'}`,
      approve ? {} : { reason }
    );
  }

  orders(): Observable<PageResponse<any>> {
    return this.http.get<PageResponse<any>>(`${this.base}/orders/mine`);
  }

  reviewStatus(orderId: string): Observable<ReviewStatus> {
    return this.http.get<ReviewStatus>(`${this.base}/reviews/orders/${orderId}/mine`);
  }

  submitReview(review: {
    orderId: string;
    restaurantId: string;
    rating: number;
    comment: string;
  }): Observable<any> {
    return this.http.post<any>(`${this.base}/reviews`, review);
  }

  validateCoupon(code: string): Observable<Coupon> {
    return this.http.get<Coupon>(`${this.base}/coupons/${encodeURIComponent(code)}/validate`);
  }

  createCoupon(request: CouponCreateRequest): Observable<Coupon> {
    return this.http.post<Coupon>(`${this.base}/coupons`, request);
  }

  coupons(): Observable<Coupon[]> {
    return this.http.get<Coupon[]>(`${this.base}/coupons`);
  }

  addresses(): Observable<CustomerAddress[]> {
    return this.http.get<CustomerAddress[]>(`${this.base}/orders/addresses`);
  }

  saveAddress(label: string, address: string): Observable<CustomerAddress> {
    return this.http.post<CustomerAddress>(`${this.base}/orders/addresses`, { label, address });
  }

  deleteAddress(addressId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/orders/addresses/${addressId}`);
  }

  createOrder(
    items: { menuItemId: string; quantity: number }[],
    deliveryAddress: string,
    couponCode?: string,
    paymentMethod = 'RAZORPAY'
  ): Observable<any> {
    return this.http.post<any>(
      `${this.base}/orders`,
      { items, deliveryAddress, couponCode, paymentMethod },
      { headers: { 'Idempotency-Key': generateSafeUUID() } }
    );
  }

  createPaymentOrder(orderId: string): Observable<PaymentOrder> {
    return this.http.post<PaymentOrder>(`${this.base}/orders/${orderId}/payment`, {});
  }

  cancelOrder(orderId: string): Observable<any> {
    return this.http.post<any>(`${this.base}/orders/${orderId}/cancel`, {});
  }

  refundOrder(orderId: string): Observable<RefundResult> {
    return this.http.post<RefundResult>(`${this.base}/orders/${orderId}/refund`, {});
  }

  refundStatus(orderId: string): Observable<RefundResult> {
    return this.http.get<RefundResult>(`${this.base}/orders/${orderId}/refund`);
  }

  verifyPayment(
    orderId: string,
    payment: { razorpayOrderId: string; razorpayPaymentId: string; razorpaySignature: string }
  ): Observable<void> {
    return this.http.post<void>(`${this.base}/orders/${orderId}/payment/verify`, payment);
  }

  createMenuItem(item: {
    restaurantName: string;
    name: string;
    description: string;
    price: number;
    availableQuantity: number;
    restaurantId?: string;
  }): Observable<any> {
    return this.http.post<any>(`${this.base}/catalog/menu`, item);
  }

  updateMenuItem(id: string, item: {
    restaurantName: string;
    name: string;
    description: string;
    price: number;
    availableQuantity: number;
  }): Observable<MenuItem> {
    return this.http.put<MenuItem>(`${this.base}/catalog/menu/${id}`, item);
  }

  deleteMenuItem(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/catalog/menu/${id}`);
  }

  managementOrders(page = 0, size = 10): Observable<PageResponse<any>> {
    return this.http.get<PageResponse<any>>(`${this.base}/orders/management`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  updateOrder(id: string, status: string): Observable<any> {
    return this.http.patch<any>(
      `${this.base}/orders/${id}/status`,
      null,
      { params: { value: status } }
    );
  }

  rejectOrder(id: string): Observable<any> {
    return this.http.post<any>(`${this.base}/orders/${id}/reject`, {});
  }

  deliveries(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/deliveries/mine`);
  }

  partnerAvailability(): Observable<PartnerAvailability> {
    return this.http.get<PartnerAvailability>(`${this.base}/deliveries/availability`);
  }

  setPartnerAvailability(available: boolean): Observable<PartnerAvailability> {
    return this.http.put<PartnerAvailability>(`${this.base}/deliveries/availability`, null, {
      params: { available }
    });
  }

  claimDelivery(id: string): Observable<any> {
    return this.http.post<any>(`${this.base}/deliveries/${id}/claim`, {});
  }

  updateDelivery(id: string, status: string, cashCollected = false): Observable<any> {
    return this.http.patch<any>(
      `${this.base}/deliveries/${id}/status`,
      null,
      { params: { status, cashCollected } }
    );
  }
}
