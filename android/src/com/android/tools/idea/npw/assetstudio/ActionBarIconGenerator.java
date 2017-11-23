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
package com.android.tools.idea.npw.assetstudio;

import com.android.ide.common.util.AssetUtil;
import com.android.ide.common.util.AssetUtil.Effect;
import com.android.ide.common.util.AssetUtil.FillEffect;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Generates icons for the action bar.
 */
@SuppressWarnings("UseJBColor") // We are generating colors in our icons, no need for JBColor here.
public class ActionBarIconGenerator extends IconGenerator {
  public static final Theme DEFAULT_THEME = Theme.HOLO_LIGHT;
  public static final Color DEFAULT_COLOR = new Color(51, 181, 229);

  private final ObjectProperty<Theme> myTheme = new ObjectValueProperty<>(DEFAULT_THEME);
  private final ObjectProperty<Color> myCustomColor = new ObjectValueProperty<>(DEFAULT_COLOR);

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param minSdkVersion the minimal supported Android SDK version
   */
  public ActionBarIconGenerator(int minSdkVersion) {
    super(minSdkVersion);
  }

  /**
   * The theme for this icon, which influences its foreground/background colors.
   */
  @NotNull
  public ObjectProperty<Theme> theme() {
    return myTheme;
  }

  /**
   * A custom color which will be used if {@link #theme()} is set to {@link Theme#CUSTOM}.
   */
  @NotNull
  public ObjectProperty<Color> customColor() {
    return myCustomColor;
  }

  @Override
  @NotNull
  public ActionBarOptions createOptions(boolean forPreview) {
    ActionBarOptions options = new ActionBarOptions();
    BaseAsset asset = sourceAsset().getValueOrNull();
    if (asset != null) {
      options.sourceImageFuture = asset.toImage();
      options.isTrimmed = asset.trimmed().get();
      options.paddingPercent = asset.paddingPercent().get();
    }

    options.theme = myTheme.get();
    if (options.theme == Theme.CUSTOM) {
      options.customThemeColor = myCustomColor.get().getRGB();
    }

    options.sourceIsClipart = asset instanceof VectorAsset;

    return options;
  }

  @Override
  @NotNull
  public BufferedImage generate(@NotNull GraphicGeneratorContext context, @NotNull Options options) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    BufferedImage sourceImage = getTrimmedAndPaddedImage(options);
    if (sourceImage == null) {
      sourceImage = AssetStudioUtils.createDummyImage();
    }
    ActionBarOptions actionBarOptions = (ActionBarOptions)options;
    Rectangle iconSizeMdpi = new Rectangle(0, 0, 32, 32);
    Rectangle targetRectMdpi = actionBarOptions.sourceIsClipart
        ? new Rectangle(0, 0, 32, 32)
        : new Rectangle(4, 4, 24, 24);
    final float scaleFactor = getMdpiScaleFactor(options.density);
    Rectangle imageRect = AssetUtil.scaleRectangle(iconSizeMdpi, scaleFactor);
    Rectangle targetRect = AssetUtil.scaleRectangle(targetRectMdpi, scaleFactor);
    BufferedImage outImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
    Graphics2D g = (Graphics2D) outImage.getGraphics();

    BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
    Graphics2D g2 = (Graphics2D)tempImage.getGraphics();
    AssetUtil.drawCenterInside(g2, sourceImage, targetRect);

    if (actionBarOptions.theme == Theme.CUSTOM) {
      AssetUtil.drawEffects(g, tempImage, 0, 0, new Effect[] {new FillEffect(new Color(actionBarOptions.customThemeColor), 0.8)});
    } else if (actionBarOptions.theme == DEFAULT_THEME) {
      AssetUtil.drawEffects(g, tempImage, 0, 0, new Effect[] {new FillEffect(new Color(0x333333), 0.6)});
    } else {
      assert actionBarOptions.theme == Theme.HOLO_DARK;
      AssetUtil.drawEffects(g, tempImage, 0, 0, new Effect[] {new FillEffect(new Color(0xFFFFFF), 0.8)});
    }

    g.dispose();
    g2.dispose();

    return outImage;
  }

  /** Options specific to generating action bar icons. */
  public static class ActionBarOptions extends Options {
    /** The theme to generate icons for. */
    public Theme theme = DEFAULT_THEME;

    /** Whether or not the source image is a clipart source. */
    public boolean sourceIsClipart = false;

    /** Custom color for use with the custom theme. */
    public int customThemeColor = 0;
  }

  /** The themes to generate action bar icons for. */
  public enum Theme {
    /** Theme.Holo - a dark (and default) version of the Honeycomb theme. */
    HOLO_DARK,

    /** Theme.HoloLight - a light version of the Honeycomb theme. */
    HOLO_LIGHT,

    /** Theme.Custom - custom colors. */
    CUSTOM
  }
}
