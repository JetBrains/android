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

import static com.android.tools.fonts.Fonts.AVAILABLE_FAMILIES;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import com.android.tools.fonts.DownloadableFontCacheServiceImpl;
import com.android.tools.fonts.SystemFonts;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class SystemFontsTest extends FontTestCase {
  private SystemFonts mySystemFonts;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySystemFonts = StudioDownloadableFontCacheService.getInstance().getSystemFonts();
  }

  public void testAllSystemFontsAvailable() {
    List<String> fontNames = ContainerUtil.map(mySystemFonts.getFontFamilies(), FontFamily::getName);
    assertThat(fontNames).containsExactlyElementsIn(AVAILABLE_FAMILIES);
  }

  public void testDefaultFontWeights() {
    assertFontWeight("sans-serif-thin", 100);
    assertFontWeight("sans-serif-light", 300);
    assertFontWeight("sans-serif", 400);
    assertFontWeight("sans-serif-medium", 500);
    assertFontWeight("sans-serif-black", 900);
    assertFontWeight("sans-serif-condensed-light", 300);
    assertFontWeight("sans-serif-condensed", 400);
    assertFontWeight("sans-serif-condensed-medium", 400); // should be 500 but that font doesn't exist on fonts.google.com
    assertFontWeight("serif", 400);
    assertFontWeight("monospace", 400);
    assertFontWeight("serif-monospace", 400);
    assertFontWeight("casual", 400);
    assertFontWeight("cursive", 400);
    assertFontWeight("sans-serif-smallcaps", 400);
  }

  private void assertFontWeight(@NotNull String fontName, int expectedWeight) {
    FontFamily family = mySystemFonts.getFont(fontName);
    assertThat(family).named(fontName).isNotNull();
    assertThat(family.getName()).isEqualTo(fontName);
    FontDetail firstDetail = family.getFonts().get(0);
    assertThat(firstDetail.getWeight()).named(fontName).isEqualTo(expectedWeight);
    assertThat(firstDetail.getItalics()).named(fontName).isFalse();
  }
}
