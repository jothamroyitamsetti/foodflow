import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { LoginComponent } from './pages/login.component';
import { HomeComponent } from './pages/home.component';
import { OrdersComponent } from './pages/orders.component';
import { OperationsComponent } from './pages/operations.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: LoginComponent, data: { register: true } },
  { path: '', component: HomeComponent, canActivate: [authGuard] },
  { path: 'orders', component: OrdersComponent, canActivate: [authGuard] },
  { path: 'manage', component: OperationsComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' }
];
