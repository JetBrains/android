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
import com.google.common.collect.Multimap;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Iterator;

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
    QueryParser.ParseResult result = FontFamilyParser.parseFontFamily(createXmlFile(xml));
    assertThat(result).isInstanceOf(FontFamilyParser.CompoundFontResult.class);
    FontFamilyParser.CompoundFontResult compoundResult = (FontFamilyParser.CompoundFontResult)result;
    assertThat(compoundResult.getFonts().keySet()).containsExactly("@font/a_bee_zee_regular", "@font/a_bee_zee_italics");
    FontDetail.Builder regular = compoundResult.getFonts().get("@font/a_bee_zee_regular");
    FontDetail.Builder italics = compoundResult.getFonts().get("@font/a_bee_zee_italics");

    assertThat(regular.myWeight).isEqualTo(400);
    assertThat(regular.myWidth).isEqualTo(100);
    assertThat(regular.myItalics).isFalse();
    assertThat(italics.myWeight).isEqualTo(700);
    assertThat(italics.myWidth).isEqualTo(100);
    assertThat(italics.myItalics).isTrue();
  }

  public void testParseFontFamilyWithDownloadableFontQuery() throws Exception {
    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:fontProviderAuthority=\"com.google.android.gms.fonts\"\n" +
                 "    android:fontProviderQuery=\"Aladin\">\n" +
                 "</font-family>\n";
    QueryParser.ParseResult result = FontFamilyParser.parseFontFamily(createXmlFile(xml));
    assertThat(result).isInstanceOf(QueryParser.DownloadableParseResult.class);
    QueryParser.DownloadableParseResult downloadableResult = (QueryParser.DownloadableParseResult)result;
    assertThat(downloadableResult.getAuthority()).isEqualTo("com.google.android.gms.fonts");

    Multimap<String, FontDetail.Builder> fonts = downloadableResult.getFonts();
    assertThat(fonts.keys()).containsExactly("Aladin");
    assertThat(fonts).hasSize(1);

    FontDetail.Builder fontDetail = fonts.get("Aladin").iterator().next();
    assertThat(fontDetail.myWeight).isEqualTo(400);
    assertThat(fontDetail.myWidth).isEqualTo(100);
    assertThat(fontDetail.myItalics).isFalse();
  }

  public void testParseFontFamilyWithDownloadableFontQueryWithParameters() throws Exception {
    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:fontProviderAuthority=\"com.google.android.gms.fonts\"\n" +
                 "    android:fontProviderQuery=\"name=Aladin&amp;weight=800&amp;width=70&amp;italic=1\">\n" +
                 "</font-family>\n";
    QueryParser.ParseResult result = FontFamilyParser.parseFontFamily(createXmlFile(xml));
    assertThat(result).isInstanceOf(QueryParser.DownloadableParseResult.class);
    QueryParser.DownloadableParseResult downloadableResult = (QueryParser.DownloadableParseResult)result;
    assertThat(downloadableResult.getAuthority()).isEqualTo("com.google.android.gms.fonts");

    Multimap<String, FontDetail.Builder> fonts = downloadableResult.getFonts();
    assertThat(fonts.keys()).containsExactly("Aladin");
    assertThat(fonts).hasSize(1);

    FontDetail.Builder fontDetail = fonts.get("Aladin").iterator().next();
    assertThat(fontDetail.myWeight).isEqualTo(800);
    assertThat(fontDetail.myWidth).isEqualTo(70);
    assertThat(fontDetail.myItalics).isTrue();
  }

  public void testParseFontFamilyWithMultipleDownloadableFonts() throws Exception {
    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:fontProviderAuthority=\"com.google.android.gms.fonts\"\n" +
                 "    android:fontProviderQuery=\"Roboto:r,700i|Aladin:800:wdth70.0\">\n" +
                 "</font-family>\n";
    QueryParser.ParseResult result = FontFamilyParser.parseFontFamily(createXmlFile(xml));
    assertThat(result).isInstanceOf(QueryParser.DownloadableParseResult.class);
    QueryParser.DownloadableParseResult downloadableResult = (QueryParser.DownloadableParseResult)result;
    assertThat(downloadableResult.getAuthority()).isEqualTo("com.google.android.gms.fonts");

    Multimap<String, FontDetail.Builder> fonts = downloadableResult.getFonts();
    assertThat(fonts.keys()).containsExactly("Aladin", "Roboto", "Roboto");
    assertThat(fonts).hasSize(3);

    FontDetail.Builder aladin = fonts.get("Aladin").iterator().next();
    assertThat(aladin.myWeight).isEqualTo(800);
    assertThat(aladin.myWidth).isEqualTo(70);
    assertThat(aladin.myItalics).isFalse();

    Iterator<FontDetail.Builder> iterator = fonts.get("Roboto").iterator();
    FontDetail.Builder roboto1 = iterator.next();
    assertThat(roboto1.myWeight).isEqualTo(400);
    assertThat(roboto1.myWidth).isEqualTo(100);
    assertThat(roboto1.myItalics).isFalse();

    FontDetail.Builder roboto2 = iterator.next();
    assertThat(roboto2.myWeight).isEqualTo(700);
    assertThat(roboto2.myWidth).isEqualTo(100);
    assertThat(roboto2.myItalics).isTrue();
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
