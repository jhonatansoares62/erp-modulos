import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Injeta o Bearer JWT nas chamadas /v1 da api-contabil e, em 401, encerra a sessão e volta ao login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const token = auth.token();
  const isApi = req.url.includes('/v1/');
  const isLogin = req.url.includes('/v1/auth/login');

  const authorized = token && isApi && !isLogin
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authorized).pipe(
    catchError((err) => {
      if (err?.status === 401 && !isLogin) {
        auth.logout();
        router.navigate(['/login']);
      }
      return throwError(() => err);
    }),
  );
};
