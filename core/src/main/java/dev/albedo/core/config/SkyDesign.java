package dev.albedo.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A custom sky as an immutable list of layers from the bottom up, where a mask may name only a lower layer so that one
 * pass from the bottom makes the sky.
 */
public final class SkyDesign {
    public static final int MAX_LAYERS = 8;

    /** Whether the game draws the sun, moon and stars over the custom sky. */
    public enum Celestial {
        SHOW, HIDE;

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Whether the custom sky keeps its brightness or gets darker at night and in rain, as the vanilla sky does. */
    public enum Light {
        FIXED, DAY_NIGHT;

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Whether the layers stop at the horizon, with the horizon color below it, or go around the full sphere. */
    public enum Extent {
        HORIZON, SPHERE;

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Whether the GPU draws the sky sharp for each screen pixel when it can, or always from the baked image, which costs less. */
    public enum Render {
        SHARP, FAST;

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final SkyDesign NONE = new SkyDesign(Collections.<SkyLayer>emptyList(), Celestial.SHOW, Light.FIXED, Extent.HORIZON);

    private final List<SkyLayer> layers;
    private final Celestial celestial;
    private final Light light;
    private final Extent extent;
    private final Render render;
    private final SkyTurning turning;

    /** A design with the sharp render. */
    public SkyDesign(List<SkyLayer> layers, Celestial celestial, Light light, Extent extent) {
        this(layers, celestial, light, extent, Render.SHARP);
    }

    /** A design with the default turning. */
    public SkyDesign(List<SkyLayer> layers, Celestial celestial, Light light, Extent extent, Render render) {
        this(layers, celestial, light, extent, render, SkyTurning.DEFAULT);
    }

    public SkyDesign(List<SkyLayer> layers, Celestial celestial, Light light, Extent extent, Render render, SkyTurning turning) {
        this.layers = Collections.unmodifiableList(new ArrayList<SkyLayer>(layers));
        this.celestial = celestial;
        this.light = light;
        this.extent = extent;
        this.render = render;
        this.turning = turning;
    }

    /** From the bottom layer up. */
    public List<SkyLayer> layers() { return layers; }

    public Celestial celestial() { return celestial; }

    public Light light() { return light; }

    public Extent extent() { return extent; }

    public Render render() { return render; }

    public SkyTurning turning() { return turning; }

    public boolean isEmpty() { return layers.isEmpty(); }

    public boolean isFull() { return layers.size() >= MAX_LAYERS; }

    public SkyDesign withLayers(List<SkyLayer> next) { return new SkyDesign(next, celestial, light, extent, render, turning); }

    public SkyDesign withCelestial(Celestial c) { return new SkyDesign(layers, c, light, extent, render, turning); }

    public SkyDesign withLight(Light l) { return new SkyDesign(layers, celestial, l, extent, render, turning); }

    public SkyDesign withExtent(Extent e) { return new SkyDesign(layers, celestial, light, e, render, turning); }

    public SkyDesign withRender(Render r) { return new SkyDesign(layers, celestial, light, extent, r, turning); }

    public SkyDesign withTurning(SkyTurning t) { return new SkyDesign(layers, celestial, light, extent, render, t); }

    /** The design with only what changes textures (the sun and moon), for a texture comparison. */
    public SkyDesign texturesOnly() { return NONE.withCelestial(celestial); }

    /** The design with only what the bake reads, so that a change of the render or the speed of the turning keeps the baked pixels. */
    public SkyDesign baked() { return withRender(Render.SHARP).withTurning(turning.baked()); }

    /** An id that no layer has: one more than the largest. */
    public int nextId() {
        int max = 0;
        for (SkyLayer l : layers) max = Math.max(max, l.id());
        return max + 1;
    }

    /** The index of the layer with this id, or -1. */
    public int indexOf(int id) {
        for (int i = 0; i < layers.size(); i++) if (layers.get(i).id() == id) return i;
        return -1;
    }

    public SkyDesign withLayer(int index, SkyLayer layer) {
        List<SkyLayer> next = new ArrayList<SkyLayer>(layers);
        next.set(index, layer);
        return withLayers(next);
    }

    /** Adds a new layer of {@code kind} at the top. */
    public SkyDesign withAdded(SkyLayer.Kind kind) {
        List<SkyLayer> next = new ArrayList<SkyLayer>(layers);
        next.add(SkyLayer.of(nextId(), kind));
        return withLayers(next);
    }

    /** Removes a layer, and the masks of other layers that use it. */
    public SkyDesign withoutLayer(int index) {
        int gone = layers.get(index).id();
        List<SkyLayer> next = new ArrayList<SkyLayer>();
        for (int i = 0; i < layers.size(); i++) {
            if (i == index) continue;
            SkyLayer l = layers.get(i);
            next.add(l.mask().layer() == gone ? l.withMask(SkyMask.NONE) : l);
        }
        return withLayers(next);
    }

    /**
     * The layer whose mask would name a higher layer after layer {@code index} moves {@code by} (+1 up, -1 down), or null when
     * the move is safe.
     */
    public SkyLayer moveBlocker(int index, int by) {
        int other = index + by;
        if (other < 0 || other >= layers.size()) return null;
        SkyLayer lower = layers.get(Math.min(index, other)), upper = layers.get(Math.max(index, other));
        return upper.mask().layer() == lower.id() ? upper : null;
    }

    /** Swaps a layer with the one above ({@code by} +1) or below (-1); the caller first asks {@link #moveBlocker}. */
    public SkyDesign withMoved(int index, int by) {
        List<SkyLayer> next = new ArrayList<SkyLayer>(layers);
        Collections.swap(next, index, index + by);
        return withLayers(next);
    }

    /** The layers below {@code index}, which are the layers it may use as its mask. */
    public List<SkyLayer> below(int index) {
        return layers.subList(0, Math.max(0, Math.min(index, layers.size())));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SkyDesign)) return false;
        SkyDesign d = (SkyDesign) o;
        return d.celestial == celestial && d.light == light && d.extent == extent && d.render == render && d.turning.equals(turning)
                && d.layers.equals(layers);
    }

    @Override
    public int hashCode() {
        int h = (((layers.hashCode() * 31 + celestial.ordinal()) * 31 + light.ordinal()) * 31 + extent.ordinal()) * 31 + render.ordinal();
        return h * 31 + turning.hashCode();
    }
}
