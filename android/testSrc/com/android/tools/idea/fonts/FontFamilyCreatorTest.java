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

import com.google.common.base.Charsets;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Collections;

import static com.android.tools.idea.fonts.FontFamily.FontSource.DOWNLOADABLE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

public class FontFamilyCreatorTest extends FontTestCase {
  private FontFamilyCreator myCreator;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myCreator = new FontFamilyCreator(myFacet);
  }

  public void testCreateRegularFont() throws Exception {
    FontDetail font = createFontDetail("Roboto", 400, 100, false);
    String newValue = myCreator.createFontFamily(font, "roboto", true);
    UIUtil.dispatchAllInvocationEvents();
    assertThat(newValue).isEqualTo("@font/roboto");
    assertThat(getFontFileContent("roboto.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"%n" +
      "        android:fontProviderAuthority=\"com.google.android.gms.fonts.provider.fontprovider\"%n" +
      "        android:fontProviderQuery=\"Roboto\">%n" +
      "</font-family>%n"
    ));
  }

  public void testCreateSpecificFont() throws Exception {
    FontDetail font = createFontDetail("Alegreya Sans SC", 900, 80, true);
    String newValue = myCreator.createFontFamily(font, "alegreya_sans_sc", true);
    UIUtil.dispatchAllInvocationEvents();
    assertThat(newValue).isEqualTo("@font/alegreya_sans_sc");
    assertThat(getFontFileContent("alegreya_sans_sc.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"%n" +
      "        android:fontProviderAuthority=\"com.google.android.gms.fonts.provider.fontprovider\"%n" +
      "        android:fontProviderQuery=\"name=Alegreya Sans SC&amp;weight=900&amp;italics=1&amp;width=80\">%n" +
      "</font-family>%n"
    ));
  }

  public void testCreateEmbeddedFont() throws Exception {
    addFontFileToFontCache("raleway", "v6", "other.ttf");
    FontDetail font = createFontDetail("Raleway", 700, 100, true);
    String newValue = myCreator.createFontFamily(font, "raleway_bold_italic", false);
    UIUtil.dispatchAllInvocationEvents();
    assertThat(newValue).isEqualTo("@font/raleway_bold_italic");
    assertThat(getFontFileContent("raleway_bold_italic.ttf")).isEqualTo("TrueType file");
  }

  public void testGetFontName() {
    FontDetail font = createFontDetail("Raleway", 700, 100, true);
    assertThat(FontFamilyCreator.getFontName(font)).isEqualTo("raleway_bold_italic");
  }

  @NotNull
  private static FontDetail createFontDetail(@NotNull String fontName, int weight, int width, boolean italics) {
    String folderName = DownloadableFontCacheServiceImpl.convertNameToFilename(fontName);
    String urlStart = "http://dontcare/fonts/" + folderName + "/v6/";
    FontFamily family = new FontFamily(GoogleFontProvider.INSTANCE, DOWNLOADABLE, fontName, urlStart + "some.ttf", null,
                          Collections.singletonList(new FontDetail.Builder(weight, width, italics, urlStart + "other.ttf", null)));
    return family.getFonts().get(0);
  }

  @NotNull
  private String getFontFileContent(@NotNull String fontFileName) throws IOException {
    @SuppressWarnings("deprecation")
    VirtualFile resourceDirectory = checkNotNull(myFacet.getPrimaryResourceDir());
    VirtualFile fontFolder = checkNotNull(resourceDirectory.findChild("font"));
    VirtualFile fontFile = checkNotNull(fontFolder.findChild(fontFileName));
    return new String(fontFile.contentsToByteArray(), CharsetToolkit.UTF8_CHARSET);
  }

  @SuppressWarnings("SameParameterValue")
  private void addFontFileToFontCache(@NotNull String fontFolder, @NotNull String versionFolder, @NotNull String fontFileName) throws IOException {
    File folder = makeFile(myFontPath, GoogleFontProvider.GOOGLE_FONT_AUTHORITY, "fonts", fontFolder, versionFolder);
    FileUtil.ensureExists(folder);
    File file = new File(folder, fontFileName);
    InputStream inputStream = new ByteArrayInputStream("TrueType file".getBytes(Charsets.UTF_8.toString()));
    try (OutputStream outputStream = new FileOutputStream(file)) {
      FileUtil.copy(inputStream, outputStream);
    }
  }
}
