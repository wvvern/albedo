package dev.albedo.core.ui;

/** What the preview of a layer page shows. */
enum SkyPreviewSource {
    /** The full sky. */
    RESULT,
    /** This layer alone, also a layer that is only a mask, over the layers below for a filter. */
    THIS_LAYER
}
