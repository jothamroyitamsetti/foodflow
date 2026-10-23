export interface User {
  id: string;
  email: string;
  fullName: string;
  role: 'CUSTOMER' | 'ADMIN' | 'RESTAURANT_OWNER' | 'DELIVERY_PARTNER';
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface Restaurant {
  id: string;
  name: string;
  address: string;
  ownerId: string;
  active: boolean;
}

export interface FoodItem {
  id: string;
  restaurantId: string;
  name: string;
  description: string;
  price: number;
  available: boolean;
  stockQuantity: number;
}

export interface OrderLine {
  itemId: string;
  itemName: string;
  quantity: number;
  unitPrice: number;
}

export interface FoodOrder {
  id: string;
  customerId: string;
  restaurantId?: string;
  total: number;
  status: string;
  createdAt: string;
  items: OrderLine[];
  statusHistory: { status: string; changedAt: string }[];
}

export interface Delivery {
  id: string;
  orderId: string;
  partnerId: string;
  status: 'ASSIGNED' | 'PICKED_UP' | 'DELIVERED';
  assignedAt: string;
}
