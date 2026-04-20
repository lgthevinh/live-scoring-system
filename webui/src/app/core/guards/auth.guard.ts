import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, take } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

/**
 * Require a logged-in user. Unauthenticated callers are redirected to
 * {@code /auth}. Role is not inspected — use {@link roleGuard} for that.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated().pipe(
    take(1),
    map(isAuth => isAuth ? true : router.createUrlTree(['/auth'])),
  );
};

/**
 * Factory: require the current user's role number to be <= {@code minRole}
 * (lower number = higher privilege, mirroring the backend AccountRole enum).
 *
 * - Unauthenticated → redirect to /auth.
 * - Authenticated but under-privileged → redirect to / (home).
 *
 * There is intentionally no dedicated "forbidden" page: in this app the
 * home screen is already role-sensitive (the UI hides sections the user
 * cannot use), so bouncing back there is the least surprising outcome.
 */
export const roleGuard = (minRole: number): CanActivateFn => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.role$().pipe(
    take(1),
    map(role => {
      if (role === null) return router.createUrlTree(['/auth']);
      if (role > minRole) return router.createUrlTree(['/']);
      return true;
    }),
  );
};
