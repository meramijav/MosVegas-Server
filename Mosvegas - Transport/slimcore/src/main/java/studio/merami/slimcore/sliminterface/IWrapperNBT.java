package studio.merami.slimcore.sliminterface;

import java.util.List;
import java.util.UUID;

public interface IWrapperNBT {
    boolean has(String key);

    boolean hasList(String key);

    int getInt(String key);

    void setInt(String key, int value);

    float getFloat(String key);

    void setFloat(String key, float value);

    String getString(String key);

    void setString(String key, String value);

    boolean hasUUID(String key);

    UUID getUUID(String key);

    void setUUID(String key, UUID value);

    List<Float> getFloats(String key);

    void setFloats(String key, List<Float> values);

    List<IWrapperNBT> getCompounds(String key);

    void setCompounds(String key, List<IWrapperNBT> values);
}
