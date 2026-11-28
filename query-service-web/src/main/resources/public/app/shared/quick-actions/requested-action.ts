import { DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

/**
 * Runs an action that the quick actions bar asked a page for with a query parameter (e.g. "/settings?apiKey=new"
 * starts a new API key): now if the URL has it, and on every later navigation that has it. The parameter is removed
 * from the URL first, so going back or reloading doesn't run the action again. Call in an injection context.
 */
export function onRequestedAction(param: Record<string, string>, run: () => void): void {
  const router = inject(Router);
  const [[key, value]] = Object.entries(param);
  // The URL a request was taken from: the creating navigation is seen twice (NavigationEnd and the timeout below).
  let handled: string | null = null;
  const check = () => {
    const tree = router.parseUrl(router.url);
    if (tree.queryParams[key] !== value) {
      handled = null;
      return;
    }
    if (router.url === handled) {
      return;
    }
    handled = router.url;
    delete tree.queryParams[key];
    router.navigateByUrl(tree, { replaceUrl: true });
    run();
  };
  router.events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed(inject(DestroyRef))).subscribe(check);
  // After the current navigation, if the page is being created by it.
  setTimeout(check);
}
