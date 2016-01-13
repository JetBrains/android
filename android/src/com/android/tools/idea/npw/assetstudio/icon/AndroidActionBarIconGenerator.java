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

import com.android.assetstudiolib.ActionBarIconGenerator;
import com.android.assetstudiolib.GraphicGenerator;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ClipartAsset;
import com.android.tools.idea.ui.properties.core.ObjectProperty;
import com.android.tools.idea.ui.properties.core.ObjectValueProperty;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Settings when generating an action bar / tab header icon.
 *
 * Defaults from https://romannurik.github.io/AndroidAssetStudio/icons-actionbar.html
 */
@SuppressWarnings("UseJBColor") // We are generating colors in our icons, no need for JBColor here
public final class AndroidActionBarIconGenerator extends AndroidIconGenerator {

  private final ObjectProperty<ActionBarIconGenerator.Theme> myTheme =
    new ObjectValueProperty<ActionBarIconGenerator.Theme>(ActionBarIconGenerator.Theme.HOLO_LIGHT);
  private final ObjectProperty<Color> myCustomColor = new ObjectValueProperty<Color>(new Color(51, 181, 229));

  /**
   * The theme for this icon, which influences its foreground/background colors.
   */
  @NotNull
  public ObjectProperty<ActionBarIconGenerator.Theme> theme() {
    return myTheme;
  }

  /**
   * A custom color which will be used if {@link #theme()} is set to
   * {@link ActionBarIconGenerator.Theme#CUSTOM}
   */
  @NotNull
  public ObjectProperty<Color> customColor() {
    return myCustomColor;
  }

  @NotNull
  @Override
  protected GraphicGenerator createGenerator() {
    return new ActionBarIconGenerator();
  }

  @NotNull
  @Override
  protected ActionBarIconGenerator.ActionBarOptions createOptions(@NotNull Class<? extends BaseAsset> assetType) {
    ActionBarIconGenerator.ActionBarOptions actionBarOptions = new ActionBarIconGenerator.ActionBarOptions();

    actionBarOptions.theme = myTheme.get();
    if (actionBarOptions.theme == ActionBarIconGenerator.Theme.CUSTOM) {
      actionBarOptions.customThemeColor = myCustomColor.get().getRGB();
    }

    actionBarOptions.sourceIsClipart = (assetType.equals(ClipartAsset.class));

    return actionBarOptions;
  }
}
