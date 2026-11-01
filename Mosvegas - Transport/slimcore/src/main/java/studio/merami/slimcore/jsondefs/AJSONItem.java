package studio.merami.slimcore.jsondefs;

import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.baseclasses.ResourceId;
import studio.merami.slimcore.baseclasses.Text;

import java.util.ArrayList;
import java.util.List;

public abstract class AJSONItem {
    public static final ResourceId EMPTY_ANIMATION = SlimCore.id("animations/empty.animation.json");

    public String label;
    public boolean item = true;
    public String model;
    public String texture;
    public String animation;
    public double scale = 1;
    public List<Animation> animations = new ArrayList<>();

    public transient String id;
    public transient String namespace;
    public transient String name;
    public transient String packName;
    public transient ResourceId modelLocation;
    public transient ResourceId textureLocation;
    public transient ResourceId animationLocation;

    public static class Animation {
        public String name;
        public String when = "always";
        public boolean loop = true;
        public int transition;
    }

    public void bake() {
        if (texture == null) texture = model;
        modelLocation = model == null ? null : asset(model, "geo/", ".geo.json");
        textureLocation = texture == null ? null : asset(texture, "textures/entity/", ".png");
        animationLocation = animation == null ? EMPTY_ANIMATION : asset(animation, "animations/", ".animation.json");
        for (Animation entry : animations) {
            if (entry.name == null) throw new IllegalArgumentException("an animation has no \"name\"");
        }
    }

    public ResourceId qualify(String location) {
        return location.contains(":") ? ResourceId.parse(location) : new ResourceId(namespace, location);
    }

    protected ResourceId asset(String location, String folder, String suffix) {
        ResourceId qualified = qualify(location);
        return new ResourceId(qualified.namespace(), folder + qualified.path() + suffix);
    }

    public String itemKey() {
        return "item." + namespace + "." + name;
    }

    public Text displayName() {
        return label == null ? Text.withFallback(itemKey(), name) : text(label);
    }

    public static Text text(String keyOrText) {
        return Text.withFallback(keyOrText, keyOrText);
    }

    @Override
    public String toString() {
        return id;
    }
}
