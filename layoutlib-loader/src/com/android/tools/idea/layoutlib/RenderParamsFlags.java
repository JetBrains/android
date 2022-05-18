/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.layoutlib;

import com.android.ide.common.rendering.api.IImageFactory;
import com.android.ide.common.rendering.api.RenderParams;
import com.android.ide.common.rendering.api.SessionParams.Key;

/**
 * This contains all known keys for the {@link RenderParams#setFlag(Key, Object)}.
 * <p>
 * LayoutLib has its own copy of this class which may be newer or older than this one.
 * <p>
 * Constants should never be modified or removed from this class.
 */
@SuppressWarnings("unused")
public final class RenderParamsFlags {

    public static final Key<String> FLAG_KEY_ROOT_TAG =
        new Key<String>("rootTag", String.class);
    public static final Key<Boolean> FLAG_KEY_DISABLE_BITMAP_CACHING =
        new Key<Boolean>("disableBitmapCaching", Boolean.class);
    public static final Key<Boolean> FLAG_KEY_RENDER_ALL_DRAWABLE_STATES =
        new Key<Boolean>("renderAllDrawableStates", Boolean.class);

    /**
     * To tell LayoutLib to not render when creating a new session. This allows controlling when the first
     * layout rendering will happen.
     */
    public static final Key<Boolean> FLAG_DO_NOT_RENDER_ON_CREATE =
        new Key<Boolean>("doNotRenderOnCreate", Boolean.class);
    /**
     * To tell Layoutlib which path to use for the adaptive icon mask.
     */
    public static final Key<String> FLAG_KEY_ADAPTIVE_ICON_MASK_PATH =
      new Key<>("adaptiveIconMaskPath", String.class);

    /**
     * When enabled, Layoutlib will resize the output image to whatever size
     * is returned by {@link IImageFactory#getImage(int, int)}. The default
     * behaviour when this is false is to crop the image to the size of the image
     * returned by {@link IImageFactory#getImage(int, int)}.
     */
    public static final Key<Boolean> FLAG_KEY_RESULT_IMAGE_AUTO_SCALE =
      new Key<Boolean>("enableResultImageAutoScale", Boolean.class);

    /**
     * Enables layout validation calls within rendering.
     * Name differs as it used to be called layout validator.
     */
    public static final Key<Boolean> FLAG_KEY_ENABLE_LAYOUT_SCANNER =
      new Key<>("enableLayoutValidator", Boolean.class);

    /**
     * Enables image-related validation checks within layout validation.
     * {@link #FLAG_KEY_ENABLE_LAYOUT_SCANNER} must be enabled before this can be effective.
     */
    public static final Key<Boolean> FLAG_ENABLE_LAYOUT_SCANNER_IMAGE_CHECK =
      new Key<>("enableLayoutValidatorImageCheck", Boolean.class);

    /**
     * To tell Layoutlib the path of the image resource of the wallpaper to use for dynamic theming.
     * If null, use default system colors.
     */
    public static final Key<String> FLAG_KEY_WALLPAPER_PATH =
      new Key<>("wallpaperPath", String.class);

    /**
     * To tell Layoutlib to use the themed version of adaptive icons.
     */
    public static final Key<Boolean> FLAG_KEY_USE_THEMED_ICON =
      new Key<>("useThemedIcon", Boolean.class);

    // Disallow instances.
    private RenderParamsFlags() {}
}
