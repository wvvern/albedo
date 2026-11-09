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

    public static final SkyDesign NONE = new SkyDesign(Collections.<SkyLayer>emptyList(), Celestial.SHOW, Light.FIXED);

    private final List<SkyLayer> layers;
    private final Celestial celestial;
    private final Light light;

    public SkyDesign(List<SkyLayer> layers, Celestial celestial, Light light) {
        this.layers = Collections.unmodifiableList(new ArrayList<SkyLayer>(layers));
        this.celestial = celestial;
        this.light = light;
    }

    /** From the bottom layer up. */
    public List<SkyLayer> layers() { return layers; }

    public Celestial celestial() { return celestial; }

    public Light light() { return light; }

    public boolean isEmpty() { return layers.isEmpty(); }

    public boolean isFull() { return layers.size() >= MAX_LAYERS; }

    public SkyDesign withLayers(List<SkyLayer> next) { return new SkyDesign(next, celestial, light); }

    public SkyDesign withCelestial(Celestial c) { return new SkyDesign(layers, c, light); }

    public SkyDesign withLight(Light l) { return new SkyDesign(layers, celestial, l); }

    /** The design with only what changes textures (the sun and moon), for a texture comparison. */
    public SkyDesign texturesOnly() { return NONE.withCelestial(celestial); }

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
        return d.celestial == celestial && d.light == light && d.layers.equals(layers);
    }

    @Override
    public int hashCode() { return (layers.hashCode() * 31 + celestial.ordinal()) * 31 + light.ordinal(); }
}
