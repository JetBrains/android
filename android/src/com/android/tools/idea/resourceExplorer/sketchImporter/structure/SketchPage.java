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
package com.android.tools.idea.resourceExplorer.sketchImporter.structure;

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.interfaces.SketchLayer;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.interfaces.SketchLayerable;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.ArrayList;

import static com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer.ARTBOARD_CLASS_TYPE;

/**
 * Refers to objects that have the "_class" field set to be one of the following:
 * <ul>
 * <li>"page"</li>
 * <li>"group"</li>
 * </ul>
 * <p>
 * {@link com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer}
 */
public class SketchPage extends SketchLayer implements SketchLayerable {
  SketchStyle style;
  SketchLayer[] layers;

  public SketchPage(@NotNull String classType,
                    @NotNull String objectId,
                    int booleanOperation,
                    @NotNull Rectangle.Double frame,
                    boolean isFlippedHorizontal,
                    boolean isFlippedVertical,
                    boolean isVisible,
                    @NotNull String name,
                    int rotation,
                    boolean shouldBreakMaskChain,
                    @NotNull SketchStyle style,
                    @NotNull SketchLayer[] layers) {
    super(classType, objectId, booleanOperation, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain);

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

  public List<SketchArtboard> getArtboards() {

    ArrayList<SketchArtboard> artboards = new ArrayList<>();

    for (SketchLayer layer : getLayers()) {
      if (ARTBOARD_CLASS_TYPE.equals(layer.getClassType())) {
        artboards.add((SketchArtboard)layer);
      }
    }

    return artboards;
  }

  @Override
  public void setAbsoluteLocation(ArrayList<String> paths, Point2D.Double parentCoords) {
    parentCoords.setLocation(parentCoords.getX() + getFrame().getX(),
                             parentCoords.getY() + getFrame().getY());
    for (SketchLayer groupLayer : this.getLayers()) {
      groupLayer.setAbsoluteLocation(paths, parentCoords);
    }
  }
}
