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

import java.awt.Color;
import org.jetbrains.annotations.NotNull;

/**
 * {@link AssetModel} corresponding to a Color imported from Sketch into Android Studio.
 */
public class ColorAssetModel implements AssetModel {
  private boolean myExportable;
  private String myName;
  private Color myColor;
  private Origin myOrigin;

  public ColorAssetModel(boolean exportable,
                         @NotNull String name,
                         @NotNull Color color,
                         @NotNull Origin origin) {
    myExportable = exportable;
    myName = name.replaceAll("[ :\\\\/*\"?|<>%.']", "_");  // TODO use a different sanitizer
    myColor = color;
    myOrigin = origin;
  }

  @Override
  public boolean isExportable() {
    return myExportable;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public void setName(@NotNull String name) {
    myName = name;
  }

  @Override
  @NotNull
  public Origin getOrigin() {
    return myOrigin;
  }

  @NotNull
  public Color getColor() {
    return myColor;
  }
}
