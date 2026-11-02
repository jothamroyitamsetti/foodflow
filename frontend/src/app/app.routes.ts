import { Routes } from '@angular/router';
import { HomeComponent } from './pages/home.component';
import { LoginComponent } from './pages/login.component';
import { RegisterComponent } from './pages/register.component';
import { OrdersComponent } from './pages/orders.component';
import { AdminComponent } from './pages/admin.component';
import { RestaurantComponent } from './pages/restaurant.component';
import { DeliveryComponent } from './pages/delivery.component';
import { roleGuard } from './core/role.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  {
    path: 'orders',
    component: OrdersComponent,
    canActivate: [roleGuard],
    data: { roles: ['CUSTOMER', 'ADMIN'] }
  },
  {
    path: 'admin',
    component: AdminComponent,
    canActivate: [roleGuard],
    data: { roles: ['ADMIN'] }
  },
  {
    path: 'restaurant',
    component: RestaurantComponent,
    canActivate: [roleGuard],
    data: { roles: ['ADMIN', 'RESTAURANT_OWNER'] }
  },
  {
    path: 'delivery',
    component: DeliveryComponent,
    canActivate: [roleGuard],
    data: { roles: ['DELIVERY_PARTNER'] }
  },
  { path: '**', redirectTo: '' }
];
