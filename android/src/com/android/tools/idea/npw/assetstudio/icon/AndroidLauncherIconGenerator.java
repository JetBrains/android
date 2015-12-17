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
import com.android.assetstudiolib.LauncherIconGenerator;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.ObjectProperty;
import com.android.tools.idea.ui.properties.core.ObjectValueProperty;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Settings when generating a launcher icon.
 *
 * Defaults from https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html
 */
@SuppressWarnings("UseJBColor") // We are generating colors in our icons, no need for JBColor here
public final class AndroidLauncherIconGenerator extends AndroidIconGenerator {

  private final ObjectProperty<Color> myBackgroundColor = new ObjectValueProperty<Color>(Color.WHITE);
  private final BoolProperty myCropped = new BoolValueProperty();
  private final ObjectProperty<GraphicGenerator.Shape> myShape =
    new ObjectValueProperty<GraphicGenerator.Shape>(GraphicGenerator.Shape.SQUARE);
  private final BoolProperty myDogEared = new BoolValueProperty();

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

  @NotNull
  @Override
  protected GraphicGenerator createGenerator() {
    return new LauncherIconGenerator();
  }

  @NotNull
  @Override
  protected GraphicGenerator.Options createOptions(@NotNull Class<? extends BaseAsset> assetType) {
    LauncherIconGenerator.LauncherOptions launcherOptions = new LauncherIconGenerator.LauncherOptions();
    launcherOptions.shape = myShape.get();
    launcherOptions.crop = myCropped.get();
    launcherOptions.style = GraphicGenerator.Style.SIMPLE;
    launcherOptions.backgroundColor = myBackgroundColor.get().getRGB();
    launcherOptions.isWebGraphic = true;
    launcherOptions.isDogEar = myDogEared.get();

    return launcherOptions;
  }
}
