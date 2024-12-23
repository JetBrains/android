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
package com.android.tools.fonts;

import com.android.ide.common.fonts.MutableFontDetail;
import com.android.ide.common.fonts.QueryParser;
import com.google.common.collect.Multimap;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Iterator;
import org.junit.Test;

import static com.android.ide.common.fonts.FontDetailKt.ITALICS;
import static com.android.ide.common.fonts.FontDetailKt.NORMAL;
import static com.google.common.truth.Truth.assertThat;

public class FontFamilyParserTest {

  @Test
  public void testParseFontFamilyWithReferences() {
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
    QueryParser.ParseResult result = parseFontFamilyXml(xml);
    assertThat(result).isInstanceOf(FontFamilyParser.CompoundFontResult.class);
    FontFamilyParser.CompoundFontResult compoundResult = (FontFamilyParser.CompoundFontResult)result;
    assertThat(compoundResult.getFonts().keySet()).containsExactly("@font/a_bee_zee_regular", "@font/a_bee_zee_italics").inOrder();
    MutableFontDetail regular = compoundResult.getFonts().get("@font/a_bee_zee_regular");
    MutableFontDetail italics = compoundResult.getFonts().get("@font/a_bee_zee_italics");

    assertThat(regular.getWeight()).isEqualTo(400);
    assertThat(regular.getWidth()).isEqualTo(100f);
    assertThat(regular.getItalics()).isEqualTo(NORMAL);
    assertThat(italics.getWeight()).isEqualTo(700);
    assertThat(italics.getWidth()).isEqualTo(100f);
    assertThat(italics.getItalics()).isEqualTo(ITALICS);
  }

  @Test
  public void testParseFontFamilyWithDownloadableFontQuery() {
    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:fontProviderAuthority=\"com.google.android.gms.fonts\"\n" +
                 "    android:fontProviderQuery=\"Aladin\">\n" +
                 "</font-family>\n";
    QueryParser.ParseResult result = parseFontFamilyXml(xml);
    assertThat(result).isInstanceOf(QueryParser.DownloadableParseResult.class);
    QueryParser.DownloadableParseResult downloadableResult = (QueryParser.DownloadableParseResult)result;
    assertThat(downloadableResult.getAuthority()).isEqualTo("com.google.android.gms.fonts");

    Multimap<String, MutableFontDetail> fonts = downloadableResult.getFonts();
    assertThat(fonts.keys()).containsExactly("Aladin");
    assertThat(fonts).hasSize(1);

    MutableFontDetail fontDetail = fonts.get("Aladin").iterator().next();
    assertThat(fontDetail.getWeight()).isEqualTo(400);
    assertThat(fontDetail.getWidth()).isEqualTo(100f);
    assertThat(fontDetail.getItalics()).isEqualTo(NORMAL);
  }

  @Test
  public void testParseFontFamilyWithDownloadableFontQueryUsingAppCompat() {
    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<font-family xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    app:fontProviderAuthority=\"com.google.android.gms.fonts\"\n" +
                 "    app:fontProviderQuery=\"Aladin\">\n" +
                 "</font-family>\n";
    QueryParser.ParseResult result = parseFontFamilyXml(xml);
    assertThat(result).isInstanceOf(QueryParser.DownloadableParseResult.class);
    QueryParser.DownloadableParseResult downloadableResult = (QueryParser.DownloadableParseResult)result;
    assertThat(downloadableResult.getAuthority()).isEqualTo("com.google.android.gms.fonts");

    Multimap<String, MutableFontDetail> fonts = downloadableResult.getFonts();
    assertThat(fonts.keys()).containsExactly("Aladin");
    assertThat(fonts).hasSize(1);

    MutableFontDetail fontDetail = fonts.get("Aladin").iterator().next();
    assertThat(fontDetail.getWeight()).isEqualTo(400);
    assertThat(fontDetail.getWidth()).isEqualTo(100f);
    assertThat(fontDetail.getItalics()).isEqualTo(NORMAL);
  }

  @Test
  public void testParseFontFamilyWithDownloadableFontQueryWithParameters() {
    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:fontProviderAuthority=\"com.google.android.gms.fonts\"\n" +
                 "    android:fontProviderQuery=\"name=Aladin&amp;weight=800&amp;width=70&amp;italic=1\">\n" +
                 "</font-family>\n";
    QueryParser.ParseResult result = parseFontFamilyXml(xml);
    assertThat(result).isInstanceOf(QueryParser.DownloadableParseResult.class);
    QueryParser.DownloadableParseResult downloadableResult = (QueryParser.DownloadableParseResult)result;
    assertThat(downloadableResult.getAuthority()).isEqualTo("com.google.android.gms.fonts");

    Multimap<String, MutableFontDetail> fonts = downloadableResult.getFonts();
    assertThat(fonts.keys()).containsExactly("Aladin");
    assertThat(fonts).hasSize(1);

    MutableFontDetail fontDetail = fonts.get("Aladin").iterator().next();
    assertThat(fontDetail.getWeight()).isEqualTo(800);
    assertThat(fontDetail.getWidth()).isEqualTo(70f);
    assertThat(fontDetail.getItalics()).isEqualTo(ITALICS);
  }

  @Test
  public void testParseFontFamilyWithMultipleDownloadableFonts() {
    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:fontProviderAuthority=\"com.google.android.gms.fonts\"\n" +
                 "    android:fontProviderQuery=\"Roboto:r,700i|Aladin:800:wdth70.0\">\n" +
                 "</font-family>\n";
    QueryParser.ParseResult result = parseFontFamilyXml(xml);
    assertThat(result).isInstanceOf(QueryParser.DownloadableParseResult.class);
    QueryParser.DownloadableParseResult downloadableResult = (QueryParser.DownloadableParseResult)result;
    assertThat(downloadableResult.getAuthority()).isEqualTo("com.google.android.gms.fonts");

    Multimap<String, MutableFontDetail> fonts = downloadableResult.getFonts();
    assertThat(fonts.keys()).containsExactly("Roboto", "Roboto", "Aladin").inOrder();
    assertThat(fonts).hasSize(3);

    MutableFontDetail aladin = fonts.get("Aladin").iterator().next();
    assertThat(aladin.getWeight()).isEqualTo(800);
    assertThat(aladin.getWidth()).isEqualTo(70f);
    assertThat(aladin.getItalics()).isEqualTo(NORMAL);

    Iterator<MutableFontDetail> iterator = fonts.get("Roboto").iterator();
    MutableFontDetail roboto1 = iterator.next();
    assertThat(roboto1.getWeight()).isEqualTo(400);
    assertThat(roboto1.getWidth()).isEqualTo(100f);
    assertThat(roboto1.getItalics()).isEqualTo(NORMAL);

    MutableFontDetail roboto2 = iterator.next();
    assertThat(roboto2.getWeight()).isEqualTo(700);
    assertThat(roboto2.getWidth()).isEqualTo(100f);
    assertThat(roboto2.getItalics()).isEqualTo(ITALICS);
  }

  private QueryParser.ParseResult parseFontFamilyXml(@NotNull @Language("XML") String content) {
    return FontFamilyParser.parseFontFamily(new ByteArrayInputStream(content.getBytes()), "example.xml");
  }
}
