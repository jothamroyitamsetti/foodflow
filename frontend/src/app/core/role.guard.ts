import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';

export const roleGuard: CanActivateFn = (route) => {
  const router = inject(Router);
  const token = sessionStorage.getItem('foodflow_token');
  const role = sessionStorage.getItem('foodflow_role');
  const allowed = route.data['roles'] as string[] | undefined;

  if (!token) {
    return router.createUrlTree(['/login'], {
      queryParams: { needAuth: 'true', returnUrl: route.url.join('/') || undefined }
    });
  }
  if (allowed?.length && !allowed.includes(role ?? '')) {
    return router.createUrlTree(['/'], {
      queryParams: { unauthorizedRole: 'true' }
    });
  }
  return true;
};
