-- Add cache configuration fields to routes table
ALTER TABLE routes ADD COLUMN cacheEnabled BOOLEAN DEFAULT FALSE;
ALTER TABLE routes ADD COLUMN cacheTtlSeconds INTEGER;
ALTER TABLE routes ADD COLUMN cacheKeyStrategy VARCHAR(50) DEFAULT 'QUERY_HASH';

-- Add comments for documentation
COMMENT ON COLUMN routes.cacheEnabled IS 'Enable/disable caching for this route';
COMMENT ON COLUMN routes.cacheTtlSeconds IS 'Cache TTL in seconds (NULL = use global default)';
COMMENT ON COLUMN routes.cacheKeyStrategy IS 'Cache key generation strategy (QUERY_HASH, etc.)';
