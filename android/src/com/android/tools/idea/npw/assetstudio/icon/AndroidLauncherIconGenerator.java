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

import com.android.tools.idea.npw.assetstudio.GraphicGenerator;
import com.android.tools.idea.npw.assetstudio.LauncherIconGenerator;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Settings when generating a launcher icon.
 *
 * Defaults from https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html
 */
@SuppressWarnings("UseJBColor") // We are generating colors in our icons, no need for JBColor here
public final class AndroidLauncherIconGenerator extends AndroidIconGenerator {
  private final BoolProperty myUseForegroundColor = new BoolValueProperty(true);
  private final ObjectProperty<Color> myForegroundColor = new ObjectValueProperty<>(Color.BLACK);
  private final ObjectProperty<Color> myBackgroundColor = new ObjectValueProperty<>(Color.WHITE);
  private final BoolProperty myCropped = new BoolValueProperty();
  private final ObjectProperty<GraphicGenerator.Shape> myShape = new ObjectValueProperty<>(GraphicGenerator.Shape.SQUARE);
  private final BoolProperty myDogEared = new BoolValueProperty();

  public AndroidLauncherIconGenerator(int minSdkVersion) {
    super(minSdkVersion, new LauncherIconGenerator());
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
  public BoolProperty cropped() {
    return myCropped;
  }

  /**
   * A shape which will be used as the icon's backdrop.
   */
  @NotNull
  public ObjectProperty<GraphicGenerator.Shape> shape() {
    return myShape;
  }

  /**
   * If true and the backdrop shape supports it, add a fold to the top-right corner of the
   * backdrop shape.
   */
  @NotNull
  public BoolProperty dogEared() {
    return myDogEared;
  }

  @Override
  @NotNull
  public GraphicGenerator.Options createOptions(boolean forPreview) {
    LauncherIconGenerator.LauncherOptions options = new LauncherIconGenerator.LauncherOptions();
    options.minSdk = getMinSdkVersion();
    BaseAsset asset = sourceAsset().getValueOrNull();
    if (asset != null) {
      options.sourceImageFuture = asset.toImage();
      options.isTrimmed = asset.trimmed().get();
      options.paddingPercent = asset.paddingPercent().get();
    }

    options.shape = myShape.get();
    options.crop = myCropped.get();
    options.style = GraphicGenerator.Style.SIMPLE;
    options.useForegroundColor = myUseForegroundColor.get();
    options.foregroundColor = myForegroundColor.get().getRGB();
    options.backgroundColor = myBackgroundColor.get().getRGB();
    options.isWebGraphic = true;
    options.isDogEar = myDogEared.get();

    return options;
  }
}
