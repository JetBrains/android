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

import com.android.tools.idea.fonts.FontFamily.FontSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;

import static com.android.tools.idea.fonts.FontFamily.FontSource.DOWNLOADABLE;
import static com.google.common.truth.Truth.assertThat;

public class FontFamilyTest extends FontTestCase {

  public void testConstructorAndGetters() {
    FontFamily family = createFontFamily(GoogleFontProvider.INSTANCE, DOWNLOADABLE, "Roboto", "http://fonts/roboto.ttf", null);
    assertThat(family.getProvider()).isEqualTo(GoogleFontProvider.INSTANCE);
    assertThat(family.getFontSource()).isEqualTo(DOWNLOADABLE);
    assertThat(family.getName()).isEqualTo("Roboto");
    assertThat(family.getMenu()).isEqualTo("http://fonts/roboto.ttf");
    assertThat(family.getMenuName()).isEqualTo("Roboto");
  }

  public void testConstructorAndGettersWithSpecifiedMenuName() {
    FontFamily family =
      createFontFamily(GoogleFontProvider.INSTANCE, DOWNLOADABLE, "Alegreya Sans SC", "file:///fonts/alegreya.ttf", "My Alegreya");
    assertThat(family.getProvider()).isEqualTo(GoogleFontProvider.INSTANCE);
    assertThat(family.getFontSource()).isEqualTo(DOWNLOADABLE);
    assertThat(family.getName()).isEqualTo("Alegreya Sans SC");
    assertThat(family.getMenu()).isEqualTo("file:///fonts/alegreya.ttf");
    assertThat(family.getMenuName()).isEqualTo("My Alegreya");
  }

  public void testGetCachedMenuFile() {
    FontFamily family = createFontFamily(GoogleFontProvider.INSTANCE, DOWNLOADABLE, "Roboto", "http://fonts/roboto/v3/roboto.ttf", null);
    File expected = makeFile(myFontPath, GoogleFontProvider.GOOGLE_FONT_AUTHORITY, "fonts", "roboto", "v3", "roboto.ttf");
    assertThat(family.getCachedMenuFile()).isEquivalentAccordingToCompareTo(expected);
  }

  public void testGetCachedMenuFileWithFileReference() {
    FontFamily family = createFontFamily(GoogleFontProvider.INSTANCE, DOWNLOADABLE, "Alegreya Sans SC", "file:///fonts/alegreya.ttf", null);
    assertThat(family.getCachedMenuFile()).isEquivalentAccordingToCompareTo(new File("/fonts/alegreya.ttf"));
  }

  @NotNull
  private static FontFamily createFontFamily(@NotNull FontProvider provider,
                                             @NotNull FontSource fontSource,
                                             @NotNull String name,
                                             @NotNull String menuUrl,
                                             @Nullable String menuName) {
    return new FontFamily(provider, fontSource, name, menuUrl, menuName,
                          Collections.singletonList(new FontDetail.Builder(400, 100, false, "http://fonts/font.ttf", null)));
  }
}
