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
package com.android.tools.idea.npw.assetstudio.assets;

import com.android.tools.idea.npw.assetstudio.AssetStudioUtils;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Function;

/**
 * An asset that represents an image on disk.
 */
public final class ImageAsset extends BaseAsset {
  @NotNull private final ObjectProperty<File> myImagePath;
  @Nullable private BufferedImage myImage;
  @Nullable private Function<BufferedImage, BufferedImage> myImageImporter;

  public ImageAsset() {
    myImagePath = new ObjectValueProperty<>(getTemplateImage("ic_launcher.png"));
    myImagePath.addListener(this::pathChanged);
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  public static File getTemplateImage(@NotNull String fileName) {
    String pathToSampleImageInTemplate =
      FileUtil.join(Template.CATEGORY_PROJECTS, "NewAndroidModule", "root", "res", "mipmap-xxxhdpi", fileName);
    return new File(TemplateManager.getTemplateRootFolder(), pathToSampleImageInTemplate);
  }

  public void setImageImporter(@Nullable Function<BufferedImage, BufferedImage> imageImporter) {
    myImageImporter = imageImporter;
  }

  private void pathChanged(ObservableValue<?> value) {
    loadImage();
  }

  private void loadImage() {
    myImage = null;
    File imageFile = myImagePath.get();
    try {
      myImage = ImageIO.read(imageFile);
    }
    catch (IOException ignored) {
      Logger.getInstance(this.getClass()).warn(String.format("Error loading image %s", imageFile), ignored);
    }

    if (myImage == null) {
      myImage = AssetStudioUtils.createDummyImage();
    }
    else {
      if (myImageImporter != null) {
        myImage = myImageImporter.apply(myImage);
      }
    }
  }

  /**
   * The path to the image asset.
   */
  @NotNull
  public ObjectProperty<File> imagePath() {
    return myImagePath;
  }

  @NotNull
  @Override
  protected BufferedImage createAsImage(@NotNull Color color) {
    if (myImage == null) {
      loadImage();
    }
    return myImage;
  }
}
