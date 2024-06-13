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

import static com.android.ide.common.fonts.FontFamilyKt.FILE_PROTOCOL_START;
import static com.android.ide.common.fonts.FontFamilyKt.HTTPS_PROTOCOL_START;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.TestOnly;
import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.fonts.FontsFolderProvider;
import com.android.ide.common.fonts.FontLoader;
import com.android.ide.common.fonts.FontProvider;
import com.android.ide.common.fonts.SdkFontsFolderProvider;
import com.android.tools.environment.Logger;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.intellij.lang.annotations.Language;

/**
 * {@link DownloadableFontCacheServiceImpl} is a threadsafe implementation of {@link DownloadableFontCacheService}.
 */
public class DownloadableFontCacheServiceImpl extends FontLoader implements DownloadableFontCacheService {
  private static final String FONTS = "fonts";
  private static final String FONT = "font";
  private static final String V1 = "v1";

  private final SystemFonts mySystemFonts;
  @GuardedBy("getLock()")
  private final Map<String, FontDirectoryDownloader> myDownloadServiceMap;

  private final FontDownloader myFontDownloader;

  private final FontsFolderProvider myFontsFolderProvider;

  @Override
  @NonNull
  public List<FontFamily> getSystemFontFamilies() {
    return new ArrayList<>(mySystemFonts.getFontFamilies());
  }

  @Override
  @Nullable
  public FontFamily getSystemFont(@NonNull String name) {
    return mySystemFonts.getFont(name);
  }

  @Override
  @NonNull
  public FontFamily getDefaultSystemFont() {
    return mySystemFonts.getFontFamilies().iterator().next();
  }

  @Override
  @Nullable
  public File getCachedMenuFile(@NonNull FontFamily family) {
    String menu = family.getMenu();
    if (menu.isEmpty()) {
      return null;
    }
    if (menu.startsWith(FILE_PROTOCOL_START)) {
      return new File(menu.substring(FILE_PROTOCOL_START.length()));
    }
    return getCachedFont(family.getProvider().getAuthority(), menu);
  }

  /**
   * Returns a file relative to the font cache path.
   * Or {@code null} if this is not a valid downloadable file.
   */
  @Nullable
  public File getRelativeCachedMenuFile(@NonNull FontFamily family) {
    String menu = family.getMenu();
    if (!menu.startsWith(HTTPS_PROTOCOL_START)) {
      return null;
    }
    return getRelativeCachedFont(family.getProvider().getAuthority(), menu);
  }

  @Override
  @Nullable
  public File getCachedFontFile(@NonNull FontDetail font) {
    String fontUrl = font.getFontUrl();
    if (fontUrl.isEmpty()) {
      return null;
    }
    if (fontUrl.startsWith(FILE_PROTOCOL_START)) {
      return new File(fontUrl.substring(FILE_PROTOCOL_START.length()));
    }
    return getCachedFont(font.getFamily().getProvider().getAuthority(), fontUrl);
  }

  @Nullable
  public File getRelativeFontFile(@NonNull FontDetail font) {
    String fontUrl = font.getFontUrl();
    if (!fontUrl.startsWith(HTTPS_PROTOCOL_START)) {
      return null;
    }
    return getRelativeCachedFont(font.getFamily().getProvider().getAuthority(), fontUrl);
  }

  @Override
  @Nullable
  @Language("XML")
  public String toXml(@NonNull FontFamily family) {
    StringBuilder output = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                             "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\">");
    boolean hasAnyDownloadedFonts = false;
    for (FontDetail detail : family.getFonts()) {
      File cachedFile = getCachedFontFile(detail);
      if (cachedFile == null || !cachedFile.exists()) {
        continue;
      }
      hasAnyDownloadedFonts = true;
      output.append(String.format("<font android:font=\"%s\"", cachedFile.getAbsolutePath()));
      if (detail.getHasExplicitStyle()) {
        output.append(String.format(" android:fontStyle=\"%s\"", detail.getFontStyle()));
      }
      int weight = detail.getWeight();
      if (weight != -1) {
        output.append(String.format(Locale.US, " android:fontWeight=\"%d\"", weight));
      }
      output.append(" />");
    }
    if (!hasAnyDownloadedFonts) {
      return null;
    }
    output.append("</font-family>");

    return output.toString();
  }

  @Override
  public CompletableFuture<Boolean> download(@NonNull FontFamily family) {
    CompletableFuture<Boolean> success = new CompletableFuture<>();
    myFontDownloader.download(Collections.singletonList(family), false, () -> success.complete(true), () -> success.complete(false));
    return success;
  }

  @Override
  @Nullable
  public Font loadMenuFont(@NonNull FontFamily fontFamily) {
    File file = getCachedMenuFile(fontFamily);
    if (file != null && file.exists()) {
      try {
        return Font.createFont(Font.TRUETYPE_FONT, file);
      }
      catch (FontFormatException | IOException ex) {
        Logger.getInstance(DownloadableFontCacheServiceImpl.class).warn("Could not load font: " + fontFamily.getName(), ex);
      }
    }
    return null;
  }

  @Override
  @Nullable
  public Font loadDetailFont(@NonNull FontDetail fontDetail) {
    File file = getCachedFontFile(fontDetail);
    if (file != null && file.exists()) {
      try {
        return Font.createFont(Font.TRUETYPE_FONT, file);
      }
      catch (FontFormatException | IOException ex) {
        Logger.getInstance(DownloadableFontCacheServiceImpl.class).warn("Could not load font: " + fontDetail.getFamily().getName(), ex);
      }
    }
    return null;
  }

  @Override
  public void refresh(@Nullable Runnable success, @Nullable Runnable failure) {
    Collection<FontDirectoryDownloader> services;
    synchronized (getLock()) {
      if (updateFontsFolder()) {
        updateDownloadServices();
      }
      services = myDownloadServiceMap.values();
    }

    for (FontDirectoryDownloader service : services) {
      service.refreshFonts(success, failure);
    }
  }

  @Override
  @Nullable
  public File getFontCachePath() {
    return super.getFontPath();
  }

  protected DownloadableFontCacheServiceImpl(
    @NonNull FontDownloader fontDownloader,
    @NonNull FontsFolderProvider fontsFolderProvider
  ) {
    myDownloadServiceMap = new HashMap<>();
    myFontDownloader = fontDownloader;
    myFontsFolderProvider = fontsFolderProvider;
    init();
    mySystemFonts = new SystemFonts(this);

    //noinspection AssignmentToStaticFieldFromInstanceMethod
    instance = this;
  }

  protected DownloadableFontCacheServiceImpl(
    @NonNull FontDownloader fontDownloader,
    @NonNull Supplier<File> sdkHomeProvider
  ) {
    this(fontDownloader, new SdkFontsFolderProvider(sdkHomeProvider));
  }

  @TestOnly
  public SystemFonts getSystemFonts() {
    return mySystemFonts;
  }

  private void init() {
    File fontFolder = myFontsFolderProvider.getFontsFolder();
    synchronized (getLock()) {
      setFontPath(fontFolder);
      fontsLoaded();
      updateDownloadServices();
    }
  }

  @Override
  protected void loadFonts() {
    super.loadFonts();
    if (getFontFamilies().isEmpty()) {
      loadDirectory(FontProvider.GOOGLE_PROVIDER, Fonts.getFallbackResourceUrl(FontProvider.GOOGLE_PROVIDER));
    }
  }

  private boolean updateFontsFolder() {
    synchronized (getLock()) {
      File newFontsFolder = myFontsFolderProvider.getFontsFolder();
      File oldFontsFolder = getFontPath();
      if (Objects.equals(newFontsFolder, oldFontsFolder)) {
        return false;
      }
      setFontPath(newFontsFolder);
      return true;
    }
  }

  @Nullable
  private File getCachedFont(@NonNull String authority, @NonNull String url) {
    File cachePath = getFontPath();
    if (cachePath == null) {
      return null;
    }
    return new File(cachePath, getRelativeCachedFont(authority, url).getPath());
  }

  private static File getRelativeCachedFont(@NonNull String authority, @NonNull String menu) {
    File providerPath = new File(authority);
    File fontsPath = new File(providerPath, FONTS);
    File fontPath = new File(fontsPath, getChildName(menu, 2, FONT));
    File versionPath = new File(fontPath, getChildName(menu, 1, V1));
    return new File(versionPath, getChildName(menu, 0, menu));
  }

  @NonNull
  private static String getChildName(@NonNull String menu, int fromLast, @NonNull String defaultName) {
    int lastIndex = menu.length();
    int prevIndex = menu.lastIndexOf('/', lastIndex - 1);
    while (fromLast > 0) {
      lastIndex = prevIndex;
      prevIndex = menu.lastIndexOf('/', lastIndex - 1);
      fromLast--;
    }
    if (prevIndex < 0) {
      return defaultName;
    }
    return menu.substring(prevIndex + 1, lastIndex);
  }

  @NonNull
  public static String convertNameToFilename(@NonNull String name) {
    StringBuilder builder = new StringBuilder();
    boolean previousUnderscore = true;
    for (char character : name.toCharArray()) {
      if (Character.isUpperCase(character)) {
        builder.append(Character.toLowerCase(character));
        previousUnderscore = false;
      }
      else if (Character.isLowerCase(character) || Character.isDigit(character)) {
        builder.append(character);
        previousUnderscore = false;
      }
      else {
        if (!previousUnderscore) {
          builder.append('_');
          previousUnderscore = true;
        }
      }
    }
    return builder.toString();
  }

  private void updateDownloadServices() {
    synchronized (getLock() ) {
      myDownloadServiceMap.clear();
      File fontPath = getFontPath();
      if (fontPath != null) {
        for (FontProvider provider : getProviders().values()) {
          myDownloadServiceMap.put(provider.getAuthority(), myFontDownloader.createFontDirectoryDownloader(this, provider, fontPath));
        }
      }
    }
  }
}
