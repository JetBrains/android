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

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

public class StudioResourcesModel {
  private ImmutableList<DrawableAssetModel> myDrawableAssets;
  private ImmutableList<ColorAssetModel> myColorAssets;

  public StudioResourcesModel(@Nullable ImmutableList<DrawableAssetModel> drawableAssets,
                              @Nullable ImmutableList<ColorAssetModel> colorAssets) {
    myDrawableAssets = drawableAssets;
    myColorAssets = colorAssets;
  }

  @Nullable
  public ImmutableList<DrawableAssetModel> getDrawableAssets() {
    return myDrawableAssets;
  }

  @Nullable
  public ImmutableList<ColorAssetModel> getColorAssets() {
    return myColorAssets;
  }
}
