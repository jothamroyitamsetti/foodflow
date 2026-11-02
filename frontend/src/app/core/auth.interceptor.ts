import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = sessionStorage.getItem('foodflow_token');
  const isAuthEndpoint = req.url.includes('/api/auth/login') || req.url.includes('/api/auth/register');

  if (token && !isAuthEndpoint) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }
  return next(req);
};
