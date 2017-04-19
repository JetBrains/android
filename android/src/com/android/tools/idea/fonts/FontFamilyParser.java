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

import com.google.common.base.Splitter;
import com.intellij.openapi.diagnostic.Logger;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.fonts.FontDetail.DEFAULT_WEIGHT;
import static com.android.tools.idea.fonts.FontDetail.DEFAULT_WIDTH;

/**
 * Parse a font xml file.
 * Each file is either a specification of a downloadable font with a font query,
 * or a specification of a family with references to individual fonts.
 */
class FontFamilyParser {

  @Nullable
  static ParseResult parseFontFamily(@NotNull File xmlFile) {
    try {
      return parseFontReference(xmlFile);
    }
    catch (SAXException | ParserConfigurationException | IOException ex) {
      Logger.getInstance(FontFamilyParser.class).error("Could not parse font xml file " + xmlFile, ex);
      return null;
    }
  }

  private static ParseResult parseFontReference(@NotNull File xmlFile)
    throws SAXException, ParserConfigurationException, IOException {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(true);
    SAXParser parser = factory.newSAXParser();
    FontFamilyHandler handler = new FontFamilyHandler(xmlFile);
    parser.parse(xmlFile, handler);
    return handler.getResult();
  }

  private static class FontFamilyHandler extends DefaultHandler {
    private static final String FONT_FAMILY = "font-family";
    private static final String FONT = "font";
    private static final String ATTR_AUTHORITY = "android:fontProviderAuthority";
    private static final String ATTR_QUERY = "android:fontProviderQuery";
    private static final String ATTR_FONT = "android:font";
    private static final String ATTR_FONT_WEIGHT = "android:fontWeight";
    private static final String ATTR_FONT_WIDTH = "android:fontWidth";
    private static final String ATTR_FONT_STYLE = "android:fontStyle";

    private final File myFile;
    private final ParseResult myResult;

    private FontFamilyHandler(@NotNull File file) {
      myFile = file;
      myResult = new ParseResult();
    }

    @NotNull
    private ParseResult getResult() {
      return myResult;
    }

    @Override
    public void startElement(@NotNull String uri, @NotNull String localName, @NotNull String name, @NotNull Attributes attributes)
      throws SAXException {
      switch (name) {
        case FONT_FAMILY:
          myResult.myAuthority = attributes.getValue(ATTR_AUTHORITY);
          parseQuery(attributes.getValue(ATTR_QUERY));
          break;
        case FONT:
          String font = attributes.getValue(ATTR_FONT);
          FontDetail.Builder detail = new FontDetail.Builder();
          detail.myWeight = parseInt(attributes.getValue(ATTR_FONT_WEIGHT), DEFAULT_WEIGHT);
          detail.myWidth = parseInt(attributes.getValue(ATTR_FONT_WIDTH), DEFAULT_WIDTH);
          detail.myItalics = parseFontStyle(attributes.getValue(ATTR_FONT_STYLE));
          if (!StringUtil.isEmpty(font)) {
            if (myResult.myFonts.isEmpty()) {
              myResult.myFonts = new HashMap<>();
            }
            myResult.myFonts.put(font, detail);
          }
          break;
        default:
          Logger.getInstance(FontFamilyParser.class).warn("Unrecognized tag: " + name + " in file: " + myFile);
          break;
      }
    }

    private void parseQuery(@Nullable String query) {
      if (query == null) {
        return;
      }
      if (query.indexOf('=') < 0) {
        myResult.myFontName = query;
        return;
      }
      for (String part : Splitter.on('&').trimResults().split(query)) {
        List<String> parameter = Splitter.on('=').splitToList(part);
        if (parameter.size() == 2) {
          switch (parameter.get(0)) {
            case "name":
              myResult.myFontName = parameter.get(1);
              break;
            case "weight":
              myResult.myDetail.myWeight = parseInt(parameter.get(1), DEFAULT_WEIGHT);
              break;
            case "width":
              myResult.myDetail.myWidth = parseInt(parameter.get(1), DEFAULT_WIDTH);
              break;
            case "italic":
              myResult.myDetail.myItalics = parseItalics(parameter.get(1));
              break;
          }
        }
      }
    }
  }

  static class ParseResult {
    private String myAuthority;
    private String myFontName;
    private FontDetail.Builder myDetail;
    private Map<String, FontDetail.Builder> myFonts;

    ParseResult() {
      myDetail = new FontDetail.Builder();
      myFonts = Collections.emptyMap();
    }

    @NotNull
    public String getAuthority() {
      return StringUtil.notNullize(myAuthority);
    }

    @NotNull
    public String getFontName() {
      return StringUtil.notNullize(myFontName);
    }

    @NotNull
    public FontDetail.Builder getFontDetail() {
      return myDetail;
    }

    @NotNull
    Map<String, FontDetail.Builder> getFonts() {
      return myFonts;
    }
  }

  static int parseInt(@Nullable String intAsString, int defaultValue) {
    if (intAsString == null) {
      return defaultValue;
    }
    try {
      return Math.round(Float.parseFloat(intAsString));
    }
    catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  static boolean parseItalics(@Nullable String italics) {
    return italics != null && italics.startsWith("1");
  }

  static boolean parseFontStyle(@Nullable String fontStyle) {
    return fontStyle != null && fontStyle.startsWith("italic");
  }
}
