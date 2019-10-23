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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ResizingConstraint;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchStyle;
import java.awt.geom.Rectangle2D;
import org.jetbrains.annotations.NotNull;

/**
 * Denotes a class that has a style and a list of layers.
 */
public interface SketchLayerable {

  @NotNull
  SketchStyle getStyle();

  @NotNull
  SketchLayer[] getLayers();

  @NotNull
  Rectangle2D.Double getFrame();

  int getRotation();

  boolean isFlippedVertical();

  boolean isFlippedHorizontal();

  @NotNull
  ResizingConstraint getResizingConstraint();
}
