package studio.merami.slimcore.entities.components;

import studio.merami.slimcore.jsondefs.AJSONItem;
import studio.merami.slimcore.rendering.AnimationFile;
import studio.merami.slimcore.rendering.Animator;
import studio.merami.slimcore.rendering.ModelGeometry;
import studio.merami.slimcore.sliminterface.IEntityData;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.IWrapperNBT;
import studio.merami.slimcore.sliminterface.SyncedValue;

public abstract class AEntityD_Definable<J extends AJSONItem> extends AEntityC_Renderable {
    private static final SyncedValue<String> DEFINITION = SyncedValue.text(AEntityD_Definable.class, "");
    private static final SyncedValue<Integer> FLAGS = SyncedValue.integer(AEntityD_Definable.class, 0);

    private J definition;
    private String definitionId;
    private Animator animator;
    private J animatorDefinition;

    protected AEntityD_Definable(IWrapperEntity host, IEntityData data) {
        super(host, data);
    }

    protected abstract J lookupDefinition(String id);

    public J definition() {
        String id = data.get(DEFINITION);
        if (definition == null || id != definitionId) {
            definition = lookupDefinition(id);
            definitionId = id;
        }
        return definition;
    }

    public void setDefinition(String id) {
        data.set(DEFINITION, lookupDefinition(id).id);
        onDefinitionChanged();
    }

    protected void onDefinitionChanged() {}

    public Animator animator() {
        J current = definition();
        if (current.modelLocation == null) return null;
        ModelGeometry model = ModelGeometry.load(current.modelLocation);
        if (animator == null || animator.model != model || animatorDefinition != current) {
            animator = new Animator(model, current.animations.isEmpty() ? null : AnimationFile.load(current.animationLocation), current.animations);
            animatorDefinition = current;
        }
        return animator;
    }

    @Override
    public double scale() {
        return definition().scale;
    }

    public boolean flag(int flag) {
        return (data.get(FLAGS) & flag) != 0;
    }

    public void setFlag(int flag, boolean value) {
        data.set(FLAGS, value ? data.get(FLAGS) | flag : data.get(FLAGS) & ~flag);
    }

    protected int flags() {
        return data.get(FLAGS);
    }

    protected void setFlags(int flags) {
        data.set(FLAGS, flags);
    }

    @Override
    public void onSyncedDataUpdated(SyncedValue<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == DEFINITION) onDefinitionChanged();
    }

    @Override
    public void load(IWrapperNBT tag) {
        super.load(tag);
        setDefinition(tag.getString("Definition"));
        setFlags(tag.getInt("Flags"));
    }

    @Override
    public void save(IWrapperNBT tag) {
        super.save(tag);
        tag.setString("Definition", definition().id);
        tag.setInt("Flags", flags());
    }
}
