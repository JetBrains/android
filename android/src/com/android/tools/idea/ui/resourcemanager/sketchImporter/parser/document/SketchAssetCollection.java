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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchGradient;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchGradient;
import java.awt.Color;
import org.jetbrains.annotations.NotNull;

public class SketchAssetCollection {
  private final Color[] colors;
  private final SketchGradient[] gradients;
  // TODO imageCollection, images?

  public SketchAssetCollection(@NotNull Color[] colors, @NotNull SketchGradient[] gradients) {
    this.colors = colors;
    this.gradients = gradients;
  }

  @NotNull
  public Color[] getColors() {
    return colors;
  }

  @NotNull
  public SketchGradient[] getGradients() {
    return gradients;
  }
}
