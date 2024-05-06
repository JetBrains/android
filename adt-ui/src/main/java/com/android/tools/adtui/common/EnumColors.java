/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.adtui.common;

import com.google.common.collect.Maps;
import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * A color group is a mapping of enum types to one (or more) corresponding colors each. This is
 * useful if you want to associate colors with list of features.
 *
 * If you have a case where state should further update the color for a feature, e.g. show
 * different colors when a component is selected or not, you can create multiple colors per enum
 * type, and switch the active dimension of colors using {@link #setColorIndex(int)}. The meaning
 * of each index is left up to the user of the class.
 */
public final class EnumColors<E extends Enum<E>> {
  @NotNull
  private final Map<E, List<Color>> myColors;
  private int myColorIndex;

  /**
   * Construct a simple color group where there is one color per type. If you need multiple colors
   * per type, use a {@link Builder} instead.
   */
  public EnumColors(@NotNull Map<E, Color> colors) {
    myColors = Maps.newHashMap();
    colors.forEach((e, c) -> myColors.put(e, Collections.singletonList(c)));
  }

  private EnumColors(@NotNull Builder<E> builder) {
    myColors = builder.myColors;
  }

  @NotNull
  public Color getColor(@NotNull E type) {
    List<Color> colorList = myColors.get(type);
    if (colorList == null) {
      throw new IllegalStateException("No colors for value " + type);
    }
    return colorList.get(myColorIndex % colorList.size());
  }

  /**
   * Set the index of the color to use, when there is more than one color per enum type.
   * If the index is set too large, the value will be modded by the number of colors per
   * type (e.g. Index 4 if there are 3 colors will return colors for the first index)
   */
  public void setColorIndex(int colorIndex) {
    myColorIndex = colorIndex;
  }

  /**
   * Build a {@link EnumColors} when there are multiple colors per type.
   */
  public static final class Builder<E extends Enum<E>> {
    private final int myNumColorsPerType;
    private Map<E, List<Color>> myColors = Maps.newHashMap();

    public Builder(int numColorsPerType) {
      myNumColorsPerType = numColorsPerType;
    }

    @NotNull
    public Builder<E> add(@NotNull E type, Color... colors) {
      if (colors.length != myNumColorsPerType) {
        throw new IllegalArgumentException(
          String.format("Attempting to initialize colors for %1$s with %2$d color(s), should be %3$d", type, colors.length,
                        myNumColorsPerType));
      }
      myColors.put(type, Arrays.asList(colors));
      return this;
    }

    @NotNull
    public EnumColors<E> build() {
      return new EnumColors<>(this);
    }
  }
}
