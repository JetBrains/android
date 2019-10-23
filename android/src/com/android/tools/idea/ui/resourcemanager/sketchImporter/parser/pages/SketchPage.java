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

import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ResizingConstraint;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayerable;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ResizingConstraint;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.deserializers.SketchLayerDeserializer;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayerable;
import java.awt.Rectangle;
import org.jetbrains.annotations.NotNull;

/**
 * {@link SketchLayer} that mimics the JSON element with attribute <code>"_class"</code> set to be one of the following:
 * <ul>
 * <li><code>"page"</code></li>
 * <li><code>"group"</code></li>
 * </ul>
 * It contains other layers, so it is a {@link SketchLayerable}.
 *
 * @see SketchLayerDeserializer
 */
public class SketchPage extends SketchLayer implements SketchLayerable {
  SketchStyle style;
  SketchLayer[] layers;

  public SketchPage(@NotNull String classType,
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
                    @NotNull SketchLayer[] layers,
                    @NotNull ResizingConstraint constraint) {
    super(classType, objectId, booleanOperation, exportOptions, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain, constraint);

    this.style = style;
    this.layers = layers;
  }

  @Override
  @NotNull
  public SketchStyle getStyle() {
    return style;
  }

  @Override
  @NotNull
  public SketchLayer[] getLayers() {
    return layers;
  }
}
