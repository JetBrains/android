/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchSharedStyle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Mimics the JSON element with attribute <code>"_class": "style"</code> contained within a sketch file.
 */
public class SketchStyle {
  public final SketchGraphicsContextSettings DEFAULT_CONTEXT_SETTINGS = new SketchGraphicsContextSettings((short)0, (short)1);
  private final SketchBorderOptions borderOptions;
  private final SketchBorder[] borders;
  private final SketchColorControls colorControls;
  private final SketchFill[] fills;
  private final short miterLimit;
  private final SketchShadow[] shadows;
  private final short windingRule;
  /**
   * If this field does not exist, the default values are considered:
   * <ul>
   * <li>blendMode: 0</li>
   * <li>opacity: 1</li>
   * </ul>
   */
  private final SketchGraphicsContextSettings contextSettings;
  private final String sharedObjectID;

  public SketchStyle(@Nullable SketchBorderOptions borderOptions,
                     @Nullable SketchBorder[] borders,
                     @Nullable SketchGraphicsContextSettings contextSettings,
                     @Nullable SketchColorControls colorControls,
                     @Nullable SketchFill[] fills,
                     short miterLimit,
                     @Nullable SketchShadow[] shadows,
                     short windingRule,
                     @NotNull String sharedObjectID) {
    this.borderOptions = borderOptions;
    this.borders = borders;
    this.colorControls = colorControls;
    this.contextSettings = contextSettings != null ? contextSettings : DEFAULT_CONTEXT_SETTINGS;
    this.fills = fills;
    this.miterLimit = miterLimit;
    this.shadows = shadows;
    this.windingRule = windingRule;
    this.sharedObjectID = sharedObjectID;
  }

  @Nullable
  public SketchBorderOptions getBorderOptions() {
    return borderOptions;
  }

  @Nullable
  public SketchBorder[] getBorders() {
    return borders;
  }

  @Nullable
  public SketchGraphicsContextSettings getContextSettings() {
    return contextSettings;
  }

  @Nullable
  public SketchColorControls getColorControls() {
    return colorControls;
  }

  @Nullable
  public SketchFill[] getFills() {
    return fills;
  }

  public short getMiterLimit() {
    return miterLimit;
  }

  @Nullable
  public SketchShadow[] getShadows() {
    return shadows;
  }

  public short getWindingRule() {
    return windingRule;
  }

  public void setFill(@NotNull SketchFill fill) {
    if (getFills() != null && getFills().length != 0) {
      getFills()[0] = fill;
    }
  }

  /**
   * Get the ID of the style, which corresponds to {@link SketchSharedStyle#getObjectId()} where this {@link SketchStyle} corresponds to
   * {@link SketchSharedStyle#getValue()}.
   */
  @NotNull
  public String getSharedObjectID() {
    return sharedObjectID;
  }
}
