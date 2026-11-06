package org.minecraft.atlas.storage;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * A set of domain snapshots. When loading, every domain is present. When writing, only the
 * domains present are replaced; the others are left untouched.
 */
public final class DataSet {

    private final Map<DataDomain, Object> values = new EnumMap<>(DataDomain.class);

    public DataSet put(DataDomain domain, Object value) {
        if (value == null) throw new IllegalArgumentException("null snapshot for " + domain);
        if (!domain.type().isInstance(value)) {
            throw new IllegalArgumentException(domain + " expects " + domain.type().getSimpleName()
                    + ", got " + value.getClass().getSimpleName());
        }
        values.put(domain, value);
        return this;
    }

    /** The snapshot of {@code domain}, or its empty value if absent. */
    public <T> T get(DataDomain domain, Class<T> type) {
        Object v = values.get(domain);
        return type.cast(v != null ? v : domain.empty());
    }

    /** The snapshot of {@code domain}, or null if absent. */
    public Object raw(DataDomain domain) {
        return values.get(domain);
    }

    public boolean has(DataDomain domain) {
        return values.containsKey(domain);
    }

    public Set<DataDomain> domains() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
