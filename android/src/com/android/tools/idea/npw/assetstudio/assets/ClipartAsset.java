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

import com.android.SdkConstants;
import com.android.assetstudiolib.GraphicGenerator;
import com.android.ide.common.util.AssetUtil;
import com.android.tools.idea.npw.RasterAssetSetStep;
import com.android.tools.idea.npw.assetstudio.AssetStudioAssetGenerator;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.google.common.collect.ImmutableList;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * An asset which represents a source clipart file.
 */
public final class ClipartAsset extends BaseAsset {
  private static final String PREFERRED_CLIPART = "android.png";
  private final StringProperty myClipartName = new StringValueProperty();

  public ClipartAsset() {
    List<String> clipartNames = getAllClipartNames();
    assert clipartNames.size() > 0;
    if (clipartNames.contains(PREFERRED_CLIPART)) {
      myClipartName.set(PREFERRED_CLIPART);
    }
    else {
      myClipartName.set(clipartNames.get(0));
    }
  }

  /**
   * Return a list of all available names that identify clipart resources.
   */
  @NotNull
  public static List<String> getAllClipartNames() {
    ImmutableList.Builder<String> listBuilder = new ImmutableList.Builder<String>();
    listBuilder.addAll(GraphicGenerator.getResourcesNames(RasterAssetSetStep.IMAGES_CLIPART_BIG, SdkConstants.DOT_PNG));
    return listBuilder.build();
  }

  /**
   * Return the target clipart as a 32x32 icon. Use {@link #getAllClipartNames()} for a list of
   * valid options.
   *
   * @throws IOException if the image cannot be loaded.
   */
  @NotNull
  public static Icon createIcon(@NotNull String clipartName) throws IOException {
    return IconUtil.createImageIcon(GraphicGenerator.getClipartIcon(clipartName));
  }

  /**
   * Return a high res version of the clipart target. Use {@link #getAllClipartNames()} for a list
   * of valid options.
   *
   * @throws IOException if the image cannot be loaded.
   */
  @NotNull
  public static BufferedImage createImage(@NotNull String clipartName) throws IOException {
    return GraphicGenerator.getClipartImage(clipartName);
  }

  /**
   * The name which identifies this clipart asset. Use {@link #getAllClipartNames()} to get a list
   * of all suitable values for this property.
   */
  @NotNull
  public StringProperty clipartName() {
    return myClipartName;
  }

  /**
   * Like {@link #createIcon(String)} but using this asset's name.
   */
  @NotNull
  public Icon createIcon() throws IOException {
    return createIcon(myClipartName.get());
  }

  /**
   * Like {@link #createImage(String)} but using this asset's name.
   */
  @NotNull
  public BufferedImage createImage() throws IOException {
    return createImage(myClipartName.get());
  }

  @NotNull
  @Override
  protected BufferedImage createAsImage(@NotNull Color color) {
    try {
      BufferedImage image = createImage();
      return AssetUtil.filledImage(image, color);
    }
    catch (IOException e) {
      return AssetStudioAssetGenerator.createDummyImage();
    }
  }
}
