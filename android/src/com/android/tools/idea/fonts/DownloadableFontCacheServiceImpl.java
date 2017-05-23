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
import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.fonts.FontLoader;
import com.android.ide.common.fonts.FontProvider;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.AndroidSdks;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.concurrent.ThreadSafe;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.android.ide.common.fonts.FontFamilyKt.FILE_PROTOCOL_START;
import static com.android.ide.common.fonts.FontFamilyKt.HTTPS_PROTOCOL_START;

/**
 * {@link DownloadableFontCacheServiceImpl} is a threadsafe implementation of {link {@link DownloadableFontCacheService}.
 */
@ThreadSafe
class DownloadableFontCacheServiceImpl extends FontLoader implements DownloadableFontCacheService {
  private static final String FONTS = "fonts";
  private static final String FONT = "font";
  private static final String V1 = "v1";

  private final SystemFonts mySystemFonts;
  @GuardedBy("getLock()")
  private final Map<String, FontDirectoryDownloadService> myDownloadServiceMap;

  @NotNull
  static DownloadableFontCacheServiceImpl getInstance() {
    return (DownloadableFontCacheServiceImpl)DownloadableFontCacheService.getInstance();
  }

  @Override
  @NotNull
  public List<FontFamily> getSystemFontFamilies() {
    return new ArrayList<>(mySystemFonts.getFontFamilies());
  }

  @Override
  @Nullable
  public FontFamily getSystemFont(@NotNull String name) {
    return mySystemFonts.getFont(name);
  }

  @Override
  @NotNull
  public FontFamily getDefaultSystemFont() {
    return mySystemFonts.getFontFamilies().iterator().next();
  }

  @Override
  @Nullable
  public File getCachedMenuFile(@NotNull FontFamily family) {
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
  public File getRelativeCachedMenuFile(@NotNull FontFamily family) {
    String menu = family.getMenu();
    if (!menu.startsWith(HTTPS_PROTOCOL_START)) {
      return null;
    }
    return getRelativeCachedFont(family.getProvider().getAuthority(), menu);
  }

  @Override
  @Nullable
  public File getCachedFontFile(@NotNull FontDetail font) {
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
  public File getRelativeFontFile(@NotNull FontDetail font) {
    String fontUrl = font.getFontUrl();
    if (!fontUrl.startsWith(HTTPS_PROTOCOL_START)) {
      return null;
    }
    return getRelativeCachedFont(font.getFamily().getProvider().getAuthority(), fontUrl);
  }

  @Override
  @Nullable
  @Language("XML")
  public String toXml(@NotNull FontFamily family) {
    StringBuilder output = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                             "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\">");
    boolean hasAnyDownloadedFonts = false;
    for (FontDetail detail : family.getFonts()) {
      File cachedFile = getCachedFontFile(detail);
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

  @Override
  public void download(@NotNull FontFamily family) {
    FontDownloadService.download(Collections.singletonList(family), false, null, null);
  }

  @Override
  @Nullable
  public Font loadMenuFont(@NotNull FontFamily fontFamily) {
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
  public Font loadDetailFont(@NotNull FontDetail fontDetail) {
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
    Collection<FontDirectoryDownloadService> services;
    synchronized (getLock()) {
      if (updateSdkHome()) {
        updateDownloadServices();
      }
      services = myDownloadServiceMap.values();
    }

    for (FontDirectoryDownloadService service : services) {
      service.refresh(success, failure);
    }
  }

  @VisibleForTesting
  DownloadableFontCacheServiceImpl() {
    myDownloadServiceMap = new HashMap<>();
    init();
    mySystemFonts = new SystemFonts(this);

    //noinspection AssignmentToStaticFieldFromInstanceMethod
    instance = this;
  }

  @TestOnly
  SystemFonts getSystemFonts() {
    return mySystemFonts;
  }

  private void init() {
    File initialSdkHome = locateSdkHome();
    if (initialSdkHome == null) {
      initialSdkHome = createTempSdk();
    }
    synchronized (getLock()) {
      clear(initialSdkHome);
      fontsLoaded();
      updateDownloadServices();
    }
  }

  @Nullable
  protected File locateSdkHome() {
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    return sdkHandler.getLocation();
  }

  @Override
  protected void loadFonts() {
    super.loadFonts();
    if (getFontFamilies().isEmpty()) {
      loadDirectory(FontProvider.GOOGLE_PROVIDER, FontDirectoryDownloadService.getFallbackResourceUrl(FontProvider.GOOGLE_PROVIDER));
    }
  }

  @Nullable
  private static File createTempSdk() {
    try {
      return FileUtil.createTempDirectory("temp", "sdk");
    }
    catch (IOException ex) {
      Logger.getInstance(DownloadableFontCacheServiceImpl.class).error(ex);
      return null;
    }
  }

  private boolean updateSdkHome() {
    synchronized (getLock()) {
      File newSdkHome = locateSdkHome();
      File oldSdkHome = getSdkHome();
      if (Objects.equals(newSdkHome, oldSdkHome)) {
        return false;
      }
      if (newSdkHome == null) {
        if (oldSdkHome != null && oldSdkHome.getName().startsWith("temp")) {
          return false;
        }
        newSdkHome = createTempSdk();
      }
      clear(newSdkHome);
      return true;
    }
  }

  @Nullable
  private File getCachedFont(@NotNull String authority, @NotNull String url) {
    File cachePath = getFontPath();
    if (cachePath == null) {
      return null;
    }
    return new File(cachePath, getRelativeCachedFont(authority, url).getPath());
  }

  private static File getRelativeCachedFont(@NotNull String authority, @NotNull String menu) {
    File providerPath = new File(authority);
    File fontsPath = new File(providerPath, FONTS);
    File fontPath = new File(fontsPath, getChildName(menu, 2, FONT));
    File versionPath = new File(fontPath, getChildName(menu, 1, V1));
    return new File(versionPath, getChildName(menu, 0, menu));
  }

  @NotNull
  private static String getChildName(@NotNull String menu, int fromLast, @NotNull String defaultName) {
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

  @NotNull
  static String convertNameToFilename(@NotNull String name) {
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
          myDownloadServiceMap.put(provider.getAuthority(), new FontDirectoryDownloadService(this, provider, fontPath));
        }
      }
    }
  }
}
