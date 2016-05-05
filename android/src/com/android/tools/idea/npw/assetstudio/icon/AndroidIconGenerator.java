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
import com.android.tools.idea.npw.assetstudio.AssetStudioGraphicGeneratorContext;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.ui.properties.core.OptionalProperty;
import com.android.tools.idea.ui.properties.core.OptionalValueProperty;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * Helper class which handles the logic of generating some target icons given a {@link BaseAsset}.
 */
public abstract class AndroidIconGenerator {
  private final OptionalProperty<BaseAsset> mySourceAsset = new OptionalValueProperty<>();
  private final StringProperty myName = new StringValueProperty();

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(AndroidIconGenerator.class);
  }

  @NotNull
  private static Map<String, Map<String, BufferedImage>> newAssetMap() {
    return Maps.newHashMap();
  }


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
   * {@link #sourceAsset()} must both be set prior to calling this method or an exception will be
   * thrown.
   */
  @NotNull
  public final CategoryIconMap generateIntoMemory() {
    if (!mySourceAsset.get().isPresent()) {
      throw new IllegalStateException("Can't generate icons without a source asset set first");
    }

    final Map<String, Map<String, BufferedImage>> categoryMap = newAssetMap();
    AssetStudioGraphicGeneratorContext context = new AssetStudioGraphicGeneratorContext();
    GraphicGenerator graphicGenerator = createGenerator();
    GraphicGenerator.Options options = createOptions(mySourceAsset.getValue());
    graphicGenerator.generate(null, categoryMap, context, options, myName.get());

    return new CategoryIconMap(categoryMap);
  }

  /**
   * Like {@link #generateIntoMemory()} but returned in a format where it's easy to see which files
   * will be created / overwritten if {@link #generateImageIconsIntoPath(AndroidProjectPaths)} is called.
   *
   * {@link #sourceAsset()} and {@link #name()} must both be set prior to calling this method or
   * an exception will be thrown.
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
   * Generate png icons into the target path.
   *
   * {@link #sourceAsset()} and {@link #name()} must both be set prior to calling this method or
   * an exception will be thrown.
   *
   * This method must be called from within a WriteAction.
   */
  public final void generateImageIconsIntoPath(@NotNull AndroidProjectPaths paths) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    Map<File, BufferedImage> pathIconMap = generateIntoFileMap(paths);

    for (Map.Entry<File, BufferedImage> fileImageEntry : pathIconMap.entrySet()) {
      File file = fileImageEntry.getKey();
      BufferedImage image = fileImageEntry.getValue();

      if (FileUtilRt.extensionEquals(file.getName(), "png")) {
        writePngToDisk(file, image);
      }
      else {
        getLog().error("Please report this error. Unable to create icon for invalid file: " + file.getAbsolutePath());
      }
    }
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

  private void writePngToDisk(@NotNull File file, @NotNull BufferedImage image) {
    try {
      VirtualFile directory = VfsUtil.createDirectories(file.getParentFile().getAbsolutePath());
      VirtualFile imageFile = directory.findChild(file.getName());
      if (imageFile == null || !imageFile.exists()) {
        imageFile = directory.createChildData(this, file.getName());
      }
      OutputStream outputStream = imageFile.getOutputStream(this);
      try {
        ImageIO.write(image, "PNG", outputStream);
      }
      finally {
        outputStream.close();
      }
    }
    catch (IOException e) {
      getLog().error(e);
    }
  }
}
