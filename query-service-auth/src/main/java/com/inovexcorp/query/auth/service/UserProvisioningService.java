package com.inovexcorp.query.auth.service;

import com.inovexcorp.query.auth.model.User;

import java.util.Map;

/**
 * Service for Just-in-Time (JIT) user provisioning from external identity providers.
 */
public interface UserProvisioningService {

    /**
     * User profile from external identity provider
     */
    class UserProfile {
        private String externalId;
        private String email;
        private String displayName;
        private String firstName;
        private String lastName;
        private Map<String, Object> attributes;

        public UserProfile(String externalId, String email) {
            this.externalId = externalId;
            this.email = email;
        }

        public String getExternalId() { return externalId; }
        public void setExternalId(String externalId) { this.externalId = externalId; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public Map<String, Object> getAttributes() { return attributes; }
        public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
    }

    /**
     * Provision or update user from external identity provider
     *
     * @param profile User profile from IdP
     * @param identityProvider Provider identifier (e.g., "google", "azure-ad")
     * @return Provisioned user
     */
    User provisionUser(UserProfile profile, String identityProvider);

    /**
     * Check if user should be auto-provisioned based on configuration
     */
    boolean shouldAutoProvision(String identityProvider);

    /**
     * Apply role mappings based on user profile and provider configuration
     */
    void applyRoleMappings(User user, UserProfile profile, String identityProvider);
}
