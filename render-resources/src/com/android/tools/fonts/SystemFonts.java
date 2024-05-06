/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.fonts;

import com.android.ide.common.fonts.*;
import com.google.common.primitives.Ints;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.android.ide.common.fonts.FontDetailKt.DEFAULT_WIDTH;
import static com.android.tools.fonts.Fonts.AVAILABLE_FAMILIES;

/**
 * There are TTF files loaded from the jar file which can be used to display
 * system fonts in the UI.
 */
public class SystemFonts {
  private static final int CONDENSED_WIDTH = 75;

  private final Map<String, FontFamily> myFonts;

  public SystemFonts(@NotNull FontLoader fontLoader) {
    myFonts = createFonts(fontLoader);
  }

  @NotNull
  public Collection<FontFamily> getFontFamilies() {
    return myFonts.values();
  }

  @Nullable
  public FontFamily getFont(String name) {
    return myFonts.get(name);
  }

  private static Map<String, FontFamily> createFonts(@NotNull FontLoader fontLoader) {
    Map<String, FontFamily> fonts = new TreeMap<>();
    for (String fontName : AVAILABLE_FAMILIES) {
      FontFamily family = createFont(fontLoader, fontName);
      if (family != null) {
        fonts.put(fontName, family);
      }
    }
    return fonts;
  }

  @Nullable
  private static FontFamily createFont(@NotNull FontLoader fontLoader, @NotNull String systemFontName) {
    switch (systemFontName) {
      case "sans-serif-thin":
        return findFont(fontLoader, systemFontName, "Roboto", DEFAULT_WIDTH, 100, 400);
      case "sans-serif-light":
        return findFont(fontLoader, systemFontName, "Roboto", DEFAULT_WIDTH, 300, 700);
      case "sans-serif":
        return findFont(fontLoader, systemFontName, "Roboto", DEFAULT_WIDTH, 400, 700);
      case "sans-serif-medium":
        return findFont(fontLoader, systemFontName, "Roboto", DEFAULT_WIDTH, 500, 900);
      case "sans-serif-black":
        return findFont(fontLoader, systemFontName, "Roboto", DEFAULT_WIDTH, 900);
      case "sans-serif-condensed-light":
        return findFont(fontLoader, systemFontName, "Roboto", CONDENSED_WIDTH, 300, 700);
      case "sans-serif-condensed":
        return findFont(fontLoader, systemFontName, "Roboto", CONDENSED_WIDTH, 400, 700);
      case "sans-serif-condensed-medium":
        // Should be (500, 700) but 500 doesn't exist on fonts.google.com
        return findFont(fontLoader, systemFontName, "Roboto", CONDENSED_WIDTH, 400, 700);
      case "serif":
        return findFont(fontLoader, systemFontName, "Noto Serif", DEFAULT_WIDTH, 400, 700);
      case "monospace":
        return findFont(fontLoader, systemFontName, "Droid Sans Mono", DEFAULT_WIDTH, 400);
      case "serif-monospace":
        return findFont(fontLoader, systemFontName, "Cutive Mono", DEFAULT_WIDTH, 400);
      case "casual":
        return findFont(fontLoader, systemFontName, "Coming Soon", DEFAULT_WIDTH, 400);
      case "cursive":
        return findFont(fontLoader, systemFontName, "Dancing Script", DEFAULT_WIDTH, 400, 700);
      case "sans-serif-smallcaps":
        return findFont(fontLoader, systemFontName, "Carrois Gothic SC", DEFAULT_WIDTH, 400);
      default:
        return null;
    }
  }

  @Nullable
  private static FontFamily findFont(@NotNull FontLoader fontLoader,
                                     @NotNull String systemFontName,
                                     @NotNull String name,
                                     int width,
                                     int... weights) {
    FontFamily family = fontLoader.findFont(FontProvider.GOOGLE_PROVIDER, name);
    if (family == null) {
      return null;
    }
    List<MutableFontDetail> filtered = family.getFonts().stream()
      .filter(font -> font.getWidth() == width && Ints.contains(weights, font.getWeight()))
      .map(FontDetail::toMutableFontDetail)
      .collect(Collectors.toList());

    MutableFontDetail wanted = new MutableFontDetail(400, width, false);
    FontDetail best = wanted.findBestMatch(family.getFonts());
    if (best == null) {
      return null;
    }
    return new FontFamily(FontProvider.GOOGLE_PROVIDER, FontSource.SYSTEM, systemFontName, best.getFontUrl(), "", filtered);
  }
}
