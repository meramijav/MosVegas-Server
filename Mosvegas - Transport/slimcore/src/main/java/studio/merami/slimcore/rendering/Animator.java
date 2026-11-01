package studio.merami.slimcore.rendering;

import studio.merami.slimcore.jsondefs.AJSONItem;

import java.util.List;
import java.util.function.Predicate;

public final class Animator {
    public static final int ROTATION = 0;
    public static final int POSITION = 3;
    public static final int SCALE = 6;
    public static final int CHANNELS = 9;
    private static final double RESET_TICKS = 1;

    private enum State {
        STOPPED,
        TRANSITION,
        RUNNING
    }

    private final class Controller implements MoLang.Context {
        final AJSONItem.Animation entry;
        final AnimationFile.Clip clip;
        final int[] bones;
        State state = State.STOPPED;
        double start;
        double animTime;
        float[][] from;

        Controller(AJSONItem.Animation entry, AnimationFile.Clip clip) {
            this.entry = entry;
            this.clip = clip;
            bones = new int[clip == null ? 0 : clip.tracks().size()];
            for (int i = 0; i < bones.length; i++) {
                ModelGeometry.Bone bone = model.bone(clip.tracks().get(i).bone());
                bones[i] = bone == null ? -1 : bone.index;
            }
        }

        @Override
        public double animTime() {
            return animTime;
        }

        @Override
        public double lifeTime() {
            return lifeTime;
        }
    }

    public final ModelGeometry model;
    private final Controller[] controllers;
    private final Pose pose;
    private final float[][] resetFrom;
    private final double[][] resetStart;
    private final boolean[][] animated;
    private final boolean[][] wasAnimated;
    private double lifeTime;

    public Animator(ModelGeometry model, AnimationFile file, List<AJSONItem.Animation> animations) {
        this.model = model;
        controllers = new Controller[animations.size()];
        for (int i = 0; i < controllers.length; i++) {
            AJSONItem.Animation entry = animations.get(i);
            controllers[i] = new Controller(entry, file == null ? null : file.clip(entry.name));
        }
        int count = model.bones.size();
        pose = new Pose(model);
        resetFrom = new float[count][];
        for (int i = 0; i < count; i++) resetFrom[i] = pose.bind[i].clone();
        resetStart = new double[count][3];
        animated = new boolean[count][3];
        wasAnimated = new boolean[count][3];
    }

    public Pose pose() {
        return pose;
    }

    public void update(double time, double lifeSeconds, Predicate<String> condition) {
        lifeTime = lifeSeconds;
        for (boolean[] row : animated) java.util.Arrays.fill(row, false);
        for (Controller controller : controllers) {
            if (controller.clip == null) continue;
            if (!condition.test(controller.entry.when)) {
                controller.state = State.STOPPED;
                continue;
            }
            if (controller.state == State.STOPPED) {
                controller.start = time;
                if (controller.entry.transition > 0) {
                    controller.state = State.TRANSITION;
                    controller.from = new float[controller.bones.length][CHANNELS];
                    for (int i = 0; i < controller.bones.length; i++) {
                        int bone = controller.bones[i];
                        if (bone < 0) continue;
                        System.arraycopy(pose.values[bone], 0, controller.from[i], 0, CHANNELS);
                    }
                } else {
                    controller.state = State.RUNNING;
                }
            }
            if (controller.state == State.TRANSITION) {
                double t = time - controller.start;
                if (t >= controller.entry.transition) {
                    controller.state = State.RUNNING;
                    controller.start = time;
                } else {
                    transition(controller, t);
                    continue;
                }
            }
            double t = time - controller.start;
            if (t >= controller.clip.length()) {
                if (controller.entry.loop) {
                    controller.start = time;
                    t = 0;
                }
            }
            run(controller, t);
        }
        for (int bone = 0; bone < animated.length; bone++) {
            for (int channel = 0; channel < 3; channel++) {
                if (animated[bone][channel]) {
                    wasAnimated[bone][channel] = true;
                    continue;
                }
                if (wasAnimated[bone][channel]) {
                    wasAnimated[bone][channel] = false;
                    resetStart[bone][channel] = time;
                    System.arraycopy(pose.values[bone], channel * 3, resetFrom[bone], channel * 3, 3);
                }
                double progress = Math.min((time - resetStart[bone][channel]) / RESET_TICKS, 1);
                for (int axis = 0; axis < 3; axis++) {
                    int index = channel * 3 + axis;
                    pose.values[bone][index] = (float) (resetFrom[bone][index] + (pose.bind[bone][index] - resetFrom[bone][index]) * progress);
                }
                if (progress >= 1) System.arraycopy(pose.bind[bone], channel * 3, resetFrom[bone], channel * 3, 3);
            }
        }
    }

    private void transition(Controller controller, double t) {
        controller.animTime = 0;
        for (int i = 0; i < controller.bones.length; i++) {
            int bone = controller.bones[i];
            if (bone < 0) continue;
            AnimationFile.Track track = controller.clip.tracks().get(i);
            AnimationFile.Channel[] channels = {track.rotation(), track.position(), track.scale()};
            for (int channel = 0; channel < 3; channel++) {
                if (channels[channel].isEmpty()) continue;
                for (AnimationFile.Axis axis : AnimationFile.Axis.values()) {
                    int index = channel * 3 + axis.ordinal();
                    double from = controller.from[i][index] - (channel == 0 ? pose.bind[bone][index] : 0);
                    double to = channels[channel].first(axis, controller);
                    double value = Easing.LINEAR.interpolate(t, controller.entry.transition, from, to, null);
                    pose.values[bone][index] = (float) value + (channel == 0 ? pose.bind[bone][index] : 0);
                }
                animated[bone][channel] = true;
            }
        }
    }

    private void run(Controller controller, double t) {
        controller.animTime = t / 20;
        for (int i = 0; i < controller.bones.length; i++) {
            int bone = controller.bones[i];
            if (bone < 0) continue;
            AnimationFile.Track track = controller.clip.tracks().get(i);
            AnimationFile.Channel[] channels = {track.rotation(), track.position(), track.scale()};
            for (int channel = 0; channel < 3; channel++) {
                if (channels[channel].isEmpty()) continue;
                for (AnimationFile.Axis axis : AnimationFile.Axis.values()) {
                    int index = channel * 3 + axis.ordinal();
                    double value = channels[channel].value(axis, t, controller);
                    pose.values[bone][index] = (float) value + (channel == 0 ? pose.bind[bone][index] : 0);
                }
                animated[bone][channel] = true;
            }
        }
    }

    public static final class Pose {
        public final ModelGeometry model;
        public final float[][] bind;
        public final float[][] values;
        public final boolean[] hidden;
        public final boolean[] hideChildren;

        Pose(ModelGeometry model) {
            this.model = model;
            int count = model.bones.size();
            bind = new float[count][CHANNELS];
            values = new float[count][CHANNELS];
            hidden = new boolean[count];
            hideChildren = new boolean[count];
            for (ModelGeometry.Bone bone : model.bones) {
                float[] rest = bind[bone.index];
                rest[ROTATION] = bone.rotX;
                rest[ROTATION + 1] = bone.rotY;
                rest[ROTATION + 2] = bone.rotZ;
                rest[SCALE] = rest[SCALE + 1] = rest[SCALE + 2] = 1;
                System.arraycopy(rest, 0, values[bone.index], 0, CHANNELS);
                hidden[bone.index] = bone.neverRender;
            }
        }

        public Pose(Pose other) {
            model = other.model;
            bind = other.bind;
            int count = other.values.length;
            values = new float[count][];
            for (int i = 0; i < count; i++) values[i] = other.values[i].clone();
            hidden = other.hidden.clone();
            hideChildren = other.hideChildren.clone();
        }

        public void copyFrom(Pose other) {
            for (int i = 0; i < values.length; i++) System.arraycopy(other.values[i], 0, values[i], 0, CHANNELS);
            System.arraycopy(other.hidden, 0, hidden, 0, hidden.length);
            System.arraycopy(other.hideChildren, 0, hideChildren, 0, hideChildren.length);
        }

        public int bone(String name) {
            ModelGeometry.Bone bone = model.bone(name);
            return bone == null ? -1 : bone.index;
        }

        public void set(int bone, int channel, float value) {
            if (bone >= 0) values[bone][channel] = value;
        }

        public void setHidden(int bone, boolean value) {
            if (bone < 0) return;
            hidden[bone] = value;
            hideChildren[bone] = value;
        }
    }
}
