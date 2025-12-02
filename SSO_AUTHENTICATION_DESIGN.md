# SSO Authentication System Design for QTT Platform

## Executive Summary

This document outlines the design and implementation of a comprehensive Single Sign-On (SSO) authentication system for the QTT (Query Templating Tool) platform. The system will protect the Angular 18 UI and JAX-RS REST API while leaving Camel routes (port 8888) unprotected.

## Architecture Overview

### Technology Stack

**Security Framework:** PAC4J 5.7.x
- OSGi-compatible security library
- Supports OAuth 2.0, OIDC, and SAML out-of-the-box
- Mature, well-documented, actively maintained
- Works seamlessly with JAX-RS

**Session Management:** HTTP Sessions + JWT (optional)
- Stateful sessions for UI access
- Optional JWT for API-only clients
- Redis-backed session storage (leveraging existing Redis infrastructure)

**User Persistence:** JPA/Hibernate (existing infrastructure)
- New User, Role, and UserSession entities
- Just-in-time user provisioning
- Audit logging for security events

---

## System Components

### 1. Core Authentication Module (`query-service-auth`)

New Maven module with the following structure:

```
query-service-auth/
├── src/main/java/com/inovexcorp/query/auth/
│   ├── model/
│   │   ├── User.java                    # JPA entity
│   │   ├── Role.java                    # JPA entity
│   │   ├── UserSession.java             # JPA entity
│   │   └── SecurityEvent.java           # Audit log entity
│   ├── service/
│   │   ├── AuthenticationService.java   # Core auth logic
│   │   ├── UserProvisioningService.java # JIT user creation
│   │   ├── SessionManagementService.java
│   │   └── RoleManagementService.java
│   ├── filter/
│   │   ├── JaxRsAuthenticationFilter.java  # JAX-RS ContainerRequestFilter
│   │   └── SecurityContextFilter.java      # Injects SecurityContext
│   ├── provider/
│   │   ├── OAuthClientProvider.java     # OAuth 2.0 configuration
│   │   ├── OidcClientProvider.java      # OIDC configuration
│   │   └── SamlClientProvider.java      # SAML configuration
│   ├── controller/
│   │   ├── AuthController.java          # Login/logout endpoints
│   │   └── UserController.java          # User profile management
│   ├── config/
│   │   ├── SecurityConfiguration.java   # OSGi configuration
│   │   └── Pac4jConfiguration.java      # PAC4J setup
│   └── util/
│       ├── JwtTokenUtil.java            # JWT generation/validation
│       └── PasswordUtil.java            # Password hashing (fallback)
├── src/main/resources/
│   └── META-INF/persistence.xml         # JPA entities registration
└── pom.xml
```

---

### 2. Authentication Flows

#### 2.1 OAuth 2.0 Flow (Google, GitHub, GitLab, etc.)

```
┌─────────┐                ┌──────────┐               ┌─────────────┐
│ Angular │                │ QTT API  │               │ OAuth IDP   │
│   UI    │                │ (Karaf)  │               │ (Google)    │
└────┬────┘                └────┬─────┘               └──────┬──────┘
     │                          │                             │
     │ 1. GET /auth/login/oauth2│                             │
     ├─────────────────────────>│                             │
     │                          │ 2. Generate state & redirect│
     │                          ├────────────────────────────>│
     │                          │                             │
     │ 3. User authenticates    │                             │
     │<─────────────────────────┼─────────────────────────────┤
     │                          │                             │
     │ 4. Callback with code    │                             │
     ├─────────────────────────>│ 5. Exchange code for token  │
     │                          ├────────────────────────────>│
     │                          │<────────────────────────────┤
     │                          │ 6. Get user profile         │
     │                          ├────────────────────────────>│
     │                          │<────────────────────────────┤
     │                          │ 7. JIT provision user       │
     │                          │ 8. Create session           │
     │ 9. Redirect to UI        │                             │
     │<─────────────────────────┤                             │
     │                          │                             │
```

#### 2.2 OIDC Flow (Azure AD, Okta, Keycloak, Auth0, etc.)

Similar to OAuth 2.0 but with ID token validation:

```
- Uses OIDC Discovery (/.well-known/openid-configuration)
- Validates ID Token signature (JWT)
- Extracts claims (email, name, groups, roles)
- Maps claims to QTT roles
- JIT provisions user
```

#### 2.3 SAML 2.0 Flow (Enterprise SSO)

```
┌─────────┐                ┌──────────┐               ┌─────────────┐
│ Angular │                │ QTT API  │               │ SAML IDP    │
│   UI    │                │   (SP)   │               │ (Okta/AD)   │
└────┬────┘                └────┬─────┘               └──────┬──────┘
     │                          │                             │
     │ 1. Access protected page │                             │
     ├─────────────────────────>│                             │
     │                          │ 2. Generate SAML AuthnReq   │
     │                          ├────────────────────────────>│
     │                          │                             │
     │ 3. User authenticates    │                             │
     │<─────────────────────────┼─────────────────────────────┤
     │                          │                             │
     │ 4. SAML Response (POST)  │                             │
     ├─────────────────────────>│ 5. Validate signature       │
     │                          │ 6. Extract attributes       │
     │                          │ 7. JIT provision user       │
     │                          │ 8. Create session           │
     │ 9. Redirect to app       │                             │
     │<─────────────────────────┤                             │
```

---

### 3. User Model

#### User Entity (JPA)

```java
@Entity
@Table(name = "qtt_users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    private String displayName;

    // External identity provider reference
    private String identityProvider; // "google", "azure-ad", "saml-okta"
    private String externalId;       // Provider-specific user ID

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "qtt_user_roles")
    private Set<Role> roles;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    @Column(nullable = false)
    private boolean enabled = true;

    // Audit fields
    private String createdBy;
    private LocalDateTime modifiedAt;
    private String modifiedBy;
}
```

#### Role Entity

```java
@Entity
@Table(name = "qtt_roles")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name; // "ADMIN", "USER", "VIEWER"

    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "qtt_role_permissions")
    private Set<String> permissions; // "routes:read", "routes:write", etc.
}
```

#### UserSession Entity

```java
@Entity
@Table(name = "qtt_user_sessions")
public class UserSession {
    @Id
    private String sessionId;

    @ManyToOne
    private User user;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastAccessedAt;

    private String ipAddress;
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String jwtToken; // Optional JWT for API access
}
```

---

### 4. REST API Endpoints

#### Authentication Endpoints

```
POST   /api/auth/login                  # Form-based login (optional fallback)
GET    /api/auth/login/oauth2/{provider}  # Initiate OAuth2 flow
GET    /api/auth/login/oidc/{provider}    # Initiate OIDC flow
GET    /api/auth/login/saml/{provider}    # Initiate SAML flow
POST   /api/auth/callback/{provider}      # OAuth/OIDC/SAML callback
POST   /api/auth/logout                   # Logout and invalidate session
GET    /api/auth/session                  # Get current user session info
GET    /api/auth/providers                # List enabled auth providers
```

#### User Management Endpoints

```
GET    /api/users/me                    # Get current user profile
PUT    /api/users/me                    # Update current user profile
GET    /api/users                       # List users (admin only)
GET    /api/users/{id}                  # Get user by ID (admin only)
PUT    /api/users/{id}                  # Update user (admin only)
DELETE /api/users/{id}                  # Deactivate user (admin only)
POST   /api/users/{id}/roles            # Assign roles (admin only)
```

---

### 5. Angular UI Integration

#### Auth Service

```typescript
@Injectable({ providedIn: 'root' })
export class AuthService {
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient, private router: Router) {
    this.loadCurrentUser();
  }

  loginWithProvider(provider: 'google' | 'azure' | 'saml-okta'): void {
    window.location.href = `/queryrest/api/auth/login/oauth2/${provider}`;
  }

  logout(): Observable<void> {
    return this.http.post<void>('/queryrest/api/auth/logout', {}).pipe(
      tap(() => {
        this.currentUserSubject.next(null);
        this.router.navigate(['/login']);
      })
    );
  }

  getCurrentUser(): Observable<User> {
    return this.http.get<User>('/queryrest/api/auth/session');
  }

  isAuthenticated(): boolean {
    return this.currentUserSubject.value !== null;
  }

  hasRole(role: string): boolean {
    return this.currentUserSubject.value?.roles.includes(role) || false;
  }
}
```

#### Auth Guard

```typescript
@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(route: ActivatedRouteSnapshot): boolean {
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['/login']);
      return false;
    }

    const requiredRoles = route.data['roles'] as string[] | undefined;
    if (requiredRoles && !requiredRoles.some(role =>
        this.authService.hasRole(role))) {
      this.router.navigate(['/forbidden']);
      return false;
    }

    return true;
  }
}
```

#### HTTP Interceptor

```typescript
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // HTTP session cookies are sent automatically
    // This interceptor handles errors and redirects

    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401) {
          // Redirect to login
          window.location.href = '/login';
        }
        return throwError(() => error);
      })
    );
  }
}
```

#### Login Component

```typescript
@Component({
  selector: 'app-login',
  template: `
    <div class="login-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Sign In to QTT</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="provider-buttons">
            <button mat-raised-button
                    *ngFor="let provider of providers$ | async"
                    (click)="loginWithProvider(provider.id)"
                    color="primary">
              <mat-icon>{{ provider.icon }}</mat-icon>
              Sign in with {{ provider.name }}
            </button>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class LoginComponent implements OnInit {
  providers$ = this.http.get<AuthProvider[]>('/queryrest/api/auth/providers');

  constructor(
    private authService: AuthService,
    private http: HttpClient
  ) {}

  loginWithProvider(providerId: string): void {
    this.authService.loginWithProvider(providerId);
  }
}
```

---

### 6. Configuration Management

#### OSGi Configuration Files

**`/etc/com.inovexcorp.query.auth.cfg`**
```properties
# General Auth Settings
auth.enabled=true
auth.session.timeout=3600
auth.jwt.enabled=false
auth.default.role=USER

# Password fallback (optional)
auth.password.enabled=false
auth.password.bcrypt.rounds=12
```

**`/etc/com.inovexcorp.query.auth.oauth2.google.cfg`**
```properties
oauth2.google.enabled=true
oauth2.google.clientId=your-client-id.apps.googleusercontent.com
oauth2.google.clientSecret=GOCSPX-your-client-secret
oauth2.google.scopes=openid,email,profile
oauth2.google.callbackUrl=https://your-domain.com/queryrest/api/auth/callback/google
oauth2.google.roleMapping=.*@yourcompany.com:ADMIN
```

**`/etc/com.inovexcorp.query.auth.oidc.azure.cfg`**
```properties
oidc.azure.enabled=true
oidc.azure.clientId=your-azure-app-id
oidc.azure.clientSecret=your-azure-secret
oidc.azure.tenantId=your-tenant-id
oidc.azure.discoveryUri=https://login.microsoftonline.com/{tenant}/v2.0/.well-known/openid-configuration
oidc.azure.scopes=openid,email,profile
oidc.azure.callbackUrl=https://your-domain.com/queryrest/api/auth/callback/azure
oidc.azure.groupMapping=QTT-Admins:ADMIN,QTT-Users:USER
```

**`/etc/com.inovexcorp.query.auth.saml.okta.cfg`**
```properties
saml.okta.enabled=true
saml.okta.entityId=https://your-domain.com/queryrest
saml.okta.metadataUrl=https://your-org.okta.com/app/exk.../sso/saml/metadata
saml.okta.callbackUrl=https://your-domain.com/queryrest/api/auth/callback/saml-okta
saml.okta.signatureAlgorithm=RSA_SHA256
saml.okta.attributeMapping=email:email,firstName:given_name,lastName:family_name
saml.okta.roleAttribute=groups
saml.okta.roleMapping=QTT Admins:ADMIN,QTT Users:USER
```

---

### 7. Security Best Practices

#### Implemented Protections

1. **CSRF Protection**
   - Double-submit cookie pattern
   - Synchronizer token pattern for state parameters

2. **Session Security**
   - HTTP-only session cookies
   - Secure flag (HTTPS only)
   - SameSite=Lax attribute
   - Configurable session timeout
   - Session fixation protection (regenerate on login)

3. **Input Validation**
   - Whitelist allowed redirect URLs
   - Validate callback state parameters
   - Sanitize user profile data

4. **Token Security**
   - JWT signature validation (OIDC)
   - SAML assertion signature validation
   - Token expiration enforcement
   - Nonce validation (OIDC)

5. **Audit Logging**
   - Login attempts (success/failure)
   - Logout events
   - User provisioning events
   - Role changes
   - Session invalidations

6. **Rate Limiting**
   - Login attempt throttling
   - API rate limiting per user/session

---

### 8. JIT User Provisioning Logic

```java
@Service
public class UserProvisioningService {

    public User provisionUser(UserProfile profile, String identityProvider) {
        // 1. Check if user exists
        Optional<User> existingUser = userRepository
            .findByIdentityProviderAndExternalId(identityProvider, profile.getId());

        if (existingUser.isPresent()) {
            // Update last login and profile info
            User user = existingUser.get();
            user.setLastLoginAt(LocalDateTime.now());
            user.setEmail(profile.getEmail());
            user.setDisplayName(profile.getDisplayName());
            return userRepository.save(user);
        }

        // 2. Create new user
        User newUser = new User();
        newUser.setUsername(profile.getEmail()); // Or generate unique username
        newUser.setEmail(profile.getEmail());
        newUser.setDisplayName(profile.getDisplayName());
        newUser.setIdentityProvider(identityProvider);
        newUser.setExternalId(profile.getId());
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setLastLoginAt(LocalDateTime.now());
        newUser.setEnabled(true);

        // 3. Assign default roles
        Role defaultRole = roleRepository.findByName("USER")
            .orElseThrow(() -> new IllegalStateException("Default USER role not found"));
        newUser.setRoles(Set.of(defaultRole));

        // 4. Apply role mappings from configuration
        Set<Role> mappedRoles = applyRoleMappings(profile, identityProvider);
        newUser.getRoles().addAll(mappedRoles);

        // 5. Save and audit
        User savedUser = userRepository.save(newUser);
        auditService.logUserCreated(savedUser, identityProvider);

        return savedUser;
    }

    private Set<Role> applyRoleMappings(UserProfile profile, String provider) {
        // Load role mapping configuration
        Map<String, String> mappings = configurationService.getRoleMappings(provider);
        Set<Role> roles = new HashSet<>();

        // Example: Email domain mapping
        if (mappings.containsKey(profile.getEmail())) {
            String roleName = mappings.get(profile.getEmail());
            roleRepository.findByName(roleName).ifPresent(roles::add);
        }

        // Example: Group/role attribute mapping (OIDC/SAML)
        if (profile.getGroups() != null) {
            for (String group : profile.getGroups()) {
                if (mappings.containsKey(group)) {
                    String roleName = mappings.get(group);
                    roleRepository.findByName(roleName).ifPresent(roles::add);
                }
            }
        }

        return roles;
    }
}
```

---

### 9. Maven Dependencies (PAC4J)

```xml
<properties>
    <pac4j.version>5.7.1</pac4j.version>
    <nimbus.jose.jwt.version>9.37.3</nimbus.jose.jwt.version>
</properties>

<dependencies>
    <!-- PAC4J Core -->
    <dependency>
        <groupId>org.pac4j</groupId>
        <artifactId>pac4j-core</artifactId>
        <version>${pac4j.version}</version>
    </dependency>

    <!-- OAuth 2.0 Support -->
    <dependency>
        <groupId>org.pac4j</groupId>
        <artifactId>pac4j-oauth</artifactId>
        <version>${pac4j.version}</version>
    </dependency>

    <!-- OIDC Support -->
    <dependency>
        <groupId>org.pac4j</groupId>
        <artifactId>pac4j-oidc</artifactId>
        <version>${pac4j.version}</version>
    </dependency>

    <!-- SAML Support -->
    <dependency>
        <groupId>org.pac4j</groupId>
        <artifactId>pac4j-saml</artifactId>
        <version>${pac4j.version}</version>
    </dependency>

    <!-- JAX-RS Integration -->
    <dependency>
        <groupId>org.pac4j</groupId>
        <artifactId>jax-rs-pac4j</artifactId>
        <version>6.0.1</version>
    </dependency>

    <!-- JWT Support (for optional JWT tokens) -->
    <dependency>
        <groupId>com.nimbusds</groupId>
        <artifactId>nimbus-jose-jwt</artifactId>
        <version>${nimbus.jose.jwt.version}</version>
    </dependency>

    <!-- BCrypt for password hashing (optional fallback) -->
    <dependency>
        <groupId>org.mindrot</groupId>
        <artifactId>jbcrypt</artifactId>
        <version>0.4</version>
    </dependency>
</dependencies>
```

---

### 10. Karaf Feature Definition

**`query-service-feature/src/main/feature/feature.xml`**

```xml
<feature name="qs-auth" version="${project.version}">
    <details>QTT Authentication with SSO Support</details>

    <!-- PAC4J Dependencies -->
    <bundle>mvn:org.pac4j/pac4j-core/5.7.1</bundle>
    <bundle>mvn:org.pac4j/pac4j-oauth/5.7.1</bundle>
    <bundle>mvn:org.pac4j/pac4j-oidc/5.7.1</bundle>
    <bundle>mvn:org.pac4j/pac4j-saml/5.7.1</bundle>
    <bundle>mvn:org.pac4j/jax-rs-pac4j/6.0.1</bundle>

    <!-- JWT Support -->
    <bundle>mvn:com.nimbusds/nimbus-jose-jwt/9.37.3</bundle>
    <bundle>mvn:com.nimbusds/oauth2-oidc-sdk/11.9.1</bundle>

    <!-- BCrypt -->
    <bundle>mvn:org.mindrot/jbcrypt/0.4</bundle>

    <!-- Our auth module -->
    <bundle start-level="84">mvn:com.inovexcorp/query-service-auth/${project.version}</bundle>
</feature>
```

---

### 11. Deployment Checklist

#### Infrastructure Requirements

- [ ] SSL/TLS certificates (production)
- [ ] External IdP configuration (OAuth/OIDC/SAML)
- [ ] Database schema migration (users, roles, sessions tables)
- [ ] Redis for session storage (optional but recommended)
- [ ] Reverse proxy configuration (Nginx/Apache for cookie security)

#### Configuration Steps

1. **Register OAuth/OIDC Application**
   - Create app in Google/Azure/Okta console
   - Set redirect URI: `https://your-domain/queryrest/api/auth/callback/{provider}`
   - Note client ID and secret

2. **Configure SAML SP Metadata**
   - Generate SP certificate/key pair
   - Export metadata XML
   - Upload to IdP (Okta/Azure/etc.)
   - Configure assertion consumer service URL

3. **Database Initialization**
   - Run schema migration (Flyway/Liquibase)
   - Seed default roles (ADMIN, USER, VIEWER)
   - Create bootstrap admin user (if needed)

4. **Karaf Configuration**
   - Update `.cfg` files with client IDs/secrets
   - Configure role mappings
   - Enable desired authentication providers
   - Set session timeout and security flags

5. **UI Deployment**
   - Rebuild Angular app with auth components
   - Update route guards
   - Configure allowed redirect URLs

---

### 12. Testing Strategy

#### Unit Tests
- User provisioning logic
- Role mapping rules
- JWT token generation/validation
- Session management

#### Integration Tests
- OAuth callback handling
- OIDC token validation
- SAML assertion parsing
- Database persistence

#### E2E Tests
- Full OAuth flow (Selenium/Playwright)
- OIDC login with mock IdP
- SAML SSO with test IdP
- Session expiration behavior
- Logout and session cleanup

#### Security Tests
- CSRF token validation
- Session fixation prevention
- XSS prevention in user profiles
- SQL injection in queries
- SAML signature validation bypass attempts

---

## Implementation Phases

### Phase 1: Foundation (Week 1)
- Create `query-service-auth` module
- Implement JPA entities (User, Role, UserSession)
- Create persistence layer (repositories)
- Add OSGi configuration support

### Phase 2: OAuth/OIDC (Week 2)
- Integrate PAC4J library
- Implement OAuth 2.0 flow
- Implement OIDC flow
- Add JIT user provisioning
- Create authentication filter

### Phase 3: SAML (Week 3)
- Configure SAML SP
- Implement SAML authentication flow
- Test with Okta/Azure SAML

### Phase 4: UI Integration (Week 4)
- Create Angular auth service
- Implement login component
- Add auth guards to routes
- Add HTTP interceptor
- Update navigation/user menu

### Phase 5: Testing & Documentation (Week 5)
- Write unit/integration tests
- E2E testing with test IdPs
- Security testing
- Documentation and runbooks

---

## Security Considerations

### Secrets Management
- **DO NOT** commit client secrets to git
- Use environment variables or secure vault (HashiCorp Vault, Azure Key Vault)
- Rotate secrets periodically
- Use separate credentials for dev/staging/prod

### Session Security
- Enable HTTP-only cookies
- Use Secure flag (HTTPS only)
- Implement session timeout (default: 1 hour)
- Session fixation protection
- CSRF tokens for state management

### TLS/SSL
- Enforce HTTPS in production
- Use modern TLS 1.2+ only
- Configure strong cipher suites
- HSTS headers

### Audit Logging
- Log all authentication attempts
- Log authorization failures
- Log user provisioning events
- Retain logs for compliance (90+ days)
- Monitor for suspicious patterns

---

## Rollback Plan

If issues arise during deployment:

1. **Disable authentication globally**
   ```properties
   # /etc/com.inovexcorp.query.auth.cfg
   auth.enabled=false
   ```

2. **Disable specific provider**
   ```properties
   # /etc/com.inovexcorp.query.auth.oauth2.google.cfg
   oauth2.google.enabled=false
   ```

3. **Uninstall feature**
   ```bash
   karaf@root> feature:uninstall qs-auth
   ```

4. **Database rollback**
   - Keep schema migration scripts versioned
   - Maintain rollback SQL scripts

---

## Monitoring & Observability

### Metrics to Track
- Login success/failure rate
- JIT user provisioning count
- Session creation/expiration rate
- Active sessions count
- Authentication errors by provider
- Average login time

### Alerts
- Failed login rate > threshold
- Provider connectivity issues
- Session store failures (Redis down)
- Certificate expiration (SAML/JWT)

### Logs
- Authentication attempts (INFO level)
- Authorization failures (WARN level)
- Provider errors (ERROR level)
- JIT user creation (INFO level)

---

## Future Enhancements

1. **Multi-Factor Authentication (MFA)**
   - TOTP support
   - SMS verification
   - Email verification

2. **API Key Management**
   - Generate API keys for programmatic access
   - Scope-based permissions
   - Key rotation

3. **Social Login Providers**
   - GitHub
   - GitLab
   - LinkedIn

4. **Advanced RBAC**
   - Fine-grained permissions
   - Resource-level access control
   - Dynamic role assignment

5. **Self-Service User Management**
   - User profile editing
   - Password reset (if password auth enabled)
   - Session management UI

---

## Conclusion

This design provides a comprehensive, production-ready SSO authentication system for the QTT platform using industry-standard protocols and libraries. The modular architecture allows incremental deployment and easy maintenance while maintaining security best practices.

**Key Benefits:**
✅ Supports OAuth 2.0, OIDC, and SAML
✅ Karaf/OSGi compatible (PAC4J)
✅ Just-in-time user provisioning
✅ Flexible role mapping
✅ Leverages existing infrastructure (JPA, Redis)
✅ Protects UI while leaving Camel routes open
✅ Comprehensive audit logging
✅ Production-ready security controls
