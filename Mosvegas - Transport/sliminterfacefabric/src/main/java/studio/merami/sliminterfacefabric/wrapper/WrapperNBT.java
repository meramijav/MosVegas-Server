package studio.merami.sliminterfacefabric.wrapper;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import studio.merami.slimcore.sliminterface.IWrapperNBT;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WrapperNBT implements IWrapperNBT {
    public final CompoundTag tag;

    public WrapperNBT(CompoundTag tag) {
        this.tag = tag;
    }

    @Override
    public boolean has(String key) {
        return tag.contains(key);
    }

    @Override
    public boolean hasList(String key) {
        return tag.contains(key, Tag.TAG_LIST);
    }

    @Override
    public int getInt(String key) {
        return tag.getInt(key);
    }

    @Override
    public void setInt(String key, int value) {
        tag.putInt(key, value);
    }

    @Override
    public float getFloat(String key) {
        return tag.getFloat(key);
    }

    @Override
    public void setFloat(String key, float value) {
        tag.putFloat(key, value);
    }

    @Override
    public String getString(String key) {
        return tag.getString(key);
    }

    @Override
    public void setString(String key, String value) {
        tag.putString(key, value);
    }

    @Override
    public boolean hasUUID(String key) {
        return tag.hasUUID(key);
    }

    @Override
    public UUID getUUID(String key) {
        return tag.getUUID(key);
    }

    @Override
    public void setUUID(String key, UUID value) {
        tag.putUUID(key, value);
    }

    @Override
    public List<Float> getFloats(String key) {
        ListTag list = tag.getList(key, Tag.TAG_FLOAT);
        List<Float> values = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) values.add(list.getFloat(i));
        return values;
    }

    @Override
    public void setFloats(String key, List<Float> values) {
        ListTag list = new ListTag();
        for (float value : values) list.add(FloatTag.valueOf(value));
        tag.put(key, list);
    }

    @Override
    public List<IWrapperNBT> getCompounds(String key) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        List<IWrapperNBT> values = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) values.add(new WrapperNBT(list.getCompound(i)));
        return values;
    }

    @Override
    public void setCompounds(String key, List<IWrapperNBT> values) {
        ListTag list = new ListTag();
        for (IWrapperNBT value : values) list.add(((WrapperNBT) value).tag);
        tag.put(key, list);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof WrapperNBT other && other.tag.equals(tag);
    }

    @Override
    public int hashCode() {
        return tag.hashCode();
    }

    @Override
    public String toString() {
        return tag.toString();
    }
}
