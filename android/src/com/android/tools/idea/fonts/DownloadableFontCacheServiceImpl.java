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
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.AndroidSdks;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.annotation.concurrent.ThreadSafe;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

/**
 * {@link DownloadableFontCacheServiceImpl} is a threadsafe implementation of {link {@link DownloadableFontCacheService}.
 */
@ThreadSafe
class DownloadableFontCacheServiceImpl implements DownloadableFontCacheService {
  private static final String PROVIDERS = "providers";
  private static final String PROVIDERS_FILENAME = "provider_directory.xml";
  private static final String FONTS = "fonts";
  private static final String FONT = "font";
  private static final String V1 = "v1";

  private final SystemFonts mySystemFonts;
  private final Object myLock;
  @GuardedBy("myLock")
  private final List<FontProvider> myProviders;
  @GuardedBy("myLock")
  private final Map<String, FontDirectoryDownloadService> myDownloadServiceMap;
  @GuardedBy("myLock")
  private final List<FontFamily> mySortedFontFamilies;
  @GuardedBy("myLock")
  private final Map<FontFamily, FontFamily> myFontFamilyLookup;
  @GuardedBy("myLock")
  private File myFontPath;
  @GuardedBy("myLock")
  private boolean myUpdatedFontPath;

  @NotNull
  static DownloadableFontCacheServiceImpl getInstance() {
    return (DownloadableFontCacheServiceImpl)DownloadableFontCacheService.getInstance();
  }

  @Override
  @NotNull
  public List<FontFamily> getFontFamilies() {
    synchronized (myLock) {
      return new ArrayList<>(mySortedFontFamilies);
    }
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
  public Font loadMenuFont(@NotNull FontFamily fontFamily) {
    File file = fontFamily.getCachedMenuFile();
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
    File file = fontDetail.getCachedFontFile();
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
    synchronized (myLock) {
      updateFontPath();
      if (myUpdatedFontPath) {
        updateProvidersFromFile();
        updateDownloadServices();
      }
      services = myDownloadServiceMap.values();
    }

    for (FontDirectoryDownloadService service : services) {
      service.refresh(success, failure);
    }
  }

  @Nullable
  FontFamily lookup(@NotNull FontFamily family) {
    synchronized (myLock) {
      return myFontFamilyLookup.get(family);
    }
  }

  @VisibleForTesting
  DownloadableFontCacheServiceImpl() {
    myLock = new Object();
    myProviders = new ArrayList<>();
    myDownloadServiceMap = new HashMap<>();
    mySortedFontFamilies = new ArrayList<>();
    myFontFamilyLookup = new HashMap<>();
    init();
    mySystemFonts = new SystemFonts(this);
  }

  @TestOnly
  SystemFonts getSystemFonts() {
    return mySystemFonts;
  }

  private void init() {
    File fontPath = locateInitialFontCache();
    synchronized (myLock) {
      myFontPath = fontPath;
      updateProvidersFromFile();
      updateDownloadServices();

      for (FontDirectoryDownloadService service : myDownloadServiceMap.values()) {
        service.loadLatest();
      }
    }
  }

  @Override
  @Nullable
  public File getFontPath() {
    synchronized (myLock) {
      return myFontPath;
    }
  }

  @Nullable
  private File locateInitialFontCache() {
    File fontCacheInSDK = locateFontCacheInSDK();
    return fontCacheInSDK != null ? fontCacheInSDK : createTempFontCache();
  }

  @Nullable
  @VisibleForTesting
  protected File locateFontCacheInSDK() {
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    File sdkLocation = sdkHandler.getLocation();
    if (sdkLocation == null) {
      return null;
    }
    File fonts = new File(sdkLocation, "fonts");
    FileUtil.createDirectory(fonts);
    return fonts;
  }

  @Nullable
  private static File createTempFontCache() {
    try {
      return FileUtil.createTempDirectory("temp", "font");
    }
    catch (IOException ex) {
      Logger.getInstance(DownloadableFontCacheServiceImpl.class).error(ex);
      return null;
    }
  }

  private void updateFontPath() {
    synchronized (myLock) {
      File fontCacheInSDK = locateFontCacheInSDK();
      if (fontCacheInSDK != null) {
        myUpdatedFontPath = !FileUtil.filesEqual(fontCacheInSDK, myFontPath);
        myFontPath = fontCacheInSDK;
        return;
      }
      if (myFontPath != null && myFontPath.getName().startsWith("temp")) {
        return;
      }
      myUpdatedFontPath = true;
      myFontPath = createTempFontCache();
    }
  }

  @Nullable
  File getCachedFont(@NotNull String authority, @NotNull String url) {
    File cachePath = getFontPath();
    if (cachePath == null) {
      return null;
    }
    return new File(cachePath, getRelativeCachedFont(authority, url).getPath());
  }

  File getRelativeCachedFont(@NotNull String authority, @NotNull String menu) {
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

  /**
   * Perform a merge sort of the existing fonts and the list form a newly completed font directory download.
   * This method will never remove fonts, but only add newly found fonts. If there are multiple providers this
   * method will be called after each completed download.
   * Removed fonts will only be detected after the next restart of the IDE.
   * The result is a list of fonts ordered by font name. Fonts from different providers will be mixed together.
   * @param fontFamilies the newly list of font families from a provider sorted by name.
   */
  void mergeFonts(@NotNull List<FontFamily> fontFamilies) {
    synchronized (myLock) {
      List<FontFamily> existingFonts = !myUpdatedFontPath ? new ArrayList<>(mySortedFontFamilies) : Collections.emptyList();
      mySortedFontFamilies.clear();
      Iterator<FontFamily> existing = existingFonts.iterator();
      Iterator<FontFamily> loaded = fontFamilies.iterator();
      FontFamily existingFont = next(existing);
      FontFamily loadedFont = next(loaded);
      while (existingFont != null && loadedFont != null) {
        switch (existingFont.compareTo(loadedFont)) {
          case 1:
            mySortedFontFamilies.add(loadedFont);
            myFontFamilyLookup.put(loadedFont, loadedFont);
            loadedFont = next(loaded);
            break;
          case -1:
            mySortedFontFamilies.add(existingFont);
            existingFont = next(existing);
            break;
          default:
            mySortedFontFamilies.add(loadedFont);
            myFontFamilyLookup.put(loadedFont, loadedFont);
            existingFont = next(existing);
            loadedFont = next(loaded);
            break;
        }
      }
      while (existingFont != null) {
        mySortedFontFamilies.add(existingFont);
        existingFont = next(existing);
      }
      while (loadedFont != null) {
        mySortedFontFamilies.add(loadedFont);
        myFontFamilyLookup.put(loadedFont, loadedFont);
        loadedFont = next(loaded);
      }
    }
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

  @Nullable
  private static FontFamily next(@NotNull Iterator<FontFamily> iterator) {
    return iterator.hasNext() ? iterator.next() : null;
  }

  private void updateProvidersFromFile() {
    synchronized (myLock) {
      myProviders.clear();
      if (myFontPath == null) {
        return;
      }
      File providersPath = new File(myFontPath, PROVIDERS);
      File providers = new File(providersPath, PROVIDERS_FILENAME);
      if (!providers.exists()) {
        myProviders.add(GoogleFontProvider.INSTANCE);
        return;
      }
      try {
        myProviders.addAll(parseProvidersXml(providers));
      }
      catch (SAXException | ParserConfigurationException | IOException ex) {
        Logger.getInstance(DownloadableFontCacheServiceImpl.class).error("Could not load font providers", ex);
        myProviders.add(GoogleFontProvider.INSTANCE);
      }
    }
  }

  private void updateDownloadServices() {
    synchronized (myLock) {
      myDownloadServiceMap.clear();
      for (FontProvider provider : myProviders) {
        myDownloadServiceMap.put(provider.getAuthority(), new FontDirectoryDownloadService(this, provider, myFontPath));
      }
    }
  }

  private static List<FontProvider> parseProvidersXml(@NotNull File providers)
    throws ParserConfigurationException, SAXException, IOException {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(false);
    SAXParser parser = factory.newSAXParser();
    ProviderHandler handler = new ProviderHandler(providers);
    parser.parse(providers, handler);
    return handler.getFontProviders();
  }

  private static class ProviderHandler extends DefaultHandler {
    private static final String PROVIDER_DIRECTORY = "provider_directory";
    private static final String PROVIDERS = "providers";
    private static final String PROVIDER = "provider";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_AUTHORITY = "authority";
    private static final String ATTR_PACKAGE = "package";
    private static final String ATTR_URL = "url";

    private final File myProviderFile;
    private final Map<String, FontProvider> myProviders;

    private ProviderHandler(@NotNull File provider) {
      myProviderFile = provider;
      myProviders = new HashMap<>();
    }

    @Override
    public void startElement(@NotNull String uri, @NotNull String localName, @NotNull String qName, @NotNull Attributes attributes) {
      switch (localName) {
        case PROVIDER_DIRECTORY:
        case PROVIDERS:
          break;
        case PROVIDER:
          String name = attributes.getValue(ATTR_NAME);
          String authority = attributes.getValue(ATTR_AUTHORITY);
          String packageName = attributes.getValue(ATTR_PACKAGE);
          String url = attributes.getValue(ATTR_URL);
          if (!isEmpty(name) && !isEmpty(authority) && !isEmpty(packageName) && !isEmpty(url)) {
            myProviders.put(packageName, new FontProvider(name, authority, url));
          }
          break;
        default:
          Logger.getInstance(DownloadableFontCacheServiceImpl.class).warn("Unrecognized tag: " + localName + " in file: " + myProviderFile);
          break;
      }
    }

    @NotNull
    public List<FontProvider> getFontProviders() {
      return new ArrayList<>(myProviders.values());
    }
  }
}
