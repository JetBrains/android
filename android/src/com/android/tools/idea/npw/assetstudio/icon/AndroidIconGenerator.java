/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.icon;

import com.android.tools.idea.npw.assetstudio.AssetStudioAssetGenerator;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Helper class which handles the logic of using an {@link AssetStudioAssetGenerator} to generate
 * some target icons.
 */
public final class AndroidIconGenerator {
  @NotNull private final AndroidIconType myIconType;
  @NotNull private final BaseAsset mySourceAsset;
  @NotNull private final String myName;

  public AndroidIconGenerator(@NotNull AndroidIconType iconType, @NotNull BaseAsset sourceAsset, @NotNull String name) {
    myIconType = iconType;
    mySourceAsset = sourceAsset;
    myName = name;
  }

  public void generate(@NotNull AndroidProjectPaths paths) {
    File resDirectory = paths.getResDirectory();
    if (resDirectory == null || resDirectory.getParentFile() == null) {
      return;
    }
    AssetStudioAssetGenerator assetGenerator = new AssetStudioAssetGenerator();
    assetGenerator.generateIconsIntoPath(resDirectory.getParentFile(), myIconType, mySourceAsset, myName);
  }
}
