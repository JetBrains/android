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
    FontFamily family = fonts.get(0);
    assertThat(family.getProvider()).isEqualTo(FontProvider.EMPTY_PROVIDER);
    assertThat(family.getFontSource()).isSameAs(FontFamily.FontSource.PROJECT);
    assertThat(family.getName()).isEqualTo("a_bee_zee_regular");
    assertThat(family.getMenuName()).isEqualTo("a_bee_zee_regular");
    assertThat(family.getMenu()).isEqualTo(FontFamily.FILE_PROTOCOL_START + PathUtil.toSystemDependentName(file.getPath()));
    assertThat(family.getCachedMenuFile()).isEquivalentAccordingToCompareTo(new File(file.getPath()));
    assertThat(family.getFonts().size()).isEqualTo(1);

    FontDetail detail = family.getFonts().get(0);
    assertThat(detail.getFamily()).isEqualTo(family);
    assertThat(detail.getWeight()).isEqualTo(400);
    assertThat(detail.getWidth()).isEqualTo(100);
    assertThat(detail.isItalics()).isFalse();
    assertThat(detail.getStyleName()).isEqualTo("Regular");
    assertThat(detail.getFontUrl()).isEqualTo(FontFamily.FILE_PROTOCOL_START + PathUtil.toSystemDependentName(file.getPath()));
    assertThat(detail.getCachedFontFile()).isEqualTo(new File(file.getPath()));
  }

  public void testCompoundFamilyFile() {
    VirtualFile fileA = myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/fonta.ttf");
    VirtualFile fileB = myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/fontb.ttf");
    VirtualFile fileC = myFixture.copyFileToProject("fonts/my_font_family.xml", "res/font/my_font_family.xml");
    ProjectFonts project = createProjectFonts(fileC);
    List<String> fonts = project.getFonts().stream().map(FontFamily::getName).collect(Collectors.toList());

    assertThat(fonts).containsExactly("fonta", "fontb", "my_font_family");
    FontFamily family = project.getFont("@font/my_font_family");
    assertThat(family).isNotNull();
    assertThat(family.getProvider()).isEqualTo(FontProvider.EMPTY_PROVIDER);
    assertThat(family.getFontSource()).isSameAs(FontFamily.FontSource.PROJECT);
    assertThat(family.getName()).isEqualTo("my_font_family");
    assertThat(family.getMenuName()).isEqualTo("my_font_family");
    assertThat(family.getMenu()).isEqualTo(FontFamily.FILE_PROTOCOL_START + PathUtil.toSystemDependentName(fileA.getPath()));
    assertThat(family.getCachedMenuFile()).isEquivalentAccordingToCompareTo(new File(fileA.getPath()));
    assertThat(family.getFonts().size()).isEqualTo(2);

    FontDetail detailA = family.getFonts().get(0);
    assertThat(detailA.getFamily()).isNotEqualTo(family);
    assertThat(detailA.getFamily().getName()).isEqualTo("fonta");
    assertThat(detailA.getWeight()).isEqualTo(400);
    assertThat(detailA.getWidth()).isEqualTo(100);
    assertThat(detailA.isItalics()).isFalse();
    assertThat(detailA.getStyleName()).isEqualTo("Regular");
    assertThat(detailA.getFontUrl()).isEqualTo(FontFamily.FILE_PROTOCOL_START + PathUtil.toSystemDependentName(fileA.getPath()));
    assertThat(detailA.getCachedFontFile()).isEquivalentAccordingToCompareTo(new File(fileA.getPath()));

    FontDetail detailB = family.getFonts().get(1);
    assertThat(detailB.getFamily()).isNotEqualTo(family);
    assertThat(detailB.getFamily().getName()).isEqualTo("fontb");
    assertThat(detailB.getWeight()).isEqualTo(400);
    assertThat(detailB.getWidth()).isEqualTo(100);
    assertThat(detailB.isItalics()).isTrue();
    assertThat(detailB.getStyleName()).isEqualTo("Regular Italic");
    assertThat(detailB.getFontUrl()).isEqualTo(FontFamily.FILE_PROTOCOL_START + PathUtil.toSystemDependentName(fileB.getPath()));
    assertThat(detailB.getCachedFontFile()).isEqualTo(new File(fileB.getPath()));
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

    String expectedUrl = "https://fonts.gstatic.com/s/roboto/v15/W5F8_SL0XFawnjxHGsZjJA.ttf";
    File expectedFile = makeFile(myFontPath, GOOGLE_FONT_AUTHORITY, "fonts", "roboto", "v15", "W5F8_SL0XFawnjxHGsZjJA.ttf");

    assertThat(fonts).containsExactly("roboto");
    FontFamily family = project.getFont("@font/roboto");
    assertThat(family).isNotNull();
    assertThat(family.getProvider()).isEqualTo(GoogleFontProvider.INSTANCE);
    assertThat(family.getFontSource()).isSameAs(FontFamily.FontSource.PROJECT);
    assertThat(family.getName()).isEqualTo("roboto");
    assertThat(family.getMenuName()).isEqualTo("roboto");
    assertThat(family.getMenu()).isEqualTo(expectedUrl);
    assertThat(family.getCachedMenuFile()).isEquivalentAccordingToCompareTo(expectedFile);
    assertThat(family.getFonts().size()).isEqualTo(1);

    FontDetail detail = family.getFonts().get(0);
    assertThat(detail.getFamily()).isSameAs(family);
    assertThat(detail.getWeight()).isEqualTo(400);
    assertThat(detail.getWidth()).isEqualTo(100);
    assertThat(detail.isItalics()).isFalse();
    assertThat(detail.getStyleName()).isEqualTo("Regular");
    assertThat(detail.getFontUrl()).isEqualTo(expectedUrl);
    assertThat(detail.getCachedFontFile()).isEquivalentAccordingToCompareTo(expectedFile);
  }

  public void testDownloadableFamilyFileWithParameters() {
    VirtualFile file = myFixture.copyFileToProject("fonts/roboto_bold.xml", "res/font/roboto_bold.xml");
    ProjectFonts project = createProjectFonts(file);
    List<String> fonts = project.getFonts().stream().map(FontFamily::getName).collect(Collectors.toList());

    String expectedUrl = "https://fonts.gstatic.com/s/robotocondensed/v15/mg0cGfGRUERshzBlvqxeAE2zk2RGRC3SlyyLLQfjS_8.ttf";
    File expectedFile =
      makeFile(myFontPath, GOOGLE_FONT_AUTHORITY, "fonts", "robotocondensed", "v15", "mg0cGfGRUERshzBlvqxeAE2zk2RGRC3SlyyLLQfjS_8.ttf");

    assertThat(fonts).containsExactly("roboto_bold");
    FontFamily family = project.getFont("@font/roboto_bold");
    assertThat(family).isNotNull();
    assertThat(family.getProvider()).isEqualTo(GoogleFontProvider.INSTANCE);
    assertThat(family.getFontSource()).isSameAs(FontFamily.FontSource.PROJECT);
    assertThat(family.getName()).isEqualTo("roboto_bold");
    assertThat(family.getMenuName()).isEqualTo("roboto_bold");
    assertThat(family.getMenu()).isEqualTo(expectedUrl);
    assertThat(family.getCachedMenuFile()).isEquivalentAccordingToCompareTo(expectedFile);
    assertThat(family.getFonts().size()).isEqualTo(1);

    FontDetail detail = family.getFonts().get(0);
    assertThat(detail.getFamily()).isSameAs(family);
    assertThat(detail.getWeight()).isEqualTo(700);
    assertThat(detail.getWidth()).isEqualTo(75);
    assertThat(detail.isItalics()).isTrue();
    assertThat(detail.getStyleName()).isEqualTo("Condensed Bold Italic");
    assertThat(detail.getFontUrl()).isEqualTo(expectedUrl);
    assertThat(detail.getCachedFontFile()).isEquivalentAccordingToCompareTo(expectedFile);
  }

  private ProjectFonts createProjectFonts(@NotNull VirtualFile file) {
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file);
    ResourceResolver resolver = configuration.getResourceResolver();
    assert resolver != null;
    return new ProjectFonts(resolver);
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
