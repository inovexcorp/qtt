import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withHashLocation } from '@angular/router';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideMarkdown } from 'ngx-markdown';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { AuthService } from './core/services/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    // zone.js change detection, as the app has always used (Angular 21+ defaults to zoneless).
    provideZoneChangeDetection(),
    provideRouter(routes, withHashLocation()),
    provideHttpClient(withXhr(), withInterceptors([authInterceptor])),
    // ngx-charts still animates with @angular/animations (deprecated, due to be removed in Angular 23); the app's
    // own animations use animate.enter/animate.leave.
    provideAnimationsAsync(),
    provideMarkdown(),
    // Who is signed in, before the first page renders (so role-dependent controls don't flicker).
    provideAppInitializer(() => inject(AuthService).load())
  ]
};
