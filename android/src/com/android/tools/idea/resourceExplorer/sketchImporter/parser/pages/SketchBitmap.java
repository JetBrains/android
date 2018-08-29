/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages;

import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchLayer;
import java.awt.Rectangle;
import org.jetbrains.annotations.NotNull;

/**
 * {@link SketchLayer} that mimics the JSON element with attribute <code>"_class": "bitmap"</code> contained within a sketch file.
 *
 * @see com.android.tools.idea.resourceExplorer.sketchImporter.parser.deserializers.SketchLayerDeserializer
 */
public class SketchBitmap extends SketchLayer {
  private final SketchStyle style;
  private final SketchFileReference image;

  public SketchBitmap(@NotNull String classType,
                      @NotNull String objectId,
                      int booleanOperation,
                      @NotNull SketchExportOptions exportOptions,
                      @NotNull Rectangle.Double frame,
                      boolean isFlippedHorizontal,
                      boolean isFlippedVertical,
                      boolean isVisible,
                      @NotNull String name,
                      int rotation,
                      boolean shouldBreakMaskChain,
                      @NotNull SketchStyle style,
                      @NotNull SketchFileReference image) {
    super(classType, objectId, booleanOperation, exportOptions, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain);
    this.style = style;
    this.image = image;
  }

  @NotNull
  public SketchStyle getStyle() {
    return style;
  }

  @NotNull
  public SketchFileReference getImage() {
    return image;
  }
}
