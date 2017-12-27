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
package com.android.tools.idea.fonts;

import com.android.ide.common.fonts.*;
import com.google.common.primitives.Ints;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.android.ide.common.fonts.FontDetailKt.DEFAULT_WIDTH;

/**
 * There are TTF files loaded from the jar file which can be used to display
 * system fonts in the UI.
 */
class SystemFonts {
  private static final int CONDENSED_WIDTH = 75;

  private final Map<String, FontFamily> myFonts;

  public SystemFonts(@NotNull DownloadableFontCacheServiceImpl service) {
    myFonts = createFonts(service);
  }

  @NotNull
  public Collection<FontFamily> getFontFamilies() {
    return myFonts.values();
  }

  @Nullable
  public FontFamily getFont(String name) {
    return myFonts.get(name);
  }

  private static Map<String, FontFamily> createFonts(@NotNull DownloadableFontCacheServiceImpl service) {
    Map<String, FontFamily> fonts = new TreeMap<>();
    for (String fontName : AndroidDomUtil.AVAILABLE_FAMILIES) {
      FontFamily family = createFont(service, fontName);
      if (family != null) {
        fonts.put(fontName, family);
      }
    }
    return fonts;
  }

  @Nullable
  private static FontFamily createFont(@NotNull DownloadableFontCacheServiceImpl service, @NotNull String systemFontName) {
    switch (systemFontName) {
      case "sans-serif":
        return findFont(service, systemFontName, "Roboto", DEFAULT_WIDTH, 100, 300, 400, 500, 700, 900);
      case "sans-serif-condensed":
        return findFont(service, systemFontName, "Roboto", CONDENSED_WIDTH, 300, 400, 700);
      case "serif":
        return findFont(service, systemFontName, "Noto Serif", DEFAULT_WIDTH, 400, 700);
      case "monospace":
        return findFont(service, systemFontName, "Droid Sans Mono", DEFAULT_WIDTH, 400);
      case "serif-monospace":
        return findFont(service, systemFontName, "Cutive Mono", DEFAULT_WIDTH, 400);
      case "casual":
        return findFont(service, systemFontName, "Coming Soon", DEFAULT_WIDTH, 400);
      case "cursive":
        return findFont(service, systemFontName, "Dancing Script", DEFAULT_WIDTH, 400, 700);
      case "sans-serif-smallcaps":
        return findFont(service, systemFontName, "Carrois Gothic SC", DEFAULT_WIDTH, 400);
      default:
        return null;
    }
  }

  @Nullable
  private static FontFamily findFont(@NotNull DownloadableFontCacheServiceImpl service,
                                     @NotNull String systemFontName,
                                     @NotNull String name,
                                     int width,
                                     int... weights) {
    FontFamily family = service.findFont(FontProvider.GOOGLE_PROVIDER, name);
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
