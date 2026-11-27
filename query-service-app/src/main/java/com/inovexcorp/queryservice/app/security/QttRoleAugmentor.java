package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.security.QttRoles;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashSet;
import java.util.Set;

/**
 * Turns the roles or groups an identity provider puts in its tokens into QTT roles.
 * <ul>
 *   <li>A token role named {@code qtt-viewer}, {@code qtt-editor} or {@code qtt-admin} is used as is.</li>
 *   <li>Any other name is looked up in {@code OIDC_VIEWER_GROUPS}, {@code OIDC_EDITOR_GROUPS} and
 *       {@code OIDC_ADMIN_GROUPS}, so sites can grant QTT roles to groups they already have.</li>
 *   <li>Higher roles include lower ones: admin adds editor and viewer, editor adds viewer.</li>
 * </ul>
 */
@ApplicationScoped
public class QttRoleAugmentor implements SecurityIdentityAugmentor {

    private final AuthSettings settings;

    QttRoleAugmentor(AuthSettings settings) {
        this.settings = settings;
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }
        Set<String> qttRoles = qttRoles(identity.getRoles(), settings);
        if (identity.getRoles().containsAll(qttRoles)) {
            return Uni.createFrom().item(identity);
        }
        return Uni.createFrom().item(QuarkusSecurityIdentity.builder(identity).addRoles(qttRoles).build());
    }

    /** The QTT roles granted by a set of token roles or groups, with implied roles added. */
    static Set<String> qttRoles(Set<String> tokenRoles, AuthSettings settings) {
        boolean admin = false;
        boolean editor = false;
        boolean viewer = false;
        for (String role : tokenRoles) {
            admin |= QttRoles.ADMIN.equals(role) || settings.adminGroups().contains(role);
            editor |= QttRoles.EDITOR.equals(role) || settings.editorGroups().contains(role);
            viewer |= QttRoles.VIEWER.equals(role) || settings.viewerGroups().contains(role);
        }
        Set<String> roles = new HashSet<>();
        if (admin) {
            roles.add(QttRoles.ADMIN);
        }
        if (admin || editor) {
            roles.add(QttRoles.EDITOR);
        }
        if (admin || editor || viewer) {
            roles.add(QttRoles.VIEWER);
        }
        return roles;
    }
}
