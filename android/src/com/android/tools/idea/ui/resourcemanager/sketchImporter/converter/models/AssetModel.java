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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models;

import org.jetbrains.annotations.NotNull;

/**
 * Interface that offers ways to access/change options or characteristics which are common between different types of assets.
 **/
public interface AssetModel {
  boolean isExportable();

  @NotNull
  String getName();

  void setName(@NotNull String name);

  @NotNull
  Origin getOrigin();

  enum Origin {
    ARTBOARD,  // drawable obtained from an artboard
    SYMBOL,    // drawable obtained from a symbol
    SLICE,     // drawable obtained from a slice
    SHAPE,     // color obtained from a shape with solid fill
    DOCUMENT,  // color defined in the document
    SHARED,    // drawable from a shared symbol / color from a shared style
    EXTERNAL   // drawable from an external symbol / color from an external style
  }
}
