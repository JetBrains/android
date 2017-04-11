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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;

import static com.android.tools.idea.fonts.FontFamily.FontSource.DOWNLOADABLE;
import static com.google.common.truth.Truth.assertThat;

public class FontDetailTest extends FontTestCase {

  public void testGenerateStyleName() {
    assertThat(FontDetail.generateStyleName(77, false)).isEqualTo("Custom-Light");
    assertThat(FontDetail.generateStyleName(100, false)).isEqualTo("Thin");
    assertThat(FontDetail.generateStyleName(200, false)).isEqualTo("Extra-Light");
    assertThat(FontDetail.generateStyleName(300, false)).isEqualTo("Light");
    assertThat(FontDetail.generateStyleName(400, false)).isEqualTo("Regular");
    assertThat(FontDetail.generateStyleName(500, false)).isEqualTo("Medium");
    assertThat(FontDetail.generateStyleName(600, false)).isEqualTo("Semi-Bold");
    assertThat(FontDetail.generateStyleName(700, false)).isEqualTo("Bold");
    assertThat(FontDetail.generateStyleName(800, false)).isEqualTo("Extra-Bold");
    assertThat(FontDetail.generateStyleName(900, false)).isEqualTo("Black");
    assertThat(FontDetail.generateStyleName(977, false)).isEqualTo("Custom-Bold");
  }

  public void testGenerateStyleNameWithItalics() {
    assertThat(FontDetail.generateStyleName(67, true)).isEqualTo("Custom-Light Italic");
    assertThat(FontDetail.generateStyleName(100, true)).isEqualTo("Thin Italic");
    assertThat(FontDetail.generateStyleName(200, true)).isEqualTo("Extra-Light Italic");
    assertThat(FontDetail.generateStyleName(300, true)).isEqualTo("Light Italic");
    assertThat(FontDetail.generateStyleName(400, true)).isEqualTo("Regular Italic");
    assertThat(FontDetail.generateStyleName(500, true)).isEqualTo("Medium Italic");
    assertThat(FontDetail.generateStyleName(600, true)).isEqualTo("Semi-Bold Italic");
    assertThat(FontDetail.generateStyleName(700, true)).isEqualTo("Bold Italic");
    assertThat(FontDetail.generateStyleName(800, true)).isEqualTo("Extra-Bold Italic");
    assertThat(FontDetail.generateStyleName(900, true)).isEqualTo("Black Italic");
    assertThat(FontDetail.generateStyleName(901, true)).isEqualTo("Custom-Bold Italic");
  }

  public void testConstructorAndGetters() {
    FontFamily family = createFontFamily(800, 120, false, "http://someurl.com/myfont1.ttf", "MyStyle");
    FontDetail font = family.getFonts().get(0);
    assertThat(font.getFamily()).isSameAs(family);
    assertThat(font.getWeight()).isEqualTo(800);
    assertThat(font.getWidth()).isEqualTo(120);
    assertThat(font.isItalics()).isEqualTo(false);
    assertThat(font.getFontUrl()).isEqualTo("http://someurl.com/myfont1.ttf");
    assertThat(font.getStyleName()).isEqualTo("MyStyle");
  }

  public void testConstructorWithGeneratedStyleName() {
    FontDetail font = createFontDetail(800, 110, true, "http://someurl.com/myfont2.ttf", null);
    assertThat(font.getStyleName()).isEqualTo("Extra-Bold Italic");
  }

  public void testDerivedConstructor() {
    FontDetail font = createFontDetail(800, 110, true, "http://someurl.com/myfont2.ttf", null);
    FontDetail derived = new FontDetail(font, new FontDetail.Builder(700, 100, false, "whatever", null));
    assertThat(derived.getFamily()).isSameAs(font.getFamily());
    assertThat(derived.getWeight()).isEqualTo(700);
    assertThat(derived.getWidth()).isEqualTo(100);
    assertThat(derived.isItalics()).isEqualTo(false);
    assertThat(derived.getFontUrl()).isEqualTo("http://someurl.com/myfont2.ttf");
    assertThat(derived.getStyleName()).isEqualTo("Bold");
  }

  public void testGetCachedFontFile() {
    FontDetail font = createFontDetail(800, 110, true, "http://someurl.com/myfont/v2/myfont2.ttf", null);
    File expected = makeFile(myFontPath, GoogleFontProvider.GOOGLE_FONT_AUTHORITY, "fonts", "myfont", "v2", "myfont2.ttf");
    assertThat(font.getCachedFontFile()).isEquivalentAccordingToCompareTo(expected);
  }

  public void testGetCachedFontFileWithFileReference() {
    FontDetail font = createFontDetail(800, 110, true, "file:///cache/folder/somefont/x.ttf", null);
    assertThat(font.getCachedFontFile()).isEquivalentAccordingToCompareTo(new File("/cache/folder/somefont/x.ttf"));
  }

  public void testMatch() {
    FontDetail font1 = createFontDetail(400, 100, false, "http://someurl.com/myfont1.ttf", "MyStyle");

    assertThat(font1.match(new FontDetail.Builder(400, 100, false, "http://other/f.ttf", "Something else"))).isEqualTo(0);
    assertThat(font1.match(new FontDetail.Builder(400, 100, false, "http://other/f.ttf", "Something else"))).isEqualTo(0);
    assertThat(font1.match(new FontDetail.Builder(300, 100, false, "http://other/f.ttf", "Something else"))).isEqualTo(100);
    assertThat(font1.match(new FontDetail.Builder(500, 100, false, "http://other/f.ttf", "Something else"))).isEqualTo(100);
    assertThat(font1.match(new FontDetail.Builder(900, 100, false, "http://other/f.ttf", "Something else"))).isEqualTo(500);
    assertThat(font1.match(new FontDetail.Builder(400, 90, false, "http://other/f.ttf", "Something else"))).isEqualTo(10);
    assertThat(font1.match(new FontDetail.Builder(400, 100, true, "http://other/f.ttf", "Something else"))).isEqualTo(50);
    assertThat(font1.match(new FontDetail.Builder(700, 120, true, "http://other/f.ttf", "Something else"))).isEqualTo(370);
  }

  @NotNull
  private static FontDetail createFontDetail(int weight, int width, boolean italics, @NotNull String url, @Nullable String styleName) {
    FontFamily family = createFontFamily(weight, width, italics, url, styleName);
    return family.getFonts().get(0);
  }

  @NotNull
  private static FontFamily createFontFamily(int weight, int width, boolean italics, @NotNull String url, @Nullable String styleName) {
    return new FontFamily(GoogleFontProvider.INSTANCE, DOWNLOADABLE, "MyFont", "http://someurl.com/mymenufont.ttf", "myMenu",
                          Collections.singletonList(new FontDetail.Builder(weight, width, italics, url, styleName)));
  }
}
