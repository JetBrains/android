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

import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.google.common.base.Joiner;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.idea.fonts.GoogleFontProvider.GOOGLE_FONT_AUTHORITY;
import static com.google.common.truth.Truth.assertThat;

public class ProjectFontsTest extends FontTestCase {

  public void testEmbeddedFontFile() {
    VirtualFile file = myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/a_bee_zee_regular.ttf");
    ProjectFonts project = createProjectFonts(file);

    List<FontFamily> fonts = project.getFonts();

    assertThat(fonts.size()).isEqualTo(1);
    FontFamily family = assertFontFamily(fonts.get(0), "a_bee_zee_regular", FontProvider.EMPTY_PROVIDER,
                                         FontFamily.FILE_PROTOCOL_START + PathUtil.toSystemDependentName(file.getPath()),
                                         new File(file.getPath()));

    assertThat(family.getFonts().size()).isEqualTo(1);

    assertFontDetail(family.getFonts().get(0), "a_bee_zee_regular", "Regular", 400, 100, false,
                     FontFamily.FILE_PROTOCOL_START + PathUtil.toSystemDependentName(file.getPath()), new File(file.getPath()));
  }

  public void testCompoundFamilyFile() {
    VirtualFile fileA = myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/fonta.ttf");
    VirtualFile fileB = myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/fontb.ttf");
    VirtualFile fileC = myFixture.copyFileToProject("fonts/my_font_family.xml", "res/font/my_font_family.xml");
    ProjectFonts project = createProjectFonts(fileC);

    List<String> fonts = project.getFonts().stream().map(FontFamily::getName).collect(Collectors.toList());
    assertThat(fonts).containsExactly("fonta", "fontb", "my_font_family");

    FontFamily family = assertFontFamily(project.getFont("@font/my_font_family"), "my_font_family", FontProvider.EMPTY_PROVIDER,
                                         FontFamily.FILE_PROTOCOL_START + PathUtil.toSystemDependentName(fileA.getPath()),
                                         new File(fileA.getPath()));

    assertThat(family.getFonts().size()).isEqualTo(2);

    assertFontDetail(family.getFonts().get(0), "fonta", "Regular", 400, 100, false,
                     FontFamily.FILE_PROTOCOL_START + PathUtil.toSystemDependentName(fileA.getPath()), new File(fileA.getPath()));
    assertFontDetail(family.getFonts().get(1), "fontb", "Regular Italic", 400, 100, true,
                     FontFamily.FILE_PROTOCOL_START + PathUtil.toSystemDependentName(fileB.getPath()), new File(fileB.getPath()));
  }

  public void testCompoundFamilyFileWithCircularReferences() {
    VirtualFile file = myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/fonta.ttf");
    myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/fontb.ttf");
    myFixture.copyFileToProject("fonts/my_circular_font_family_1.xml", "res/font/my_circular_font_family_1.xml");
    myFixture.copyFileToProject("fonts/my_circular_font_family_1.xml", "res/font/my_circular_font_family_2.xml");
    ProjectFonts project = createProjectFonts(file);
    List<String> fonts = project.getFonts().stream().map(FontFamily::getName).collect(Collectors.toList());

    assertThat(fonts).containsExactly("fonta", "fontb", "my_circular_font_family_1", "my_circular_font_family_2");
    assertUnresolvedFont(project.getFont("@font/my_circular_font_family_1"), "my_circular_font_family_1");
    assertUnresolvedFont(project.getFont("@font/my_circular_font_family_2"), "my_circular_font_family_2");
  }

  public void testDownloadableFamilyFile() {
    VirtualFile file = myFixture.copyFileToProject("fonts/roboto.xml", "res/font/roboto.xml");
    ProjectFonts project = createProjectFonts(file);

    List<String> fonts = project.getFonts().stream().map(FontFamily::getName).collect(Collectors.toList());
    assertThat(fonts).containsExactly("roboto");

    FontFamily family = assertFontFamily(project.getFont("@font/roboto"), "roboto", "roboto", "v15", "W5F8_SL0XFawnjxHGsZjJA.ttf");
    assertThat(family.getFonts().size()).isEqualTo(1);

    assertFontDetail(family.getFonts().get(0), "Roboto", "Regular", 400, 100, false, "roboto", "v15", "W5F8_SL0XFawnjxHGsZjJA.ttf");
  }

  public void testDownloadableFamilyFileWithParameters() {
    VirtualFile file = myFixture.copyFileToProject("fonts/roboto_bold.xml", "res/font/roboto_bold.xml");
    ProjectFonts project = createProjectFonts(file);

    List<String> fonts = project.getFonts().stream().map(FontFamily::getName).collect(Collectors.toList());
    assertThat(fonts).containsExactly("roboto_bold");
    FontFamily family = assertFontFamily(project.getFont("@font/roboto_bold"), "roboto_bold",
                                         "robotocondensed", "v15", "mg0cGfGRUERshzBlvqxeAE2zk2RGRC3SlyyLLQfjS_8.ttf");
    assertThat(family.getFonts().size()).isEqualTo(1);

    assertFontDetail(family.getFonts().get(0), "Roboto", "Condensed Bold Italic", 700, 75, true,
                     "robotocondensed", "v15", "mg0cGfGRUERshzBlvqxeAE2zk2RGRC3SlyyLLQfjS_8.ttf");
  }

  public void testDownloadableFamilyFileWithMultipleFonts() {
    VirtualFile file = myFixture.copyFileToProject("fonts/misc.xml", "res/font/misc.xml");
    ProjectFonts project = createProjectFonts(file);

    List<String> fonts = project.getFonts().stream().map(FontFamily::getName).collect(Collectors.toList());
    assertThat(fonts).containsExactly("misc");

    FontFamily family = assertFontFamily(project.getFont("@font/misc"), "misc",
                                         "alegreyasans", "v3", "KYNzioYhDai7mTMnx_gDgn8f0n03UdmQgF_CLvNR2vg.ttf");
    assertThat(family.getFonts().size()).isEqualTo(5);

    assertFontDetail(family.getFonts().get(0), "Source Sans Pro", "ExtraLight", 200, 100, false,
                     "sourcesanspro", "v9", "toadOcfmlt9b38dHJxOBGKXvKVW_haheDNrHjziJZVk.ttf");
    assertFontDetail(family.getFonts().get(1), "Alegreya Sans", "Regular", 400, 100, false,
                     "alegreyasans", "v3", "KYNzioYhDai7mTMnx_gDgn8f0n03UdmQgF_CLvNR2vg.ttf");
    assertFontDetail(family.getFonts().get(2), "Alegreya Sans", "Bold", 700, 100, false,
                     "alegreyasans", "v3", "11EDm-lum6tskJMBbdy9aVCbmAUID8LN-q3pJpOk3Ys.ttf");
    assertFontDetail(family.getFonts().get(3), "Exo 2", "Regular Italic", 400, 100, true,
                     "exo2", "v3", "xxA5ZscX9sTU6U0lZJUlYA.ttf");
    assertFontDetail(family.getFonts().get(4), "Exo 2", "Bold Italic", 700, 100, true,
                     "exo2", "v3", "Sdo-zW-4_--pDkTg6bYrY_esZW2xOQ-xsNqO47m55DA.ttf");
  }

  private ProjectFonts createProjectFonts(@NotNull VirtualFile file) {
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file);
    ResourceResolver resolver = configuration.getResourceResolver();
    assert resolver != null;
    return new ProjectFonts(resolver);
  }

  @NotNull
  private FontFamily assertFontFamily(@Nullable FontFamily family,
                                @NotNull String expectedFontName,
                                @NotNull String expectedFolder1,
                                @NotNull String expectedFolder2,
                                @NotNull String expectedFilename) {
    String expectedUrl = Joiner.on('/').join("https://fonts.gstatic.com/s", expectedFolder1, expectedFolder2, expectedFilename);
    File expectedFile = makeFile(myFontPath, GOOGLE_FONT_AUTHORITY, "fonts", expectedFolder1, expectedFolder2, expectedFilename);
    return assertFontFamily(family, expectedFontName, GoogleFontProvider.INSTANCE, expectedUrl, expectedFile);
  }

  @NotNull
  private static FontFamily assertFontFamily(@Nullable FontFamily family,
                                             @NotNull String expectedFontName,
                                             @NotNull FontProvider expectedProvider,
                                             @NotNull String expectedUrl,
                                             @NotNull File expectedFile) {
    assertThat(family).isNotNull();
    assertThat(family.getProvider()).isEqualTo(expectedProvider);
    assertThat(family.getFontSource()).isSameAs(FontFamily.FontSource.PROJECT);
    assertThat(family.getName()).isEqualTo(expectedFontName);
    assertThat(family.getMenuName()).isEqualTo(expectedFontName);
    assertThat(family.getMenu()).isEqualTo(expectedUrl);
    assertThat(family.getCachedMenuFile()).isEquivalentAccordingToCompareTo(expectedFile);
    return family;
  }

  private void assertFontDetail(@NotNull FontDetail detail,
                                @NotNull String expectedFontName,
                                @NotNull String expectedStyleName,
                                int expectedWeight,
                                int expectedWidth,
                                boolean expectedItalics,
                                @NotNull String expectedFolder1,
                                @NotNull String expectedFolder2,
                                @NotNull String expectedFilename) {
    String url = Joiner.on('/').join("https://fonts.gstatic.com/s", expectedFolder1, expectedFolder2, expectedFilename);
    File file = makeFile(myFontPath, GOOGLE_FONT_AUTHORITY, "fonts", expectedFolder1, expectedFolder2, expectedFilename);
    assertFontDetail(detail, expectedFontName, expectedStyleName, expectedWeight, expectedWidth, expectedItalics, url, file);
  }

  private static void assertFontDetail(@NotNull FontDetail detail,
                                       @NotNull String expectedFontName,
                                       @NotNull String expectedStyleName,
                                       int expectedWeight,
                                       int expectedWidth,
                                       boolean expectedItalics,
                                       @NotNull String expectedUrl,
                                       @NotNull File expectedFile) {
    assertThat(detail.getFamily().getName()).isEqualTo(expectedFontName);
    assertThat(detail.getWeight()).isEqualTo(expectedWeight);
    assertThat(detail.getWidth()).isEqualTo(expectedWidth);
    assertThat(detail.isItalics()).isEqualTo(expectedItalics);
    assertThat(detail.getStyleName()).isEqualTo(expectedStyleName);
    assertThat(detail.getFontUrl()).isEqualTo(expectedUrl);
    assertThat(detail.getCachedFontFile()).isEquivalentAccordingToCompareTo(expectedFile);
  }

  private static void assertUnresolvedFont(@Nullable FontFamily family, @NotNull String expectedName) {
    assertThat(family).isNotNull();
    assertThat(family.getProvider()).isEqualTo(FontProvider.EMPTY_PROVIDER);
    assertThat(family.getFontSource()).isSameAs(FontFamily.FontSource.PROJECT);
    assertThat(family.getName()).isEqualTo(expectedName);
    assertThat(family.getMenuName()).isEqualTo(expectedName);
    assertThat(family.getMenu()).isEmpty();
    assertThat(family.getCachedMenuFile()).isNull();
    assertThat(family.getFonts().size()).isEqualTo(1);

    FontDetail detail = family.getFonts().get(0);
    assertThat(detail.getFamily()).isSameAs(family);
    assertThat(detail.getWeight()).isEqualTo(400);
    assertThat(detail.getWidth()).isEqualTo(100);
    assertThat(detail.isItalics()).isFalse();
    assertThat(detail.getStyleName()).isEqualTo("Regular");
    assertThat(detail.getFontUrl()).isEmpty();
    assertThat(detail.getCachedFontFile()).isNull();
  }
}
