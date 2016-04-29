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

import com.android.assetstudiolib.TextRenderUtil;
import com.android.tools.idea.ui.properties.core.IntProperty;
import com.android.tools.idea.ui.properties.core.IntValueProperty;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * An asset that represents a text value and related settings.
 */
@SuppressWarnings("UseJBColor")
public final class TextAsset extends BaseAsset {
  private static final String PREFERRED_FONT = "Roboto";
  private final StringProperty myText = new StringValueProperty("Aa");
  private final StringProperty myFontFamily = new StringValueProperty();
  private final IntProperty myFontSize = new IntValueProperty(144); // Large value for crisp icons

  public TextAsset() {
    List<String> fontFamilies = getAllFontFamilies();
    assert fontFamilies.size() > 0;

    if (fontFamilies.contains(PREFERRED_FONT)) {
      myFontFamily.set(PREFERRED_FONT);
    }
    else {
      myFontFamily.set(fontFamilies.get(0));
    }
  }

  /**
   * Return all font families available for text rendering.
   */
  @NotNull
  public static List<String> getAllFontFamilies() {
    return Lists.newArrayList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
  }

  /**
   * The text value that will be rendered into the final asset.
   */
  @NotNull
  public StringProperty text() {
    return myText;
  }

  /**
   * The font family associated with this text. Use {@link #getAllFontFamilies()} to get a list of
   * suitable values for this property.
   */
  @NotNull
  public StringProperty fontFamily() {
    return myFontFamily;
  }

  @NotNull
  @Override
  protected BufferedImage createAsImage(@NotNull Color color) {
    TextRenderUtil.Options options = new TextRenderUtil.Options();
    options.font = Font.decode(myFontFamily + " " + myFontSize.get());
    options.foregroundColor = color.getRGB();
    return TextRenderUtil.renderTextImage(myText.get(), 1, options);
  }
}
