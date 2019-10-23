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
 * Mimics the JSON element with attribute <code>"_class": "exportFormat"</code> contained within a sketch file.
 */
public class SketchExportFormat {
  public static final int NAMING_SCHEME_SUFFIX = 0;
  public static final int NAMING_SCHEME_PREFIX = 1;

  private final String name;
  private final int namingScheme;
  private final float scale;

  public SketchExportFormat(@NotNull String name, int scheme, float scale) {
    this.name = name;
    namingScheme = scheme;
    this.scale = scale;
  }

  @NotNull
  public String getName() {
    return name;
  }

  public int getNamingScheme() {
    return namingScheme;
  }

  public float getScale() {
    return scale;
  }
}
