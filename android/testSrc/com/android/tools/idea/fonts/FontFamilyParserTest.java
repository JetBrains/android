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
import com.intellij.testFramework.UsefulTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.*;

import static com.google.common.truth.Truth.assertThat;

public class FontFamilyParserTest extends UsefulTestCase {

  public void testParseFontFamilyWithReferences() throws Exception {
    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                 "     <font\n" +
                 "        android:fontStyle=\"normal\"\n" +
                 "        android:fontWeight=\"400\"\n" +
                 "        android:font=\"@font/a_bee_zee_regular\" />\n" +
                 "    <font\n" +
                 "        android:fontStyle=\"italic\"\n" +
                 "        android:fontWeight=\"700\"\n" +
                 "        android:font=\"@font/a_bee_zee_italics\" />" +
                 "</font-family>\n";
    FontFamilyParser.ParseResult result = FontFamilyParser.parseFontFamily(createXmlFile(xml));
    assertThat(result).isNotNull();
    assertThat(result.getAuthority()).isEmpty();
    assertThat(result.getFontName()).isEmpty();
    assertThat(result.getFonts().keySet()).containsExactly("@font/a_bee_zee_regular", "@font/a_bee_zee_italics");
    assertThat(result.getFonts().get("@font/a_bee_zee_regular").myWeight).isEqualTo(400);
    assertThat(result.getFonts().get("@font/a_bee_zee_regular").myWidth).isEqualTo(100);
    assertThat(result.getFonts().get("@font/a_bee_zee_regular").myItalics).isFalse();
    assertThat(result.getFonts().get("@font/a_bee_zee_italics").myWeight).isEqualTo(700);
    assertThat(result.getFonts().get("@font/a_bee_zee_italics").myWidth).isEqualTo(100);
    assertThat(result.getFonts().get("@font/a_bee_zee_italics").myItalics).isTrue();
  }

  public void testParseFontFamilyWithDownloadableFontQuery() throws Exception {
    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:fontProviderAuthority=\"com.google.android.gms.fonts.provider.fontprovider\"\n" +
                 "    android:fontProviderQuery=\"Aladin\">\n" +
                 "</font-family>\n";
    FontFamilyParser.ParseResult result = FontFamilyParser.parseFontFamily(createXmlFile(xml));
    assertThat(result).isNotNull();
    assertThat(result.getAuthority()).isEqualTo("com.google.android.gms.fonts.provider.fontprovider");
    assertThat(result.getFontName()).isEqualTo("Aladin");
    assertThat(result.getFonts()).isEmpty();
    assertThat(result.getFontDetail().myWeight).isEqualTo(400);
    assertThat(result.getFontDetail().myWidth).isEqualTo(100);
    assertThat(result.getFontDetail().myItalics).isFalse();
  }

  public void testParseFontFamilyWithDownloadableFontQueryWithParameters() throws Exception {
    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:fontProviderAuthority=\"com.google.android.gms.fonts.provider.fontprovider\"\n" +
                 "    android:fontProviderQuery=\"name=Aladin&amp;weight=800&amp;width=70&amp;italic=1\">\n" +
                 "</font-family>\n";
    FontFamilyParser.ParseResult result = FontFamilyParser.parseFontFamily(createXmlFile(xml));
    assertThat(result).isNotNull();
    assertThat(result.getAuthority()).isEqualTo("com.google.android.gms.fonts.provider.fontprovider");
    assertThat(result.getFontName()).isEqualTo("Aladin");
    assertThat(result.getFonts()).isEmpty();
    assertThat(result.getFontDetail().myWeight).isEqualTo(800);
    assertThat(result.getFontDetail().myWidth).isEqualTo(70);
    assertThat(result.getFontDetail().myItalics).isTrue();
  }

  private static File createXmlFile(@NotNull @Language("XML") String content) throws IOException {
    File folder = FileUtil.createTempDirectory("temp", "folder");
    File file = new File(folder, "example.xml");
    InputStream inputStream = new ByteArrayInputStream(content.getBytes(Charsets.UTF_8.toString()));
    try (OutputStream outputStream = new FileOutputStream(file)) {
      FileUtil.copy(inputStream, outputStream);
    }
    return file;
  }
}
