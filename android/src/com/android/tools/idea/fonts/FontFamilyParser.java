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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.ide.common.fonts.FontDetailKt.DEFAULT_WIDTH;

import com.android.ide.common.fonts.MutableFontDetail;
import com.android.ide.common.fonts.QueryParser;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parse a font xml file.
 * Each file is either a specification of a downloadable font with a font query,
 * or a specification of a family with references to individual fonts.
 */
final class FontFamilyParser {

  @NotNull
  static QueryParser.ParseResult parseFontFamily(@NotNull File xmlFile) {
    try {
      return parseFontReference(xmlFile);
    }
    catch (QueryParser.FontQueryParserError ex) {
      return new ParseErrorResult(ex.getMessage());
    }
    catch (SAXException | ParserConfigurationException | IOException ex) {
      String message = "Could not parse font xml file " + xmlFile;
      Logger.getInstance(FontFamilyParser.class).debug(message, ex);
      return new ParseErrorResult(message);
    }
  }

  private static QueryParser.ParseResult parseFontReference(@NotNull File xmlFile)
    throws SAXException, ParserConfigurationException, IOException {
    SAXParserFactory factory = SAXParserFactory.newDefaultInstance();
    factory.setNamespaceAware(true);
    SAXParser parser = factory.newSAXParser();
    FontFamilyHandler handler = new FontFamilyHandler(xmlFile);
    parser.parse(xmlFile, handler);
    return handler.getResult();
  }

  static class ParseErrorResult extends QueryParser.ParseResult {
    private final String myMessage;

    ParseErrorResult(@NotNull String message) {
      myMessage = message;
    }

    public String getMessage() {
      return myMessage;
    }
  }

  private static class FontFamilyHandler extends DefaultHandler {
    private static final String FONT_FAMILY = "font-family";
    private static final String FONT = "font";
    private static final String ATTR_AUTHORITY = "fontProviderAuthority";
    private static final String ATTR_QUERY = "fontProviderQuery";
    private static final String ATTR_FONT = "font";
    private static final String ATTR_FONT_WEIGHT = "fontWeight";
    private static final String ATTR_FONT_WIDTH = "fontWidth";
    private static final String ATTR_FONT_STYLE = "fontStyle";

    private final File myFile;
    private QueryParser.ParseResult myResult;

    private FontFamilyHandler(@NotNull File file) {
      myFile = file;
    }

    @NotNull
    private QueryParser.ParseResult getResult() {
      if (myResult == null) {
        myResult = new ParseErrorResult("The font file is empty");
      }
      return myResult;
    }

    @Override
    public void startElement(@NotNull String uri, @NotNull String localName, @NotNull String name, @NotNull Attributes attributes)
      throws SAXException {
      switch (name) {
        case FONT_FAMILY:
          myResult = parseQuery(getAttributeValue(attributes, ATTR_AUTHORITY), getAttributeValue(attributes, ATTR_QUERY));
          break;
        case FONT:
          String fontName = getAttributeValue(attributes, ATTR_FONT);
          int weight = parseInt(getAttributeValue(attributes, ATTR_FONT_WEIGHT), -1);
          int width = parseInt(getAttributeValue(attributes, ATTR_FONT_WIDTH), DEFAULT_WIDTH);
          String fontStyle = getAttributeValue(attributes, ATTR_FONT_STYLE);
          boolean italics = parseFontStyle(fontStyle);
          boolean hasExplicitStyle = fontStyle != null;
          myResult = addFont(fontName, weight, width, italics, hasExplicitStyle);
          break;
        default:
          Logger.getInstance(FontFamilyParser.class).warn("Unrecognized tag: " + name + " in file: " + myFile);
          break;
      }
    }

    @Nullable
    private static String getAttributeValue(@NotNull Attributes attributes, @NotNull String attrName) {
      String value = attributes.getValue(ANDROID_URI, attrName);
      if (value != null) {
        return value;
      }
      return attributes.getValue(AUTO_URI, attrName);
    }

    /**
     * Parse the downloadable font query if present
     *
     * The XML file may be either a downloadable font with required attributes: font authority and a query attribute,
     * or the file may be a font family definition which combines several font tags.
     */
    @Nullable
    private QueryParser.ParseResult parseQuery(@Nullable String authority, @Nullable String query) {
      // If there already is an error condition stop
      if (myResult instanceof ParseErrorResult) {
        return myResult;
      }
      // If neither an authority or a query is defined then this XML file must be a font family definition.
      // Simply return the existing result (which may be null).
      if (authority == null && query == null) {
        return myResult;
      }
      if (myResult != null) {
        return new ParseErrorResult("<" + FONT_FAMILY + "> must be the root element");
      }
      if (authority == null) {
        return new ParseErrorResult("The <" + FONT_FAMILY + "> tag must contain an " + ATTR_AUTHORITY + " attribute");
      }
      if (query == null) {
        return new ParseErrorResult("The <" + FONT_FAMILY + "> tag must contain a " + ATTR_QUERY + " attribute");
      }
      return QueryParser.parseDownloadableFont(authority, query);
    }

    private QueryParser.ParseResult addFont(@Nullable String fontName, int weight, int width, boolean italics, boolean hasExplicitStyle) {
      if (myResult instanceof ParseErrorResult) {
        return myResult;
      }
      if (myResult != null && !(myResult instanceof CompoundFontResult)) {
        return new ParseErrorResult("<" + FONT + "> is not allowed in a downloadable font definition");
      }
      if (fontName == null) {
        return new ParseErrorResult("The <" + FONT + "> tag must contain a " + ATTR_FONT + " attribute");
      }
      CompoundFontResult result = (CompoundFontResult)myResult;
      if (result == null) {
        result = new CompoundFontResult();
      }
      result.addFont(fontName, weight, width, italics, hasExplicitStyle);
      return result;
    }
  }

  static class CompoundFontResult extends QueryParser.ParseResult {
    private Map<String, MutableFontDetail> myFonts;

    CompoundFontResult() {
      myFonts = new LinkedHashMap<>();
    }

    @NotNull
    Map<String, MutableFontDetail> getFonts() {
      return myFonts;
    }

    private void addFont(@NotNull String fontName, int weight, int width, boolean italics, boolean hasExplicitStyle) {
      myFonts.put(fontName, new MutableFontDetail(weight, width, italics, hasExplicitStyle));
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

  static boolean parseFontStyle(@Nullable String fontStyle) {
    return fontStyle != null && fontStyle.startsWith("italic");
  }
}
