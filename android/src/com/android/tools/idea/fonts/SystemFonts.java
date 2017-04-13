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

import com.google.common.collect.ImmutableList;
import com.intellij.util.ResourceUtil;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import static com.android.tools.idea.fonts.FontFamily.FontSource.SYSTEM;

/**
 * There are TTF files loaded from the jar file which can be used to display
 * system fonts in the UI.
 */
class SystemFonts {
  private final Map<String, FontFamily> myFonts;

  public SystemFonts() {
    myFonts = createFonts();
  }

  @NotNull
  public Collection<FontFamily> getFontFamilies() {
    return myFonts.values();
  }

  private static Map<String, FontFamily> createFonts() {
    Map<String, FontFamily> fonts = new TreeMap<>();
    for (String fontName : AndroidDomUtil.AVAILABLE_FAMILIES) {
      String fontUrl = ResourceUtil.getResource(SystemFonts.class, "fonts/system", fontName + ".ttf").toString();
      FontDetail.Builder detail = new FontDetail.Builder(FontDetail.DEFAULT_WEIGHT, FontDetail.DEFAULT_WIDTH, false, fontUrl, "Regular");
      fonts.put(fontName, new FontFamily(FontProvider.EMPTY_PROVIDER, SYSTEM, fontName, fontUrl, null, ImmutableList.of(detail)));
    }
    return fonts;
  }

  @Nullable
  public FontFamily getFont(String name) {
    return myFonts.get(name);
  }
}
