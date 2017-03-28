/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.assetstudiolib.AdaptiveIconGenerator;
import com.android.assetstudiolib.GraphicGenerator;
import com.android.resources.Density;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.ui.properties.core.*;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Settings when generating a launcher icon.
 *
 * Defaults from https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html
 */
@SuppressWarnings("UseJBColor") // We are generating colors in our icons, no need for JBColor here
public final class AndroidAdaptiveIconGenerator extends AndroidIconGenerator {

  private final BoolProperty myUseForegroundColor = new BoolValueProperty(true);
  private final ObjectProperty<Color> myForegroundColor = new ObjectValueProperty<>(Color.BLACK);
  private final ObjectProperty<Color> myBackgroundColor = new ObjectValueProperty<>(Color.WHITE);
  private final BoolProperty myForegroundCropped = new BoolValueProperty();
  private final BoolProperty myBackgroundCropped = new BoolValueProperty();
  private final ObjectProperty<GraphicGenerator.Shape> myLegacyShape = new ObjectValueProperty<>(GraphicGenerator.Shape.SQUARE);
  private final BoolProperty myShowGrid = new BoolValueProperty();
  private final BoolProperty myShowSafeZone = new BoolValueProperty(true);
  private final ObjectValueProperty<Density> myPreviewDensity = new ObjectValueProperty<>(Density.XHIGH);
  private final OptionalProperty<ImageAsset> myBackgroundImageAsset = new OptionalValueProperty<>();
  private final StringProperty myForegroundLayerName = new StringValueProperty();
  private final StringProperty myBackgroundLayerName = new StringValueProperty();

  public AndroidAdaptiveIconGenerator(int minSdkVersion) {
    super(minSdkVersion);
  }

  /**
   * Whether to use the foreground color. When using images as the source asset for our icons,
   * you shouldn't apply the foreground color, which would paint over it and obscure the image.
   */
  @NotNull
  public BoolProperty useForegroundColor() {
    return myUseForegroundColor;
  }

  /**
   * A color for rendering the foreground icon.
   */
  @NotNull
  public ObjectProperty<Color> foregroundColor() {
    return myForegroundColor;
  }

  /**
   * A color for rendering the background shape.
   */
  @NotNull
  public ObjectProperty<Color> backgroundColor() {
    return myBackgroundColor;
  }

  /**
   * If {@code true}, any extra part of the source asset that doesn't fit on the final icon will
   * be cropped. Otherwise, the source asset will be shrunk to fit.
   */
  @NotNull
  public BoolProperty foregroundCropped() {
    return myForegroundCropped;
  }

  /**
   * If {@code true}, any extra part of the source asset that doesn't fit on the final icon will
   * be cropped. Otherwise, the source asset will be shrunk to fit.
   */
  @NotNull
  public BoolProperty backgroundCropped() {
    return myBackgroundCropped;
  }

  /**
   * A shape which will be used as the legacy icon's backdrop.
   */
  @NotNull
  public ObjectProperty<GraphicGenerator.Shape> legacyShape() {
    return myLegacyShape;
  }

  @NotNull
  public OptionalProperty<ImageAsset> backgroundImageAsset() {
    return myBackgroundImageAsset;
  }

  @NotNull
  public BoolProperty showGrid() {
    return myShowGrid;
  }

  @NotNull
  public BoolProperty showSafeZone() {
    return myShowSafeZone;
  }

  @NotNull
  public ObjectValueProperty<Density> previewDensity() {
    return myPreviewDensity;
  }

  @NotNull
  public StringProperty foregroundLayerName() {
    return myForegroundLayerName;
  }

  @NotNull
  public StringProperty backgroundLayerName() {
    return myBackgroundLayerName;
  }

  @NotNull
  @Override
  protected GraphicGenerator createGenerator() {
    return new AdaptiveIconGenerator();
  }

  @NotNull
  @Override
  protected GraphicGenerator.Options createOptions(@NotNull Class<? extends BaseAsset> assetType) {
    AdaptiveIconGenerator.AdaptiveIconOptions launcherOptions = new AdaptiveIconGenerator.AdaptiveIconOptions();
    launcherOptions.legacyShape = myLegacyShape.get();
    launcherOptions.cropForeground = myForegroundCropped.get();
    launcherOptions.cropBackground = myBackgroundCropped.get();
    launcherOptions.useForegroundColor = myUseForegroundColor.get();
    launcherOptions.foregroundColor = myForegroundColor.get().getRGB();
    if (myBackgroundImageAsset.getValueOrNull() == null) {
      launcherOptions.backgroundImage = null;
    } else {
      launcherOptions.backgroundImage = myBackgroundImageAsset.getValueOrNull().toImage();
    }
    launcherOptions.backgroundColor = myBackgroundColor.get().getRGB();
    launcherOptions.isWebGraphic = true;
    launcherOptions.showGrid = myShowGrid.get();
    launcherOptions.showSafeZone = myShowSafeZone.get();
    launcherOptions.previewDensity = myPreviewDensity.get();
    launcherOptions.foregroundLayerName = myForegroundLayerName.get();
    launcherOptions.backgroundLayerName = myBackgroundLayerName.get();

    return launcherOptions;
  }
}
