import { provideZoneChangeDetection } from '@angular/core';

// Providers for every TestBed (angular.json: test.options.providersFile). The app runs with zone.js change detection
// (main.ts), while TestBed defaults to zoneless since Angular 21, so the tests get the same change detection as the app.
export default [provideZoneChangeDetection()];
