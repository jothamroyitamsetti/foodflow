import { TestBed } from '@angular/core/testing';
import { CartService } from './cart.service';
import { FoodItem } from './models';

describe('CartService', () => {
  let cart: CartService;
  const item: FoodItem = {
    id: 'item-1',
    restaurantId: 'restaurant-1',
    name: 'Test meal',
    description: '',
    price: 4.5,
    available: true,
    stockQuantity: 5
  };

  beforeEach(() => {
    TestBed.configureTestingModule({});
    cart = TestBed.inject(CartService);
  });

  it('increments the quantity and computes totals from backend prices', () => {
    cart.add(item);
    cart.add(item);
    expect(cart.entries).toEqual([{ item, quantity: 2 }]);
    expect(cart.total).toBe(9);
  });

  it('removes a line and clears the cart', () => {
    cart.add(item);
    cart.remove(item.id);
    expect(cart.entries).toEqual([]);
    cart.add(item);
    cart.clear();
    expect(cart.total).toBe(0);
  });
});
