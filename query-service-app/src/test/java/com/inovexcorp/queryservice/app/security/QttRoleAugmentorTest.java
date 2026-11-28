package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.security.QttRoles;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.inovexcorp.queryservice.security.QttRoles.ADMIN;
import static com.inovexcorp.queryservice.security.QttRoles.EDITOR;
import static com.inovexcorp.queryservice.security.QttRoles.VIEWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QttRoleAugmentorTest {

    private static final AuthSettings NO_GROUPS = AuthSettings.of(true, List.of(), List.of(), List.of());

    @Test
    void higherRolesIncludeLowerOnes() {
        assertEquals(Set.of(VIEWER), QttRoleAugmentor.qttRoles(Set.of(VIEWER), NO_GROUPS));
        assertEquals(Set.of(VIEWER, EDITOR), QttRoleAugmentor.qttRoles(Set.of(EDITOR), NO_GROUPS));
        assertEquals(Set.of(VIEWER, EDITOR, ADMIN), QttRoleAugmentor.qttRoles(Set.of(ADMIN), NO_GROUPS));
    }

    @Test
    void unrelatedRolesGrantNothing() {
        assertEquals(Set.of(), QttRoleAugmentor.qttRoles(Set.of("offline_access", "uma_authorization"), NO_GROUPS));
    }

    @Test
    void configuredGroupsMapToRoles() {
        AuthSettings settings = AuthSettings.of(true, List.of("Readers"), List.of("Query Authors", " devs "),
                List.of("/platform/ops"));
        assertEquals(Set.of(VIEWER), QttRoleAugmentor.qttRoles(Set.of("Readers"), settings));
        assertEquals(Set.of(VIEWER, EDITOR), QttRoleAugmentor.qttRoles(Set.of("Query Authors"), settings));
        assertEquals(Set.of(VIEWER, EDITOR), QttRoleAugmentor.qttRoles(Set.of("devs"), settings));
        assertEquals(Set.of(VIEWER, EDITOR, ADMIN), QttRoleAugmentor.qttRoles(Set.of("Readers", "/platform/ops"), settings));
        assertEquals(Set.of(), QttRoleAugmentor.qttRoles(Set.of("readers"), settings), "group names are case-sensitive");
    }

    @Test
    void augmentAddsRolesAndKeepsThePrincipal() {
        SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("alice")).addRole(EDITOR).addRole("offline_access").build();
        SecurityIdentity augmented = new QttRoleAugmentor(NO_GROUPS).augment(identity, null).await().indefinitely();
        assertEquals("alice", augmented.getPrincipal().getName());
        assertEquals(Set.of(EDITOR, VIEWER, "offline_access"), augmented.getRoles());
    }

    @Test
    void anonymousAndAlreadyCompleteIdentitiesAreUnchanged() {
        SecurityIdentity anonymous = QuarkusSecurityIdentity.builder().setAnonymous(true).build();
        assertSame(anonymous, new QttRoleAugmentor(NO_GROUPS).augment(anonymous, null).await().indefinitely());

        SecurityIdentity complete = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("bob")).addRoles(Set.of(VIEWER, EDITOR)).build();
        assertSame(complete, new QttRoleAugmentor(NO_GROUPS).augment(complete, null).await().indefinitely());
        assertTrue(complete.hasRole(EDITOR));
    }

    @Test
    void qttQueryStandsApartFromTheManagementRoles() {
        AuthSettings settings = AuthSettings.of(true, List.of(), List.of(), List.of("ops"), List.of("data-apps"), "open");
        assertEquals(Set.of(QttRoles.QUERY), QttRoleAugmentor.qttRoles(Set.of(QttRoles.QUERY), settings));
        assertEquals(Set.of(QttRoles.QUERY), QttRoleAugmentor.qttRoles(Set.of("data-apps"), settings));
        // Admin doesn't imply query, and query implies nothing.
        assertEquals(Set.of(VIEWER, EDITOR, ADMIN), QttRoleAugmentor.qttRoles(Set.of("ops"), settings));
        assertEquals(Set.of(VIEWER, EDITOR, ADMIN, QttRoles.QUERY),
                QttRoleAugmentor.qttRoles(Set.of("ops", "data-apps"), settings));
    }

    @Test
    void anInvalidQueryAuthDefaultStopsStartup() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> AuthSettings.of(false, List.of(), List.of(), List.of(), List.of(), "default"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> AuthSettings.of(false, List.of(), List.of(), List.of(), List.of(), "public"));
        assertEquals(com.inovexcorp.queryservice.QueryAccess.AUTHENTICATED,
                AuthSettings.of(false, List.of(), List.of(), List.of(), List.of(), "Authenticated").queryAuthDefault());
    }
}
