package com.inovexcorp.queryservice.persistence;

import java.util.Date;
import java.util.List;

/** Stored API keys for the query routes. */
public interface ApiKeyService {

    void add(ApiKey key);

    /** The key with this ID, or null. */
    ApiKey get(String id);

    /** All keys, revoked ones included, newest first. */
    List<ApiKey> getAll();

    /** Marks a key revoked; false when there is no such key. Revoking twice keeps the first revocation. */
    boolean revoke(String id, String revokedBy, Date when);

    /** Records that a key was used (best effort, not transactional with the request). */
    void touch(String id, Date when);
}
