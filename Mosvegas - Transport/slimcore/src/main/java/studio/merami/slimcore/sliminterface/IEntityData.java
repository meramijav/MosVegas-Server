package studio.merami.slimcore.sliminterface;

public interface IEntityData {
    <T> T get(SyncedValue<T> key);

    <T> void set(SyncedValue<T> key, T value);
}
