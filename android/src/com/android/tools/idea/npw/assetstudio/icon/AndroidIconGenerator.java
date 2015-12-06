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

import com.android.assetstudiolib.GraphicGenerator;
import com.android.tools.idea.npw.assetstudio.AssetStudioAssetGenerator;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.ui.properties.core.OptionalProperty;
import com.android.tools.idea.ui.properties.core.OptionalValueProperty;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

/**
 * Helper class which handles the logic of using an {@link AssetStudioAssetGenerator} to generate
 * some target icons.
 */
public abstract class AndroidIconGenerator {
  private final OptionalProperty<BaseAsset> mySourceAsset = new OptionalValueProperty<BaseAsset>();
  private final StringProperty myName = new StringValueProperty();

  @NotNull
  public final OptionalProperty<BaseAsset> sourceAsset() {
    return mySourceAsset;
  }

  @NotNull
  public final StringProperty name() {
    return myName;
  }

  /**
   * Generate icons into a map in memory. This is useful for generating previews.
   *
   * A source asset must be set prior to calling this method or an exception will be thrown.
   */
  @NotNull
  public final CategoryIconMap generateIntoMemory() {
    if (!mySourceAsset.get().isPresent()) {
      throw new IllegalStateException("Can't generate icons without a source asset set first");
    }

    final Map<String, Map<String, BufferedImage>> categoryMap = AssetStudioAssetGenerator.newAssetMap();
    AssetStudioAssetGenerator assetGenerator = new AssetStudioAssetGenerator();
    GraphicGenerator graphicGenerator = createGenerator();
    GraphicGenerator.Options options = createOptions(mySourceAsset.getValue());
    graphicGenerator.generate(null, categoryMap, assetGenerator, options, myName.get());

    return new CategoryIconMap(categoryMap);
  }

  /**
   * Like {@link #generateIntoMemory()} but returned in a format where it's easy to see which files
   * will be created / overwritten if {@link #generateIntoPath(AndroidProjectPaths)} is called.
   *
   * A source asset and name must both be set prior to calling this method or an exception will be
   * thrown.
   */
  @NotNull
  public final Map<File, BufferedImage> generateIntoFileMap(@NotNull AndroidProjectPaths paths) {
    if (myName.get().isEmpty()) {
      throw new IllegalStateException("Can't save icons to disk if a filename isn't set first");
    }

    File resDirectory = paths.getResDirectory();
    if (resDirectory == null || resDirectory.getParentFile() == null) {
      throw new IllegalArgumentException("Invalid paths used when trying to generate an icon");
    }

    CategoryIconMap categoryIconMap = generateIntoMemory();
    return categoryIconMap.toFileMap(resDirectory.getParentFile());
  }

  /**
   * Generate icons into the target path.
   *
   * A source asset and name must both be set prior to calling this method or an exception will be
   * thrown.
   *
   * This method must be called from within a WriteAction.
   */
  public final void generateIntoPath(@NotNull AndroidProjectPaths paths) {
    AssetStudioAssetGenerator assetGenerator = new AssetStudioAssetGenerator();
    assetGenerator.generateIconsIntoPath(generateIntoFileMap(paths));
  }

  @NotNull
  protected abstract GraphicGenerator createGenerator();

  @NotNull
  protected abstract GraphicGenerator.Options createOptions(@NotNull Class<? extends BaseAsset> assetType);

  @NotNull
  private GraphicGenerator.Options createOptions(@NotNull BaseAsset baseAsset) {
    // TODO: Pass in minSdk value into options and generate only what's needed?
    GraphicGenerator.Options options = createOptions(baseAsset.getClass());
    options.sourceImage = baseAsset.toImage();
    return options;
  }
}
