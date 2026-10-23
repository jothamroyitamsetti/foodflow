import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <section class="auth-card card">
      <h1>{{ registering ? 'Create your account' : 'Welcome back' }}</h1>
      <p class="muted">Order from your neighborhood favorites.</p>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <label *ngIf="registering">Full name
          <input formControlName="fullName" autocomplete="name">
        </label>
        <label>Email
          <input formControlName="email" type="email" autocomplete="email">
        </label>
        <label>Password
          <input formControlName="password" type="password" [attr.autocomplete]="registering ? 'new-password' : 'current-password'">
        </label>
        <p class="error" *ngIf="error">{{ error }}</p>
        <p class="success" *ngIf="success">{{ success }}</p>
        <button class="button" [disabled]="form.invalid || loading">
          {{ loading ? 'Please wait...' : registering ? 'Register' : 'Sign in' }}
        </button>
      </form>
      <p *ngIf="registering">Already registered? <a routerLink="/login">Sign in</a></p>
      <p *ngIf="!registering">New to FoodFlow? <a routerLink="/register">Create an account</a></p>
    </section>
  `
})
export class LoginComponent {
  readonly registering: boolean;
  loading = false;
  error = '';
  success = '';
  form = this.fb.group({
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]]
  });

  constructor(
    private readonly fb: FormBuilder,
    private readonly auth: AuthService,
    private readonly router: Router,
    route: ActivatedRoute
  ) {
    this.registering = route.snapshot.data['register'] === true;
    this.success = route.snapshot.queryParamMap.has('registered') ? 'Account created. Sign in to continue.' : '';
    if (!this.registering) this.form.controls.fullName.disable();
  }

  submit(): void {
    if (this.form.invalid) return;
    const { fullName, email, password } = this.form.getRawValue();
    this.loading = true;
    this.error = '';
    if (this.registering) {
      this.auth.register(fullName ?? '', email ?? '', password ?? '').subscribe({
        next: () => {
          this.loading = false;
          void this.router.navigate(['/login'], { queryParams: { registered: true } });
        },
        error: error => this.fail(error)
      });
    } else {
      this.auth.login(email ?? '', password ?? '').subscribe({
        next: () => {
          this.loading = false;
          void this.router.navigate(['/']);
        },
        error: error => this.fail(error)
      });
    }
  }

  private fail(error: { error?: { message?: string } }): void {
    this.loading = false;
    this.error = error.error?.message ?? 'Unable to complete the request. Please try again.';
  }
}
