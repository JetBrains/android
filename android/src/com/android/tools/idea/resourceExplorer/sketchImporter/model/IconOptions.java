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
package com.android.tools.idea.resourceExplorer.sketchImporter.model;

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchArtboard;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchExportFormat;
import org.jetbrains.annotations.NotNull;

/**
 * Class meant to hold the options the user chooses for each individual icon that is imported,
 * i.e. whether they want to hide the background or other things that may come up in the future.
 */
public class IconOptions extends ObjectOptions {

  public IconOptions(@NotNull String name, boolean isExportable) {
    super(name, isExportable);
  }

  public IconOptions(@NotNull SketchArtboard artboard) {
    super(artboard);
    myName = getDefaultName(artboard);
  }

  @NotNull
  private static String getDefaultName(@NotNull SketchArtboard artboard) {
    String name = artboard.getName();

    if (artboard.getExportOptions().getExportFormats().length != 0) {
      SketchExportFormat format = artboard.getExportOptions().getExportFormats()[0];

      if (format.getNamingScheme() == SketchExportFormat.NAMING_SCHEME_PREFIX) {
        return format.getName() + name;
      }
      else if (format.getNamingScheme() == SketchExportFormat.NAMING_SCHEME_SUFFIX) {
        return name + format.getName();
      }
    }

    return name;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }
}