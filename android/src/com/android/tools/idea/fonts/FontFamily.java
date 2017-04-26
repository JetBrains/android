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

import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.Immutable;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A {@link FontFamily} represent a font with a name and a list of font files described as a {@link FontDetail}.
 * Each family comes from a {@link FontSource}. For {@link FontSource#DOWNLOADABLE} fonts each family was provided
 * by a specific {@link FontProvider}.
 */
@Immutable
public class FontFamily implements Comparable<FontFamily> {
  public enum FontSource {
    SYSTEM,       // The font is a system font i.e. one of the 8 predefined fonts in the Android platform.
    PROJECT,      // The font is a reference to a font created in a font resource file in the project.
    DOWNLOADABLE, // The font is a reference to a font in a font directory from a specific font provider (e.g. Google Fonts).
    LOOKUP,       // Fake font family used to lookup the real font reference.
    HEADER        // Fake font family used in UI to refer to a header in a list of fonts.
  }

  public static final String FILE_PROTOCOL_START = URLUtil.FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR;
  public static final String HTTPS_PROTOCOL_START = "https" + URLUtil.SCHEME_SEPARATOR;

  private final FontProvider myProvider;
  private final FontSource mySource;
  private final String myName;
  private final String myMenu;
  private final String myMenuName;
  private final List<FontDetail> myFonts;

  public FontFamily(@NotNull FontProvider provider,
                    @NotNull FontSource source,
                    @NotNull String name,
                    @NotNull String menu,
                    @Nullable String menuName,
                    @NotNull List<FontDetail.Builder> fonts) {
    myProvider = provider;
    mySource = source;
    myName = name;
    myMenu = menu;
    myMenuName = StringUtil.isEmpty(menuName) ? name : menuName;
    myFonts = build(fonts);
  }

  /**
   * Special use for creating synonyms in font-family files with references to other fonts.
   */
  private FontFamily(@NotNull FontProvider provider,
                     @NotNull FontSource source,
                     @NotNull String name,
                     @NotNull String menu,
                     @Nullable String menuName,
                     @NotNull ImmutableList<FontDetail> fonts) {
    myProvider = provider;
    mySource = source;
    myName = name;
    myMenu = menu;
    myMenuName = StringUtil.isEmpty(menuName) ? name : menuName;
    myFonts = fonts;
  }

  public static FontFamily createCompound(@NotNull FontProvider provider,
                                          @NotNull FontSource source,
                                          @NotNull String name,
                                          @NotNull String menu,
                                          @NotNull Collection<FontDetail> fonts) {
    return new FontFamily(provider, source, name, menu, null, ImmutableList.copyOf(fonts));
  }

  @NotNull
  public FontProvider getProvider() {
    return myProvider;
  }

  @NotNull
  public FontSource getFontSource() {
    return mySource;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getMenu() {
    return myMenu;
  }

  @NotNull
  public String getMenuName() {
    return myMenuName;
  }

  @NotNull
  public List<FontDetail> getFonts() {
    return myFonts;
  }

  /**
   * Returns the possibly cached font file.
   * Or <code>null</code> if this family has errors in the specification.
   */
  @Nullable
  public File getCachedMenuFile() {
    if (myMenu.isEmpty()) {
      return null;
    }
    if (myMenu.startsWith(FILE_PROTOCOL_START)) {
      return new File(myMenu.substring(FILE_PROTOCOL_START.length()));
    }
    DownloadableFontCacheServiceImpl service = DownloadableFontCacheServiceImpl.getInstance();
    return service.getCachedFont(myProvider.getAuthority(), myMenu);
  }

  /**
   * Returns a file relative to the font cache path.
   * Or <code>null</code> if this is not a valid downloadable file.
   */
  @Nullable
  public File getRelativeCachedMenuFile() {
    if (!myMenu.startsWith(HTTPS_PROTOCOL_START)) {
      return null;
    }
    DownloadableFontCacheServiceImpl service = DownloadableFontCacheServiceImpl.getInstance();
    return service.getRelativeCachedFont(myProvider.getAuthority(), myMenu);
  }

  @Override
  public int compareTo(@NotNull FontFamily other) {
    return Comparator
      .comparing(FontFamily::getName)
      .thenComparing(FontFamily::getProvider)
      .compare(this, other);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myProvider, myName);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof FontFamily)) {
      return false;
    }
    FontFamily otherFamily = (FontFamily)other;
    return myProvider.equals(otherFamily.myProvider) &&
           myName.equals(otherFamily.myName);
  }

  private ImmutableList<FontDetail> build(@NotNull List<FontDetail.Builder> fonts) {
    ImmutableList.Builder<FontDetail> details = ImmutableList.builder();
    for (FontDetail.Builder font : fonts) {
      if (font.myFontUrl != null) {
        details.add(new FontDetail(this, font));
      }
    }
    return details.build();
  }

  @Nullable
  @Language("XML")
  public String toXml() {
    StringBuilder output = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                             "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\">");
    boolean hasAnyDownloadedFonts = false;
    for (FontDetail detail : getFonts()) {
      File cachedFile = detail.getCachedFontFile();
      if (cachedFile == null || !cachedFile.exists()) {
        continue;
      }
      hasAnyDownloadedFonts = true;
      output.append(String.format("<font android:font=\"%1$s\" android:fontStyle=\"%2$s\" android:fontWeight=\"%3$d\" />",
                                  cachedFile.getAbsolutePath(),
                                  detail.getFontStyle(),
                                  detail.getWeight()));
    }
    if (!hasAnyDownloadedFonts) {
      return null;
    }
    output.append("</font-family>");

    return output.toString();
  }

  public void download() {
    download(null, null);
  }

  // TODO: Replace the runnables with a Future<>
  @VisibleForTesting
  void download(@Nullable Runnable success, @Nullable Runnable failure) {
    FontDownloadService.download(Collections.singletonList(this), false, success, failure);
  }
}
