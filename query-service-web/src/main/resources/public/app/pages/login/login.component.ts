import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService, AuthProvider } from '../../core/services/auth.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {
  providers$: Observable<AuthProvider[]>;
  returnUrl: string = '/';
  devEmail: string = '';
  devLoginEnabled = true; // TODO: Read from config

  constructor(
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.providers$ = this.authService.getAuthProviders();
  }

  ngOnInit(): void {
    // Get return URL from route parameters or default to '/'
    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/';

    // If already authenticated, redirect to return URL
    if (this.authService.isAuthenticated()) {
      this.router.navigateByUrl(this.returnUrl);
    }
  }

  loginWithProvider(providerId: string): void {
    this.authService.loginWithProvider(providerId);
  }

  devLogin(): void {
    if (!this.devEmail) {
      return;
    }

    this.authService.devLogin(this.devEmail).subscribe({
      next: () => {
        this.router.navigateByUrl(this.returnUrl);
      },
      error: (error) => {
        console.error('Login failed:', error);
        alert('Login failed: ' + (error.error?.error || 'Unknown error'));
      }
    });
  }
}
