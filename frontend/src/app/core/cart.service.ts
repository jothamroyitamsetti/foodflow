import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { FoodItem } from './models';

export interface CartEntry {
  item: FoodItem;
  quantity: number;
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly entriesSubject = new BehaviorSubject<CartEntry[]>([]);
  readonly entries$ = this.entriesSubject.asObservable();

  get entries(): CartEntry[] {
    return this.entriesSubject.value;
  }

  get total(): number {
    return this.entries.reduce((sum, line) => sum + line.item.price * line.quantity, 0);
  }

  add(item: FoodItem): void {
    const entries = [...this.entries];
    const existing = entries.find(line => line.item.id === item.id);
    if (existing) existing.quantity++;
    else entries.push({ item, quantity: 1 });
    this.entriesSubject.next(entries);
  }

  remove(itemId: string): void {
    this.entriesSubject.next(this.entries.filter(line => line.item.id !== itemId));
  }

  clear(): void {
    this.entriesSubject.next([]);
  }
}
