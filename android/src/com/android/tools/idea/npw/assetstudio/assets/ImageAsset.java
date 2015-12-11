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

import com.android.tools.idea.npw.assetstudio.AssetStudioAssetGenerator;
import com.android.tools.idea.ui.properties.core.ObjectProperty;
import com.android.tools.idea.ui.properties.core.ObjectValueProperty;
import com.google.common.base.Strings;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * An asset that represents an image on disk.
 */
public final class ImageAsset extends BaseAsset {

  private final ObjectProperty<File> myImagePath =
    new ObjectValueProperty<File>(new File(Strings.nullToEmpty(System.getProperty("user.home"))));

  /**
   * The path to the image asset.
   */
  public ObjectProperty<File> imagePath() {
    return myImagePath;
  }

  @NotNull
  @Override
  protected BufferedImage createAsImage() {
    BufferedImage image = null;
    try {
      image = ImageIO.read(myImagePath.get());
    }
    catch (IOException ignored) {
    }

    if (image == null) {
      image = AssetStudioAssetGenerator.createDummyImage();
    }

    return image;
  }
}
