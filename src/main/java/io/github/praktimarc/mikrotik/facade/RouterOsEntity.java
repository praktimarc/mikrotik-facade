package io.github.praktimarc.mikrotik.facade;

/**
 * Marker contract for typed RouterOS entities that retain their complete raw record.
 */
public interface RouterOsEntity {

    /**
     * Returns the complete raw RouterOS record from which this entity was mapped.
     *
     * @return immutable raw RouterOS record
     */
    RouterOsRecord raw();
}
