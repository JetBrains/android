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

import com.android.ide.common.util.AssetUtil;
import com.android.tools.idea.npw.assetstudio.AssetStudioUtils;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconGenerator;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.core.*;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Base class for all asset types which can be converted into Android icons. See also
 * {@link AndroidIconGenerator}, which handles the conversion.
 *
 * Asset fields are all {@link AbstractProperty} instances, which allows for assets to be easily
 * bound to and modified by UI widgets.
 */
@SuppressWarnings("UseJBColor") // Intentionally not using JBColor for Android icons
public abstract class BaseAsset {
  private final BoolProperty myTrimmed = new BoolValueProperty();
  private final IntProperty myPaddingPercent = new IntValueProperty(0);
  private final IntProperty myScalingPercent = new IntValueProperty(100);
  private final ObjectProperty<Color> myColor = new ObjectValueProperty<>(Color.BLACK);
  private final OptionalProperty<Dimension> myTargetSize = new OptionalValueProperty<>();

  /**
   * Whether or not transparent space should be removed from the asset before rendering.
   */
  @NotNull
  public BoolProperty trimmed() {
    return myTrimmed;
  }

  /**
   * A percentage of padding (transparent space) to add around the asset before rendering.
   *
   * Expected values are between -10 (zoomed in enough to clip some of the asset's edges) and 50
   * (zoomed out so that the image is half size and centered).
   */
  @NotNull
  public IntProperty paddingPercent() {
    return myPaddingPercent;
  }

  @NotNull
  public IntProperty scalingPercent() {
    return myScalingPercent;
  }

  public OptionalProperty<Dimension> targetSize() {
    return myTargetSize;
  }

  /**
   * A color to use when rendering this image. Not all asset types are affected by this color.
   */
  @NotNull
  public ObjectProperty<Color> color() {
    return myColor;
  }

  /**
   * Returns the image represented by this asset (this will be an empty image if the asset is not
   * in a valid state for generating the image).
   */
  @NotNull
  public final BufferedImage toImage() {
    BufferedImage image = createAsImage(myColor.get());
    if (myTrimmed.get()) {
      image = AssetStudioUtils.trim(image);
    }
    if (myPaddingPercent.get() != 0) {
      image = AssetStudioUtils.pad(image, myPaddingPercent.get());
    }
    if (myScalingPercent.get() != 100) {
      image = AssetUtil.scaledImage(image,
                                    image.getWidth() * myScalingPercent.get() / 100,
                                    image.getHeight() * myScalingPercent.get() / 100);
    }

    // Set image size to target size
    Dimension imageTargetSize = targetSize().getValueOrNull();
    if (imageTargetSize != null && !Objects.equals(imageTargetSize, new Dimension(image.getWidth(), image.getHeight()))) {
      BufferedImage source = image;
      image = AssetUtil.newArgbBufferedImage(imageTargetSize.width, imageTargetSize.height);
      Graphics2D gImage = (Graphics2D)image.getGraphics();
      AssetUtil.drawCentered(gImage, source, new Rectangle(0, 0, image.getWidth(), image.getHeight()));
      gImage.dispose();
    }

    return image;
  }

  @NotNull
  protected abstract BufferedImage createAsImage(@NotNull Color color);
}
