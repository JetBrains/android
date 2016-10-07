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
import com.android.tools.idea.ui.properties.core.ObjectProperty;
import com.android.tools.idea.ui.properties.core.ObjectValueProperty;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * An asset that represents an image on disk.
 */
public final class ImageAsset extends BaseAsset {

  @NotNull private final ObjectProperty<File> myImagePath;

  public ImageAsset() {
    String pathToSampleImageInTemplate =
      FileUtil.join(Template.CATEGORY_PROJECTS, "NewAndroidModule", "root", "res", "mipmap-xxxhdpi", "ic_launcher.png");

    myImagePath = new ObjectValueProperty<>(new File(TemplateManager.getTemplateRootFolder(), pathToSampleImageInTemplate));
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
    BufferedImage image = null;
    try {
      image = ImageIO.read(myImagePath.get());
    }
    catch (IOException ignored) {
    }

    if (image == null) {
      image = AssetStudioUtils.createDummyImage();
    }

    return image;
  }
}
