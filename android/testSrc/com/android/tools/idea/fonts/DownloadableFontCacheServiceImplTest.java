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

import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.fonts.FontProvider;
import com.android.ide.common.fonts.FontSource;
import com.android.ide.common.fonts.MutableFontDetail;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static com.android.ide.common.fonts.FontProviderKt.GOOGLE_FONT_AUTHORITY;
import static com.google.common.truth.Truth.assertThat;

public class DownloadableFontCacheServiceImplTest extends FontTestCase {

  public void testConvertNameToFilename() {
    assertThat(DownloadableFontCacheServiceImpl.convertNameToFilename("ABeeZee")).isEqualTo("abeezee");
    assertThat(DownloadableFontCacheServiceImpl.convertNameToFilename("Alegreya Sans SC")).isEqualTo("alegreya_sans_sc");
    assertThat(DownloadableFontCacheServiceImpl.convertNameToFilename("Alfa Slab One")).isEqualTo("alfa_slab_one");
    assertThat(DownloadableFontCacheServiceImpl.convertNameToFilename("ALFA SLAB ONE")).isEqualTo("alfa_slab_one");
    assertThat(
      DownloadableFontCacheServiceImpl.convertNameToFilename("Alfa Slab One Regular Italic")).isEqualTo("alfa_slab_one_regular_italic");
  }

  public void testGetCachedMenuFile() {
    DownloadableFontCacheService service = DownloadableFontCacheService.getInstance();
    FontFamily family =
      createFontFamily(FontProvider.GOOGLE_PROVIDER, FontSource.DOWNLOADABLE, "Roboto", "https://fonts.com/roboto/v15/xyz.ttf", "");
    File expected = makeFile(myFontPath, GOOGLE_FONT_AUTHORITY, "fonts", "roboto", "v15", "xyz.ttf");
    assertThat(service.getCachedMenuFile(family)).isEquivalentAccordingToCompareTo(expected);
  }

  public void testFontFamilyXml() throws IOException {
    MutableFontDetail builder = new MutableFontDetail();
    builder.setItalics(true);
    builder.setStyleName("Regular Italic");
    File fakeTtfFile = FileUtil.createTempFile("fontFamily", ".ttf");
    // The font needs to exist to be added to the generated file so create a fake cache file
    builder.setFontUrl("file://" + fakeTtfFile.getAbsolutePath());
    FontFamily embeddedFontFamily = new FontFamily(FontProvider.EMPTY_PROVIDER, FontSource.PROJECT, "embeddedFont", "file://test/path", "",
                                                   Collections.singletonList(builder));

    FontFamily compoundFontFamily = new FontFamily(FontProvider.GOOGLE_PROVIDER,
                                                   FontSource.DOWNLOADABLE,
                                                   "myFont",
                                                   "Menu name",
                                                   "",
                                                   ImmutableList.of(embeddedFontFamily.getFonts().get(0)));
    DownloadableFontCacheService service = DownloadableFontCacheService.getInstance();
    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\">" +
                 "<font android:font=\"" + fakeTtfFile.getAbsolutePath() + "\" " +
                 "android:fontStyle=\"italic\" " +
                 "android:fontWeight=\"400\" />" +
                 "</font-family>",
                 service.toXml(compoundFontFamily));
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static FontFamily createFontFamily(@NotNull FontProvider provider,
                                             @NotNull FontSource fontSource,
                                             @NotNull String name,
                                             @NotNull String menuUrl,
                                             @NotNull String menuName) {
    return new FontFamily(provider, fontSource, name, menuUrl, menuName, Collections.singletonList(
      new MutableFontDetail(400, 100, false, "https://fonts.com/roboto/v15/qrs.ttf", "", false)));
  }
}
