package studio.merami.slimcore.sliminterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SyncedValue<T> {
    public enum Kind {
        INT,
        FLOAT,
        STRING,
        NBT
    }

    private static final List<SyncedValue<?>> ALL = new ArrayList<>();

    public final Class<?> owner;
    public final Kind kind;
    public final T initial;
    public final int index;

    private SyncedValue(Class<?> owner, Kind kind, T initial) {
        this.owner = owner;
        this.kind = kind;
        this.initial = initial;
        synchronized (ALL) {
            index = ALL.size();
            ALL.add(this);
        }
    }

    public static SyncedValue<Integer> integer(Class<?> owner, int initial) {
        return new SyncedValue<>(owner, Kind.INT, initial);
    }

    public static SyncedValue<Float> decimal(Class<?> owner, float initial) {
        return new SyncedValue<>(owner, Kind.FLOAT, initial);
    }

    public static SyncedValue<String> text(Class<?> owner, String initial) {
        return new SyncedValue<>(owner, Kind.STRING, initial);
    }

    public static SyncedValue<IWrapperNBT> nbt(Class<?> owner) {
        return new SyncedValue<>(owner, Kind.NBT, null);
    }

    public static List<SyncedValue<?>> of(Class<?> entity) {
        for (Class<?> type = entity; type != null; type = type.getSuperclass()) {
            try {
                Class.forName(type.getName(), true, type.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        List<SyncedValue<?>> result = new ArrayList<>();
        synchronized (ALL) {
            for (SyncedValue<?> value : ALL) {
                if (value.owner.isAssignableFrom(entity)) result.add(value);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public String toString() {
        return owner.getSimpleName() + "#" + index + ":" + kind;
    }
}
