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

import com.android.tools.idea.npw.assetstudio.*;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.intellij.openapi.Disposable;
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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class which handles the logic of generating some target icons given a {@link BaseAsset}.
 */
public abstract class AndroidIconGenerator implements Disposable {
  private final OptionalProperty<BaseAsset> mySourceAsset = new OptionalValueProperty<>();
  private final StringProperty myName = new StringValueProperty();

  private final int myMinSdkVersion;

  @NotNull private final GraphicGeneratorContext myContext;
  @NotNull private final GraphicGenerator myGraphicGenerator;

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param minSdkVersion the minimal supported Android SDK version
   * @param graphicGenerator the graphic generator to use
   */
  public AndroidIconGenerator(int minSdkVersion, @NotNull GraphicGenerator graphicGenerator) {
    this(minSdkVersion, graphicGenerator, new GraphicGeneratorContext(40));
  }

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param minSdkVersion the minimal supported Android SDK version
   * @param graphicGenerator the graphic generator to use
   * @param context the graphic generator context
   */
  public AndroidIconGenerator(int minSdkVersion, @NotNull GraphicGenerator graphicGenerator, @NotNull GraphicGeneratorContext context) {
    myMinSdkVersion = minSdkVersion;
    myContext = context;
    myGraphicGenerator = graphicGenerator;
  }

  @Override
  public void dispose() {
    myContext.dispose();
  }

  @NotNull
  public GraphicGeneratorContext getGraphicGeneratorContext() {
    return myContext;
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(AndroidIconGenerator.class);
  }

  @NotNull
  private static Map<String, Map<String, BufferedImage>> newAssetMap() {
    return new HashMap<>();
  }

  @NotNull
  public final OptionalProperty<BaseAsset> sourceAsset() {
    return mySourceAsset;
  }

  @NotNull
  public final StringProperty name() {
    return myName;
  }

  public int getMinSdkVersion() {
    return myMinSdkVersion;
  }

  @NotNull
  public IconGeneratorResult generateIcons(GraphicGenerator.Options options) {
    return new IconGeneratorResult(myGraphicGenerator.generateIcons(myContext, options, myName.get()), options);
  }

  /**
   * Generates icons into a map in memory. This is useful for generating previews.
   *
   * {@link #sourceAsset()} must both be set prior to calling this method or an exception will be
   * thrown.
   */
  @NotNull
  public final CategoryIconMap generateIntoMemory() {
    if (!mySourceAsset.get().isPresent()) {
      throw new IllegalStateException("Can't generate icons without a source asset set first");
    }

    GraphicGenerator.Options options = createOptions(false);
    return generateIntoMemory(options);
  }

  @NotNull
  private CategoryIconMap generateIntoMemory(GraphicGenerator.Options options) {
    Map<String, Map<String, BufferedImage>> categoryMap = newAssetMap();
    myGraphicGenerator.generate(null, categoryMap, myContext, options, myName.get());
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
   * Like {@link #generateIntoMemory()} but returned in a format where it's easy to see which files
   * will be created / overwritten if {@link #generateImageIconsIntoPath(AndroidProjectPaths)} is called.
   *
   * {@link #sourceAsset()} and {@link #name()} must both be set prior to calling this method or
   * an exception will be thrown.
   */
  @NotNull
  public final Map<File, GeneratedIcon> generateIntoIconMap(@NotNull AndroidProjectPaths paths) {
    GraphicGenerator.Options options = createOptions(false);
    return generateIntoIconMap(paths, options);
  }

  /**
   * Similar to {@link ##generateIntoIconMap(AndroidModuleTemplate)} but instead of generating real icons
   * uses placeholders that are much faster to produce.
   *
   * {@link #sourceAsset()} and {@link #name()} must both be set prior to calling this method or
   * an exception will be thrown.
   */
  @NotNull
  public final Map<File, GeneratedIcon> generateIconPlaceholders(@NotNull AndroidProjectPaths paths) {
    GraphicGenerator.Options options = createOptions(false);
    options.usePlaceholders = true;
    return generateIntoIconMap(paths, options);
  }

  /**
   * Like {@link #generateIntoMemory()} but returned in a format where it's easy to see which files
   * will be created / overwritten if {@link #generateImageIconsIntoPath(AndroidModuleTemplate)} is called.
   *
   * {@link #sourceAsset()} and {@link #name()} must both be set prior to calling this method or
   * an exception will be thrown.
   */
  @NotNull
  public final Map<File, GeneratedIcon> generateIntoIconMap(@NotNull AndroidProjectPaths paths, GraphicGenerator.Options options) {
    if (myName.get().isEmpty()) {
      throw new IllegalStateException("Can't save icons to disk if a filename isn't set first");
    }

    File resDirectory = paths.getResDirectory();
    if (resDirectory == null || resDirectory.getParentFile() == null) {
      throw new IllegalArgumentException("Invalid paths used when trying to generate an icon");
    }

    IconGeneratorResult icons = generateIcons(options);
    Map<File, GeneratedIcon> outputMap = new HashMap<>();
    icons.getIcons().getList().forEach(icon -> {
      if (icon.getOutputPath() != null && icon.getCategory() != IconCategory.PREVIEW) {
        File path = new File(resDirectory.getParentFile(), icon.getOutputPath().toString());
        outputMap.put(path, icon);
      }
    });
    return outputMap;
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
    Map<File, GeneratedIcon> pathIconMap = generateIntoIconMap(paths);

    ApplicationManager.getApplication().runWriteAction(() -> {
      for (Map.Entry<File, GeneratedIcon> fileImageEntry : pathIconMap.entrySet()) {
        File file = fileImageEntry.getKey();
        GeneratedIcon icon = fileImageEntry.getValue();

        if (icon instanceof GeneratedImageIcon) {
          if (FileUtilRt.extensionEquals(file.getName(), "png")) {
            writePngToDisk(file, ((GeneratedImageIcon)icon).getImage());
          }
          else {
            getLog().error("Please report this error. Unable to create icon for invalid file: " + file.getAbsolutePath());
          }
        }
        else if (icon instanceof GeneratedXmlResource) {
          if (FileUtilRt.extensionEquals(file.getName(), "xml")) {
            writeTextToDisk(file, ((GeneratedXmlResource)icon).getXmlText());
          }
          else {
            getLog().error("Please report this error. Unable to create icon for invalid file: " + file.getAbsolutePath());
          }
        }
        else {
          getLog().error("Please report this error. Unable to create icon for invalid file: " + file.getAbsolutePath());
        }
      }
    });
  }

  @NotNull
  public abstract GraphicGenerator.Options createOptions(boolean forPreview);

  private void writePngToDisk(@NotNull File file, @NotNull BufferedImage image) {
    try {
      VirtualFile directory = VfsUtil.createDirectories(file.getParentFile().getAbsolutePath());
      VirtualFile imageFile = directory.findChild(file.getName());
      if (imageFile == null || !imageFile.exists()) {
        imageFile = directory.createChildData(this, file.getName());
      }
      try (OutputStream outputStream = imageFile.getOutputStream(this)) {
        ImageIO.write(image, "PNG", outputStream);
      }
    }
    catch (IOException e) {
      getLog().error(e);
    }
  }

  private void writeTextToDisk(@NotNull File file, @NotNull String text) {
    try {
      VirtualFile directory = VfsUtil.createDirectories(file.getParentFile().getAbsolutePath());
      VirtualFile imageFile = directory.findChild(file.getName());
      if (imageFile == null || !imageFile.exists()) {
        imageFile = directory.createChildData(this, file.getName());
      }
      try (OutputStream outputStream = imageFile.getOutputStream(this)) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        outputStream.write(bytes);
      }
    }
    catch (IOException e) {
      getLog().error(e);
    }
  }
}
