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

import com.android.tools.idea.downloads.DownloadService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.android.tools.idea.fonts.FontDetail.DEFAULT_WEIGHT;
import static com.android.tools.idea.fonts.FontDetail.DEFAULT_WIDTH;
import static com.android.tools.idea.fonts.FontFamily.FontSource.DOWNLOADABLE;

/**
 * {@link FontDirectoryDownloadService} is a download service for downloading a font directory
 * i.e. a list of font families.
 */
class FontDirectoryDownloadService extends DownloadService {
  private static final String SERVICE_POSTFIX = " Downloadable Fonts";
  private static final String FONT_DIRECTORY_FILENAME = "font_directory.xml";
  private static final String TEMPORARY_FONT_DIRECTORY_FILENAME = "temp_font_directory.xml";
  private static final String DIRECTORY = "directory";

  private final FontProvider myProvider;
  private final DownloadableFontCacheServiceImpl myFontService;

  public FontDirectoryDownloadService(@NotNull DownloadableFontCacheServiceImpl fontService, @NotNull FontProvider provider, @NotNull File fontCachePath) {
    super(provider.getName() + SERVICE_POSTFIX,
          provider.getUrl(),
          provider.getFallbackResourceUrl(),
          getCachePath(provider, fontCachePath),
          TEMPORARY_FONT_DIRECTORY_FILENAME,
          FONT_DIRECTORY_FILENAME);
    myFontService = fontService;
    myProvider = provider;
  }

  @Override
  public void loadFromFile(@NotNull URL url) {
    try {
      List<FontFamily> fontFamilies = parseXml(url);
      fontFamilies.sort(Comparator.comparing(FontFamily::getName));
      myFontService.mergeFonts(fontFamilies);
    }
    catch (SAXException | ParserConfigurationException | IOException ex) {
      Logger.getInstance(FontDirectoryDownloadService.class).error("Could not load fonts", ex);
    }
  }

  @NotNull
  private static File getCachePath(@NotNull FontProvider provider, @NotNull File fontCachePath) {
    File providerPath = new File(fontCachePath, provider.getAuthority());
    File directoryPath = new File(providerPath, DIRECTORY);
    FileUtil.createDirectory(directoryPath);
    return directoryPath;
  }

  @NotNull
  private List<FontFamily> parseXml(@NotNull URL url) throws SAXException, ParserConfigurationException, IOException {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(false);
    SAXParser parser = factory.newSAXParser();
    DirectoryHandler handler = new DirectoryHandler(myProvider, url);
    parser.parse(url.toString(), handler);
    return handler.getFontFamilies();
  }

  private static class DirectoryHandler extends DefaultHandler {
    private static final String FONT_DIRECTORY = "font_directory";
    private static final String FAMILIES = "families";
    private static final String FAMILY = "family";
    private static final String FONT = "font";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_STYLE_NAME = "styleName";
    private static final String ATTR_MENU = "menu";
    private static final String ATTR_MENU_NAME = "menuName";
    private static final String ATTR_WEIGHT = "weight";
    private static final String ATTR_WIDTH = "width";
    private static final String ATTR_ITALIC = "italic";
    private static final String ATTR_URL = "url";

    private final FontProvider myProvider;
    private final URL myUrl;
    private final List<FontFamily> myFontFamilies;
    private final List<FontDetail.Builder> myFontDetails;
    private String myFontName;
    private String myFontMenu;
    private String myFontMenuName;

    private DirectoryHandler(@NotNull FontProvider provider, @NotNull URL url) {
      myProvider = provider;
      myUrl = url;
      myFontFamilies = new ArrayList<>();
      myFontDetails = new ArrayList<>();
    }

    @Override
    public void startElement(@NotNull String uri, @NotNull String localName, @NotNull String name, @NotNull Attributes attributes)
      throws SAXException {
      switch (name) {
        case FONT_DIRECTORY:
        case FAMILIES:
          break;
        case FAMILY:
          myFontName = attributes.getValue(ATTR_NAME);
          myFontMenu = addProtocol(attributes.getValue(ATTR_MENU));
          myFontMenuName = attributes.getValue(ATTR_MENU_NAME);
          break;
        case FONT:
          FontDetail.Builder font = new FontDetail.Builder();
          font.myWeight = FontFamilyParser.parseInt(attributes.getValue(ATTR_WEIGHT), DEFAULT_WEIGHT);
          font.myWidth = FontFamilyParser.parseInt(attributes.getValue(ATTR_WIDTH), DEFAULT_WIDTH);
          font.myItalics = FontFamilyParser.parseItalics(attributes.getValue(ATTR_ITALIC));
          font.myFontUrl = addProtocol(attributes.getValue(ATTR_URL));
          font.myStyleName = attributes.getValue(ATTR_STYLE_NAME);
          if (myFontName != null && myFontMenu != null && font.myWeight > 0 && !StringUtil.isEmpty(font.myFontUrl)) {
            myFontDetails.add(font);
          }
          break;
        default:
          throw new SAXException("Unrecognized tag: " + name + " in file: " + myUrl);
      }
    }

    @Override
    public void endElement(@NotNull String uri, @NotNull String localName, @NotNull String name) {
      if (name.equals(FAMILY)) {
        if (myFontName != null && myFontMenu != null) {
          myFontFamilies.add(new FontFamily(myProvider, DOWNLOADABLE, myFontName, myFontMenu, myFontMenuName, myFontDetails));
        }
        myFontDetails.clear();
      }
    }

    @NotNull
    public List<FontFamily> getFontFamilies() {
      return myFontFamilies;
    }

    @Nullable
    private static String addProtocol(@Nullable String url) {
      return url == null || !StringUtil.startsWith(url, "//") ? url : FontFamily.HTTPS_PROTOCOL_START + url.substring(2);
    }
  }
}
