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

import org.jetbrains.annotations.NotNull;

/**
 * Mimics the JSON element with attribute <code>"_class": "exportOptions"</code> contained within a sketch file.
 */
public class SketchExportOptions {
  private final SketchExportFormat[] exportFormats;
  // Class has more parameters in the JSON file but their use is still unknown

  public SketchExportOptions(@NotNull SketchExportFormat[] formats) {
    exportFormats = formats;
  }

  @NotNull
  public SketchExportFormat[] getExportFormats() {
    return exportFormats;
  }
}
