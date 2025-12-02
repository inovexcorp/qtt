package com.inovexcorp.query.auth.service.impl;

import com.inovexcorp.query.auth.model.Role;
import com.inovexcorp.query.auth.model.User;
import com.inovexcorp.query.auth.service.RoleService;
import com.inovexcorp.query.auth.service.SecurityEventService;
import com.inovexcorp.query.auth.service.UserProvisioningService;
import com.inovexcorp.query.auth.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component(immediate = true, service = UserProvisioningService.class)
public class UserProvisioningServiceImpl implements UserProvisioningService {

    @Reference
    private UserService userService;

    @Reference
    private RoleService roleService;

    @Reference
    private SecurityEventService securityEventService;

    @Override
    public User provisionUser(UserProfile profile, String identityProvider) {
        // Check if user already exists
        Optional<User> existingUser = userService.findByProviderAndExternalId(identityProvider, profile.getExternalId());

        if (existingUser.isPresent()) {
            // Update existing user
            User user = existingUser.get();
            user.setEmail(profile.getEmail());
            user.setDisplayName(profile.getDisplayName());
            user.setFirstName(profile.getFirstName());
            user.setLastName(profile.getLastName());
            user.setLastLoginAt(LocalDateTime.now());

            userService.updateUser(user);
            log.info("Updated existing user from {}: {}", identityProvider, user.getEmail());
            return user;
        }

        // Create new user
        User newUser = new User();
        newUser.setUsername(profile.getEmail());
        newUser.setEmail(profile.getEmail());
        newUser.setDisplayName(profile.getDisplayName());
        newUser.setFirstName(profile.getFirstName());
        newUser.setLastName(profile.getLastName());
        newUser.setIdentityProvider(identityProvider);
        newUser.setExternalId(profile.getExternalId());
        newUser.setEnabled(true);
        newUser.setCreatedBy(identityProvider);
        newUser.setLastLoginAt(LocalDateTime.now());

        // Assign default USER role
        Optional<Role> defaultRole = roleService.findByName("USER");
        if (defaultRole.isPresent()) {
            newUser.addRole(defaultRole.get());
        } else {
            log.warn("Default USER role not found, user will have no roles");
        }

        // Apply role mappings from configuration
        applyRoleMappings(newUser, profile, identityProvider);

        User savedUser = userService.createUser(newUser);

        // Log user creation event
        securityEventService.logUserCreated(savedUser.getId(), savedUser.getUsername(), identityProvider);

        log.info("Provisioned new user from {}: {}", identityProvider, savedUser.getEmail());
        return savedUser;
    }

    @Override
    public boolean shouldAutoProvision(String identityProvider) {
        // TODO: Read from OSGi configuration
        // For now, always auto-provision
        return true;
    }

    @Override
    public void applyRoleMappings(User user, UserProfile profile, String identityProvider) {
        // TODO: Implement role mapping logic based on OSGi configuration
        // Example: email domain mapping, group membership mapping, etc.

        // Simple example: admin@yourcompany.com gets ADMIN role
        if (profile.getEmail() != null && profile.getEmail().equals("admin@yourcompany.com")) {
            roleService.findByName("ADMIN").ifPresent(user::addRole);
            log.info("Applied ADMIN role to user: {}", profile.getEmail());
        }
    }
}
