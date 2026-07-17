package com.giri.oms.auth.entity;

/**
 * Spring Security expects authorities to be prefixed with "ROLE_" when checked
 * via hasRole(...) — that prefixing happens in AppUser.getAuthorities(), not
 * here, so this enum itself stays a plain, storable value.
 */
public enum Role {
    ADMIN,
    MANAGER,
    STAFF
}
