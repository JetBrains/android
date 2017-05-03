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

import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import org.jetbrains.android.dom.AndroidDomUtil;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.android.ide.common.fonts.FontDetailKt.DEFAULT_WEIGHT;
import static com.google.common.truth.Truth.assertThat;

public class SystemFontsTest extends FontTestCase {
  private SystemFonts mySystemFonts;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySystemFonts = DownloadableFontCacheServiceImpl.getInstance().getSystemFonts();
  }

  public void testAllSystemFontsAvailable() {
    List<String> fontNames = mySystemFonts.getFontFamilies().stream().map(FontFamily::getName).collect(Collectors.toList());
    assertThat(fontNames).containsExactlyElementsIn(AndroidDomUtil.AVAILABLE_FAMILIES);
  }

  public void testAllMenuFontsAreOfWeight400() {
    for (String fontName : AndroidDomUtil.AVAILABLE_FAMILIES) {
      FontFamily family = mySystemFonts.getFont(fontName);
      FontDetail menuFont = null;
      assertThat(family).isNotNull();
      assertThat(family.getName()).isEqualTo(fontName);
      for (FontDetail detail : family.getFonts()) {
        if (Objects.equals(family.getMenu(), detail.getFontUrl())) {
          menuFont = detail;
        }
      }
      assertThat(menuFont).isNotNull();
      assertThat(menuFont.getWeight()).isEqualTo(DEFAULT_WEIGHT);
      assertThat(menuFont.getItalics()).isFalse();
    }
  }
}
