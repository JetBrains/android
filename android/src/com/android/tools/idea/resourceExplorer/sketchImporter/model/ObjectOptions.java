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

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.interfaces.SketchLayer;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for options assigned to a page/icon/color/text etc.
 */
public abstract class ObjectOptions {
  protected String myName;
  private boolean myExportable;

  protected ObjectOptions(@NotNull String name, boolean isExportable) {
    myName = name;
    myExportable = isExportable;
  }

  /**
   * By default, an item is exportable if it has <b>at least one exportFormat</b> (regardless of
   * the specifics of the format -> users can mark an item as exportable in Sketch with one click).
   */
  protected ObjectOptions(@NotNull SketchLayer sketchLayer) {
    myExportable = sketchLayer.getExportOptions().getExportFormats().length != 0;
  }

  public boolean isExportable() {
    return myExportable;
  }

  @NotNull
  public String getName() {
    return myName;
  }
}