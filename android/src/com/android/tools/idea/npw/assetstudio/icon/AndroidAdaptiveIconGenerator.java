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

import com.android.tools.idea.npw.assetstudio.AdaptiveIconGenerator;
import com.android.tools.idea.npw.assetstudio.GraphicGenerator;
import com.android.resources.Density;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.observable.core.*;
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
  private final BoolProperty myGenerateLegacyIcon = new BoolValueProperty();
  private final BoolProperty myGenerateRoundIcon = new BoolValueProperty();
  private final BoolProperty myGenerateWebIcon = new BoolValueProperty();
  private final ObjectProperty<GraphicGenerator.Shape> myLegacyIconShape = new ObjectValueProperty<>(GraphicGenerator.Shape.SQUARE);
  private final ObjectProperty<GraphicGenerator.Shape> myWebIconShape = new ObjectValueProperty<>(GraphicGenerator.Shape.SQUARE);
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
   * If {@code true}, generate the "Legacy" icon (API 24 and earlier)
   */
  @NotNull
  public BoolProperty generateLegacyIcon() {
    return myGenerateLegacyIcon;
  }

  /**
   * If {@code true}, generate the "Round" icon (API 25)
   */
  @NotNull
  public BoolProperty generateRoundIcon() {
    return myGenerateRoundIcon;
  }

  /**
   * If {@code true}, generate the "Web" icon for PlayStore
   */
  @NotNull
  public BoolProperty generateWebIcon() {
    return myGenerateWebIcon;
  }

  /**
   * A shape which will be used as the "Legacy" icon's backdrop.
   */
  @NotNull
  public ObjectProperty<GraphicGenerator.Shape> legacyIconShape() {
    return myLegacyIconShape;
  }

  /**
   * A shape which will be used as the "Web" icon's backdrop.
   */
  @NotNull
  public ObjectProperty<GraphicGenerator.Shape> webIconShape() {
    return myWebIconShape;
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
    AdaptiveIconGenerator.AdaptiveIconOptions options = createOptions();
    options.generateOutputIcons = true;
    options.generatePreviewIcons = false;
    return options;
  }


  @NotNull
  @Override
  protected GraphicGenerator.Options createPreviewOptions(@NotNull Class<? extends BaseAsset> assetType) {
    AdaptiveIconGenerator.AdaptiveIconOptions options = createOptions();
    options.generateOutputIcons = false;
    options.generatePreviewIcons = true;
    return options;
  }

  @NotNull
  private AdaptiveIconGenerator.AdaptiveIconOptions createOptions() {
    AdaptiveIconGenerator.AdaptiveIconOptions options = new AdaptiveIconGenerator.AdaptiveIconOptions();
    options.useForegroundColor = myUseForegroundColor.get();
    options.foregroundColor = myForegroundColor.get().getRGB();
    if (myBackgroundImageAsset.getValueOrNull() == null) {
      options.backgroundImage = null;
    } else {
      options.backgroundImage = myBackgroundImageAsset.getValueOrNull().toImage();
    }
    options.backgroundColor = myBackgroundColor.get().getRGB();
    options.showGrid = myShowGrid.get();
    options.showSafeZone = myShowSafeZone.get();
    options.previewDensity = myPreviewDensity.get();
    options.foregroundLayerName = myForegroundLayerName.get();
    options.backgroundLayerName = myBackgroundLayerName.get();
    options.generateLegacyIcon = myGenerateLegacyIcon.get();
    options.legacyIconShape = myLegacyIconShape.get();
    options.webIconShape = myWebIconShape.get();
    options.generateRoundIcon = myGenerateRoundIcon.get();
    options.generateWebIcon = myGenerateWebIcon.get();

    return options;
  }
}
