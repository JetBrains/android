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

import static com.android.ide.common.fonts.FontFamilyKt.FILE_PROTOCOL_START;
import static com.android.ide.common.fonts.FontProviderKt.GOOGLE_FONT_AUTHORITY;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.fonts.FontProvider;
import com.android.ide.common.fonts.FontSource;
import com.android.testutils.TestUtils;
import com.android.tools.fonts.DownloadableFontCacheServiceImpl;
import com.android.tools.fonts.FontDownloader;
import com.android.tools.fonts.ProjectFonts;
import com.android.tools.res.CacheableResourceRepository;
import com.android.tools.res.FolderResourceRepository;
import com.android.tools.res.ResourceRepositoryManager;
import com.android.tools.res.SingleRepoResourceRepositoryManager;
import com.google.common.base.Joiner;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProjectFontsTest {
  @Rule
  public final TemporaryFolder tempFolder = new TemporaryFolder();
  private File resFolder;
  private Path fontResFolderPath;
  private final Path fontsPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData/fonts");
  private DownloadableFontCacheServiceImpl fontService;

  @Before
  public void setUp() throws IOException {
    resFolder = tempFolder.newFolder("res");
    fontResFolderPath = resFolder.toPath().resolve("font");
    fontResFolderPath.toFile().mkdirs();
  }

  @Test
  public void testEmbeddedFontFile() throws IOException {
    Path fontFilePath = fontResFolderPath.resolve("a_bee_zee_regular.ttf");
    Files.copy(fontsPath.resolve("customfont.ttf"), fontFilePath);
    ProjectFonts project = createProjectFonts(resFolder);

    List<FontFamily> fonts = project.getFonts();

    assertThat(fonts.size()).isEqualTo(1);
    FontFamily family = assertFontFamily(fonts.get(0), "a_bee_zee_regular", FontProvider.EMPTY_PROVIDER,
                                         FILE_PROTOCOL_START + fontFilePath,
                                         fontFilePath.toFile());

    assertThat(family.getFonts().size()).isEqualTo(1);

    assertFontDetail(family.getFonts().get(0), "a_bee_zee_regular", "Regular", 400, 100, false,
                     FILE_PROTOCOL_START + fontFilePath, fontFilePath.toFile());
  }

  @Test
  public void testCompoundFamilyFile() throws IOException {
    Path fileA = copyFontToResources("customfont.ttf", "fonta.ttf");
    Path fileB = copyFontToResources("customfont.ttf", "fontb.ttf");
    copyFontToResources("my_font_family.xml", "my_font_family.xml");
    ProjectFonts project = createProjectFonts(resFolder);

    List<String> fonts = project.getFonts().stream().map(FontFamily::getName).toList();
    assertThat(fonts).containsExactly("fonta", "fontb", "my_font_family");

    FontFamily family = assertFontFamily(project.getFont("@font/my_font_family"), "my_font_family", FontProvider.EMPTY_PROVIDER,
                                         FILE_PROTOCOL_START + fileA,
                                         fileA.toFile());

    assertThat(family.getFonts().size()).isEqualTo(2);

    assertFontDetail(family.getFonts().get(0), "fontb", "Regular Italic", 400, 100, true,
                     FILE_PROTOCOL_START + fileB, fileB.toFile());
    assertFontDetail(family.getFonts().get(1), "fonta", "Regular", 400, 100, false,
                     FILE_PROTOCOL_START + fileA, fileA.toFile());
  }

  @Test
  public void testCompoundFamilyFileWithCircularReferences() throws IOException {
    copyFontToResources("customfont.ttf", "fonta.ttf");
    copyFontToResources("customfont.ttf", "fontb.ttf");
    copyFontToResources("my_circular_font_family_1.xml", "my_circular_font_family_1.xml");
    copyFontToResources("my_circular_font_family_1.xml", "my_circular_font_family_2.xml");
    ProjectFonts project = createProjectFonts(resFolder);
    List<String> fonts = project.getFonts().stream().map(FontFamily::getName).toList();

    assertThat(fonts).containsExactly("fonta", "fontb", "my_circular_font_family_1", "my_circular_font_family_2");
    assertUnresolvedFont(project.getFont("@font/my_circular_font_family_1"), "my_circular_font_family_1");
    assertUnresolvedFont(project.getFont("@font/my_circular_font_family_2"), "my_circular_font_family_2");
  }

  @Test
  public void testDownloadableFamilyFile() throws IOException {
    copyFontToResources("roboto.xml", "roboto.xml");
    ProjectFonts project = createProjectFonts(resFolder);

    List<String> fonts = project.getFonts().stream().map(FontFamily::getName).toList();
    assertThat(fonts).containsExactly("roboto");

    FontFamily family = assertFontFamily(project.getFont("@font/roboto"), "roboto", "roboto", "v16", "W5F8_SL0XFawnjxHGsZjJA.ttf");
    assertThat(family.getFonts().size()).isEqualTo(1);

    assertFontDetail(family.getFonts().get(0), "Roboto", "Regular", 400, 100, false, "roboto", "v16", "W5F8_SL0XFawnjxHGsZjJA.ttf");
  }

  @Test
  public void testDownloadableFamilyFileWithParameters() throws IOException {
    copyFontToResources("roboto_bold.xml", "roboto_bold.xml");
    ProjectFonts project = createProjectFonts(resFolder);

    List<String> fonts = project.getFonts().stream().map(FontFamily::getName).toList();
    assertThat(fonts).containsExactly("roboto_bold");
    FontFamily family = assertFontFamily(project.getFont("@font/roboto_bold"), "roboto_bold",
                                         "robotocondensed", "v14", "mg0cGfGRUERshzBlvqxeAE2zk2RGRC3SlyyLLQfjS_8.ttf");
    assertThat(family.getFonts().size()).isEqualTo(1);

    assertFontDetail(family.getFonts().get(0), "Roboto", "Condensed Bold Italic", 700, 75, true,
                     "robotocondensed", "v14", "mg0cGfGRUERshzBlvqxeAE2zk2RGRC3SlyyLLQfjS_8.ttf");
  }

  @Test
  public void testDownloadableFamilyFileWithMultipleFonts() throws IOException {
    copyFontToResources("misc.xml", "misc.xml");
    ProjectFonts project = createProjectFonts(resFolder);

    List<String> fonts = project.getFonts().stream().map(FontFamily::getName).toList();
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

  @Test
  public void testNonExistingXmlFile() throws Exception {
    copyFontToResources("misc.xml", "misc.xml");
    ProjectFonts project = createProjectFonts(resFolder);
    fontResFolderPath.resolve("misc.xml").toFile().delete();
    assertUnresolvedFont(project.getFont("@font/misc"), "misc");
  }

  private Path copyFontToResources(@NotNull String fontName, @NotNull String resFontName) throws IOException {
    Path resFontPath = fontResFolderPath.resolve(resFontName);
    Files.copy(fontsPath.resolve(fontName), resFontPath);
    return resFontPath;
  }

  private ProjectFonts createProjectFonts(@NotNull File resFolder) {
    CacheableResourceRepository resRepo = new FolderResourceRepository(resFolder);
    ResourceRepositoryManager manager = new SingleRepoResourceRepositoryManager(resRepo);
    fontService = new DownloadableFontCacheServiceImpl(FontDownloader.NOOP_FONT_DOWNLOADER, (Supplier<File>)() -> null) { };
    return new ProjectFonts(fontService, manager);
  }

  @NotNull
  private FontFamily assertFontFamily(@Nullable FontFamily family,
                                @NotNull String expectedFontName,
                                @NotNull String expectedFolder1,
                                @NotNull String expectedFolder2,
                                @NotNull String expectedFilename) {
    String expectedUrl = Joiner.on('/').join("https://fonts.gstatic.com/s", expectedFolder1, expectedFolder2, expectedFilename);
    File expectedFile = Path.of(fontService.getFontPath().getAbsolutePath(), GOOGLE_FONT_AUTHORITY, "fonts", expectedFolder1, expectedFolder2, expectedFilename).toFile();
    return assertFontFamily(family, expectedFontName, FontProvider.GOOGLE_PROVIDER, expectedUrl, expectedFile);
  }

  @NotNull
  private FontFamily assertFontFamily(@Nullable FontFamily family,
                                             @NotNull String expectedFontName,
                                             @NotNull FontProvider expectedProvider,
                                             @NotNull String expectedUrl,
                                             @NotNull File expectedFile) {
    expectedUrl = expectedUrl.replace('\\', '/');
    assertThat(family).isNotNull();
    assertThat(family.getProvider()).isEqualTo(expectedProvider);
    assertThat(family.getFontSource()).isSameAs(FontSource.PROJECT);
    assertThat(family.getName()).isEqualTo(expectedFontName);
    assertThat(family.getMenuName()).isEqualTo(expectedFontName);
    assertThat(family.getMenu()).isEqualTo(expectedUrl);
    assertThat(fontService.getCachedMenuFile(family)).isEquivalentAccordingToCompareTo(expectedFile);
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
    File file = Path.of(fontService.getFontPath().getAbsolutePath(), GOOGLE_FONT_AUTHORITY, "fonts", expectedFolder1, expectedFolder2, expectedFilename).toFile();
    assertFontDetail(detail, expectedFontName, expectedStyleName, expectedWeight, expectedWidth, expectedItalics, url, file);
  }

  private void assertFontDetail(@NotNull FontDetail detail,
                                       @NotNull String expectedFontName,
                                       @NotNull String expectedStyleName,
                                       int expectedWeight,
                                       int expectedWidth,
                                       boolean expectedItalics,
                                       @NotNull String expectedUrl,
                                       @NotNull File expectedFile) {
    expectedUrl = expectedUrl.replace('\\', '/');
    assertThat(detail.getFamily().getName()).isEqualTo(expectedFontName);
    assertThat(detail.getWeight()).isEqualTo(expectedWeight);
    assertThat(detail.getWidth()).isEqualTo(expectedWidth);
    assertThat(detail.getItalics()).isEqualTo(expectedItalics);
    assertThat(detail.getStyleName()).isEqualTo(expectedStyleName);
    assertThat(detail.getFontUrl()).isEqualTo(expectedUrl);
    assertThat(fontService.getCachedFontFile(detail)).isEquivalentAccordingToCompareTo(expectedFile);
  }

  private void assertUnresolvedFont(@Nullable FontFamily family, @NotNull String expectedName) {
    assertThat(family).isNotNull();
    assertThat(family.getProvider()).isEqualTo(FontProvider.EMPTY_PROVIDER);
    assertThat(family.getFontSource()).isSameAs(FontSource.PROJECT);
    assertThat(family.getName()).isEqualTo(expectedName);
    assertThat(family.getMenuName()).isEqualTo(expectedName);
    assertThat(family.getMenu()).isEmpty();
    assertThat(fontService.getCachedMenuFile(family)).isNull();
    assertThat(family.getFonts().size()).isEqualTo(0);
  }
}
