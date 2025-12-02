# QTT Authentication System - Implementation Guide

## Overview

This guide explains how to use, configure, and extend the authentication system implemented for the QTT (Query Templating Tool) platform.

## Current Implementation Status

### ✅ Completed Components

1. **Backend Infrastructure**
   - JPA entities (User, Role, UserSession, SecurityEvent)
   - Service layer with full CRUD operations
   - Just-in-time (JIT) user provisioning
   - Session management
   - Security event auditing
   - JAX-RS authentication filter
   - REST API endpoints for authentication

2. **Frontend Infrastructure**
   - Angular auth service
   - Auth guard for route protection
   - Login component UI
   - HTTP interceptor support

3. **Configuration**
   - OSGi configuration files
   - Karaf feature definitions
   - Default role initialization (ADMIN, USER, VIEWER)

### 🚧 Pending Implementation

1. **PAC4J Integration** (OAuth, OIDC, SAML)
   - OAuth 2.0 client configuration
   - OIDC client with token validation
   - SAML Service Provider setup
   - Callback handlers for each protocol

2. **Advanced Features**
   - Role mapping from IdP groups/claims
   - JWT token generation for API access
   - MFA support
   - API key management

---

## Quick Start

### 1. Enable Authentication

Edit `query-service-distribution/src/main/resources/etc/com.inovexcorp.query.auth.cfg`:

```properties
# Enable authentication
auth.enabled=true

# Session timeout (1 hour)
auth.session.timeout=3600

# Auto-provision users from SSO
auth.auto.provision=true

# Default role for new users
auth.default.role=USER
```

### 2. Start QTT

```bash
cd query-service-distribution/target/assembly
bin/karaf
```

### 3. Verify Installation

Check that the auth bundle is active:

```bash
karaf@root> bundle:list | grep auth
```

You should see: `query-service-auth`

### 4. Test Development Login

Navigate to: `http://localhost:8080/login`

Enter any email address (e.g., `admin@yourcompany.com`) and click "Dev Login".

**⚠️ IMPORTANT**: The dev login endpoint MUST be disabled in production!

---

## Architecture

### Database Schema

The authentication system creates the following tables:

- `qtt_users` - User accounts
- `qtt_roles` - Roles (ADMIN, USER, VIEWER, custom)
- `qtt_user_roles` - User-role assignments
- `qtt_role_permissions` - Role permissions
- `qtt_user_sessions` - Active user sessions
- `qtt_security_events` - Audit log

Tables are auto-created on first startup via Hibernate's `hbm2ddl.auto=update`.

### Default Roles

| Role | Description | Permissions |
|------|-------------|-------------|
| ADMIN | Full system access | routes:*, datasources:*, users:*, roles:*, metrics:*, settings:*, sparqi:* |
| USER | Standard user | routes:read/write, datasources:read/write, metrics:read, settings:read, sparqi:use |
| VIEWER | Read-only access | routes:read, datasources:read, metrics:read, settings:read |

### API Endpoints

**Authentication**
- `GET /api/auth/session` - Get current user session
- `GET /api/auth/providers` - List enabled SSO providers
- `GET /api/auth/login/oauth2/{provider}` - Initiate OAuth flow
- `GET /api/auth/callback/{provider}` - OAuth callback handler
- `POST /api/auth/logout` - Logout
- `POST /api/auth/dev/login` - Development login (REMOVE IN PRODUCTION)

**User Management** (Admin only)
- `GET /api/users/me` - Get current user profile
- `GET /api/users` - List all users
- `POST /api/users` - Create user
- `PUT /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Delete user
- `POST /api/users/{id}/roles` - Assign roles

---

## Configuration Guide

### Session Configuration

```properties
# Session timeout in seconds (default: 3600 = 1 hour)
auth.session.timeout=3600

# Session cleanup interval (default: 3600 = 1 hour)
auth.session.cleanup.interval=3600
```

### Failed Login Protection

```properties
# Maximum failed login attempts before lockout
auth.login.max.attempts=5

# Lockout duration in seconds (default: 900 = 15 minutes)
auth.login.lockout.duration=900
```

### Security Event Retention

```properties
# Days to retain security events (default: 90 days)
auth.events.retention.days=90
```

---

## Adding SSO Providers

### Google OAuth 2.0

1. Create OAuth client at: https://console.cloud.google.com/apis/credentials
2. Set redirect URI: `https://your-domain.com/queryrest/api/auth/callback/google`
3. Create config file: `etc/com.inovexcorp.query.auth.oauth2.google.cfg`

```properties
oauth2.google.enabled=true
oauth2.google.clientId=your-client-id.apps.googleusercontent.com
oauth2.google.clientSecret=GOCSPX-your-secret
oauth2.google.scopes=openid,email,profile
oauth2.google.callbackUrl=https://your-domain.com/queryrest/api/auth/callback/google

# Role mapping (email pattern -> role)
oauth2.google.roleMapping=.*@yourcompany.com:ADMIN,.*@contractor.com:USER
```

### Azure AD (OIDC)

1. Register app in Azure Portal
2. Create config file: `etc/com.inovexcorp.query.auth.oidc.azure.cfg`

```properties
oidc.azure.enabled=true
oidc.azure.clientId=your-azure-app-id
oidc.azure.clientSecret=your-secret
oidc.azure.tenantId=your-tenant-id
oidc.azure.discoveryUri=https://login.microsoftonline.com/{tenant}/v2.0/.well-known/openid-configuration
oidc.azure.scopes=openid,email,profile
oidc.azure.callbackUrl=https://your-domain.com/queryrest/api/auth/callback/azure

# Group-based role mapping
oidc.azure.groupMapping=QTT-Admins:ADMIN,QTT-Users:USER
```

### Okta SAML

1. Create SAML app in Okta Admin Console
2. Upload SP metadata or configure manually:
   - ACS URL: `https://your-domain.com/queryrest/api/auth/callback/saml-okta`
   - Entity ID: `https://your-domain.com/queryrest`
3. Create config file: `etc/com.inovexcorp.query.auth.saml.okta.cfg`

```properties
saml.okta.enabled=true
saml.okta.entityId=https://your-domain.com/queryrest
saml.okta.metadataUrl=https://your-org.okta.com/app/exk.../sso/saml/metadata
saml.okta.callbackUrl=https://your-domain.com/queryrest/api/auth/callback/saml-okta
saml.okta.signatureAlgorithm=RSA_SHA256

# Attribute mapping
saml.okta.attributeMapping=email:email,firstName:given_name,lastName:family_name

# Role mapping from SAML groups attribute
saml.okta.roleAttribute=groups
saml.okta.roleMapping=QTT Admins:ADMIN,QTT Users:USER
```

---

## Angular Integration

### Protecting Routes

Edit `app.routes.ts`:

```typescript
import { AuthGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    canActivate: [AuthGuard],
    children: [
      { path: 'routes', component: RoutesComponent },
      { path: 'datasources', component: DatasourcesComponent },
      // Admin-only route
      {
        path: 'users',
        component: UsersComponent,
        data: { roles: ['ADMIN'] }
      }
    ]
  }
];
```

### Using Auth Service in Components

```typescript
import { AuthService } from './core/services/auth.service';

export class MyComponent {
  constructor(private authService: AuthService) {}

  ngOnInit() {
    // Check if authenticated
    if (this.authService.isAuthenticated()) {
      console.log('User:', this.authService.currentUser);
    }

    // Check roles
    if (this.authService.hasRole('ADMIN')) {
      // Show admin features
    }

    // Subscribe to user changes
    this.authService.currentUser$.subscribe(user => {
      if (user) {
        console.log('User logged in:', user.displayName);
      }
    });
  }

  logout() {
    this.authService.logout().subscribe(() => {
      // Redirect to login
    });
  }
}
```

### Conditional UI Elements

```html
<!-- Show only if authenticated -->
<div *ngIf="authService.isAuthenticated()">
  Welcome, {{ authService.currentUser?.displayName }}!
</div>

<!-- Show only for admins -->
<button *ngIf="authService.hasRole('ADMIN')"
        (click)="deleteItem()">
  Delete
</button>

<!-- Show based on permission -->
<a *ngIf="authService.hasPermission('users:write')"
   routerLink="/users">
  Manage Users
</a>
```

---

## Security Best Practices

### 1. Disable Dev Login in Production

Remove or comment out the dev login endpoint:

```java
// In AuthController.java - REMOVE this method in production
// @POST
// @Path("/dev/login")
// public Response devLogin(...) { ... }
```

### 2. Use HTTPS

Ensure your Karaf instance is behind an HTTPS reverse proxy (Nginx, Apache, etc.):

```nginx
server {
    listen 443 ssl;
    server_name qtt.yourcompany.com;

    ssl_certificate /etc/ssl/certs/qtt.crt;
    ssl_certificate_key /etc/ssl/private/qtt.key;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 3. Secure Session Cookies

Update `org.ops4j.pax.web.cfg`:

```properties
org.ops4j.pax.web.session.cookie.httpOnly=true
org.ops4j.pax.web.session.cookie.secure=true
org.ops4j.pax.web.session.cookie.sameSite=Lax
```

### 4. Rotate Secrets

Regularly rotate OAuth/OIDC client secrets and update configurations.

### 5. Monitor Security Events

Regularly review security events:

```bash
# In Karaf console
karaf@root> jdbc:query jdbc/qtt "SELECT * FROM qtt_security_events WHERE event_type = 'LOGIN_FAILURE' ORDER BY timestamp DESC LIMIT 100"
```

### 6. Set Strong Session Timeouts

Balance security and usability:

```properties
# 30 minutes for high-security environments
auth.session.timeout=1800

# 8 hours for internal tools
auth.session.timeout=28800
```

---

## Troubleshooting

### Authentication Filter Not Working

**Problem**: Users can access protected endpoints without logging in.

**Solution**: Verify `auth.enabled` is set to `true`:

```bash
karaf@root> config:property-get com.inovexcorp.query.auth auth.enabled
```

### User Not Provisioned on First Login

**Check JIT provisioning**:
```bash
karaf@root> config:property-get com.inovexcorp.query.auth auth.auto.provision
```

**Check logs**:
```bash
karaf@root> log:tail
```

Look for: `"Provisioned new user from..."`

### Session Expired Too Quickly

Increase session timeout:

```bash
karaf@root> config:property-set com.inovexcorp.query.auth auth.session.timeout 7200
```

### Can't See Login Page

**Verify bundle status**:
```bash
karaf@root> bundle:list | grep auth
```

**Check for errors**:
```bash
karaf@root> bundle:diag query-service-auth
```

---

## Database Queries

### List All Users

```sql
SELECT u.id, u.username, u.email, u.display_name, u.identity_provider, u.enabled
FROM qtt_users u
ORDER BY u.created_at DESC;
```

### Find User Roles

```sql
SELECT u.username, r.name as role
FROM qtt_users u
JOIN qtt_user_roles ur ON u.id = ur.user_id
JOIN qtt_roles r ON r.id = ur.role_id
WHERE u.username = 'user@example.com';
```

### View Security Events

```sql
SELECT event_type, username, ip_address, timestamp, success, error_message
FROM qtt_security_events
ORDER BY timestamp DESC
LIMIT 100;
```

### Active Sessions Count

```sql
SELECT COUNT(*) as active_sessions
FROM qtt_user_sessions
WHERE active = true AND expires_at > NOW();
```

---

## Next Steps

### Phase 2: Complete SSO Integration

1. Add PAC4J dependencies to `query-service-auth/pom.xml`
2. Implement `OAuthClientProvider`, `OidcClientProvider`, `SamlClientProvider`
3. Update `AuthController` callback handlers
4. Test with real OAuth/OIDC/SAML providers

### Phase 3: Advanced Features

1. Implement JWT token generation for API-only clients
2. Add role mapping from IdP claims/groups
3. Implement MFA support (TOTP, SMS)
4. Create admin UI for user management
5. Add API key management for programmatic access

### Phase 4: Production Hardening

1. Add rate limiting
2. Implement CSRF protection
3. Add security headers (HSTS, CSP, X-Frame-Options)
4. Set up monitoring and alerting
5. Create runbook for security incidents

---

## Support

For questions or issues:

1. Check the logs: `karaf@root> log:tail`
2. Review security events in the database
3. Consult the design document: `SSO_AUTHENTICATION_DESIGN.md`
4. Open an issue on the project repository

---

## Appendix: Permission System

### Permission Naming Convention

Permissions follow the pattern: `resource:action`

**Resources**:
- `routes` - SPARQL route templates
- `datasources` - RDF datasources
- `users` - User accounts
- `roles` - Roles and permissions
- `metrics` - System metrics
- `settings` - System configuration
- `sparqi` - AI assistant

**Actions**:
- `read` - View resource
- `write` - Create/update resource
- `delete` - Remove resource
- `admin` - Full control (all actions)

**Wildcards**:
- `routes:*` - All route actions
- `*:read` - Read all resources
- `*:*` - All permissions (superuser)

### Example Permission Checks

```java
// In your service/controller
if (user.hasPermission("routes:delete")) {
    // Allow route deletion
}

if (user.hasAnyPermission("users:admin", "users:write")) {
    // Allow user modification
}
```

---

**Document Version**: 1.0
**Last Updated**: 2025-12-02
**Author**: Claude (Anthropic AI)
