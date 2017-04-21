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
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.download.impl.DownloadableFileServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.fonts.FontFamily.FontSource.DOWNLOADABLE;
import static com.android.tools.idea.fonts.FontFamily.FontSource.PROJECT;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class FontFamilyTest extends FontTestCase {

  public void testConstructorAndGetters() {
    FontFamily family = createFontFamily(GoogleFontProvider.INSTANCE, DOWNLOADABLE, "Roboto", "https://fonts.com/roboto/v15/xyz.ttf", null);
    assertThat(family.getProvider()).isEqualTo(GoogleFontProvider.INSTANCE);
    assertThat(family.getFontSource()).isEqualTo(DOWNLOADABLE);
    assertThat(family.getName()).isEqualTo("Roboto");
    assertThat(family.getMenu()).isEqualTo("https://fonts.com/roboto/v15/xyz.ttf");
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
    FontFamily family = createFontFamily(GoogleFontProvider.INSTANCE, DOWNLOADABLE, "Roboto", "https://fonts.com/roboto/v15/xyz.ttf", null);
    File expected = makeFile(myFontPath, GoogleFontProvider.GOOGLE_FONT_AUTHORITY, "fonts", "roboto", "v15", "xyz.ttf");
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
                          Collections.singletonList(new FontDetail.Builder(400, 100, false, "https://fonts.com/roboto/v15/qrs.ttf", null)));
  }

  public void testFontFamilyXml() throws IOException {
    FontDetail.Builder builder = new FontDetail.Builder();
    builder.myItalics = true;
    builder.myStyleName = "Regular Italic";
    File fakeTtfFile = FileUtil.createTempFile("fontFamily", ".ttf");
    // The font needs to exist to be added to the generated file so create a fake cache file
    builder.myFontUrl = "file://" + fakeTtfFile.getAbsolutePath();
    FontFamily embeddedFontFamily =
      new FontFamily(FontProvider.EMPTY_PROVIDER, PROJECT, "embeddedFont", "file://test/path", null, Collections.singletonList(builder));

    FontFamily compoundFontFamily = FontFamily.createCompound(GoogleFontProvider.INSTANCE,
                                                              DOWNLOADABLE,
                                                              "myFont",
                                                              "Menu name",
                                                              ImmutableList.of(
                                                                embeddedFontFamily.getFonts().get(0)
                                                              ));
    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\">" +
                 "<font android:font=\"" + fakeTtfFile.getAbsolutePath() + "\" " +
                 "android:fontStyle=\"italic\" " +
                 "android:fontWeight=\"400\" />" +
                 "</font-family>",
                 compoundFontFamily.toXml());
  }

  public void testToXmlWithNoCachedFont() {
    FontFamily family = createFontFamily(GoogleFontProvider.INSTANCE, DOWNLOADABLE, "Roboto", "https://fonts.com/roboto/v15/xyz.ttf", null);
    String xml = family.toXml();
    assertThat(xml).isNull();
  }

  public void testDownload() throws Exception {
    DownloadableFileService fileService = mockFileService();
    FontFamily family = createFontFamily(GoogleFontProvider.INSTANCE, DOWNLOADABLE, "Roboto", "https://fonts.com/roboto/v15/xyz.ttf", null);
    family.download(() -> checkFilesDownloaded(fileService), () -> fail("Error happened during download"));
  }

  private static void checkFilesDownloaded(@NotNull DownloadableFileService fileService) {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<DownloadableFileDescription>> filesCaptor = ArgumentCaptor.forClass(List.class);
    verify(fileService).createDownloader(filesCaptor.capture(), anyString());
    List<DownloadableFileDescription> files = filesCaptor.getValue();
    assertThat(files.size()).isEqualTo(2);
    assertThat(files.get(0).getDownloadUrl()).isEqualTo("https://fonts.com/roboto/v15/xyz.ttf");
    assertThat(files.get(1).getDownloadUrl()).isEqualTo("https://fonts.com/roboto/v15/qrs.ttf");
  }

  private DownloadableFileService mockFileService() {
    FileDownloader downloader = mock(FileDownloader.class);
    DownloadableFileService fileService = mock(DownloadableFileServiceImpl.class);
    doCallRealMethod().when(fileService).createFileDescription(anyString(), anyString());
    registerApplicationComponent(DownloadableFileService.class, fileService);
    when(fileService.createDownloader(anyList(), anyString())).thenReturn(downloader);
    return fileService;
  }
}
