/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.adtui.font;

import com.google.common.collect.Sets;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.util.SystemInfo;
import java.awt.Font;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public final class FontUtil {
  @NotNull
  public static Font getFontAbleToDisplay(@NotNull String s, @NotNull Font defaultFont) {
    if (SystemInfo.isMac          // On Macs, all fonts can display all the characters because the system renders using fallback fonts.
        || isExtendedAscii(s)) {  // Assume that default font can handle ASCII
      return defaultFont;
    }

    Set<Font> fonts = Sets.newHashSetWithExpectedSize(10);

    FontPreferences fontPreferences = EditorColorsManager.getInstance().getGlobalScheme().getFontPreferences();
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) > 255) {
        fonts.add(ComplementaryFontsRegistry.getFontAbleToDisplay(s.charAt(i), Font.PLAIN, fontPreferences, null).getFont());
      }
    }

    if (fonts.isEmpty()) {
      return defaultFont;
    }

    // find the font the can handle the most # of characters
    Font bestFont = defaultFont;
    int max = 0;
    for (Font f : fonts) {
      int supportedChars = 0;
      for (int i = 0; i < s.length(); i++) {
        if (f.canDisplay(s.charAt(i))) {
          supportedChars++;
        }
      }

      if (supportedChars > max) {
        max = supportedChars;
        bestFont = f;
      }
    }

    return bestFont;
  }

  private static boolean isExtendedAscii(@NotNull String s) {
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) > 255) {
        return false;
      }
    }

    return true;
  }
}
