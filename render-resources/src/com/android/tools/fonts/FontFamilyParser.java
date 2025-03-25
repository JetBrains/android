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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.fonts.FontQueryParserError;
import com.android.ide.common.fonts.MutableFontDetail;
import com.android.ide.common.fonts.ParseResult;
import com.android.ide.common.fonts.QueryResolver;
import com.android.tools.environment.Logger;
import java.io.InputStream;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.ide.common.fonts.FontDetailKt.DEFAULT_WIDTH;
import static com.android.ide.common.fonts.FontDetailKt.ITALICS;
import static com.android.ide.common.fonts.FontDetailKt.NORMAL;

/**
 * Parse a font xml file.
 * Each file is either a specification of a downloadable font with a font query,
 * or a specification of a family with references to individual fonts.
 */
public class FontFamilyParser {

  @NonNull
  public static ParseResult parseFontFamily(@NonNull InputStream xmlStream, @NonNull String fileName) {
    try {
      return parseFontReference(xmlStream, fileName);
    }
    catch (FontQueryParserError ex) {
      return new ParseErrorResult(ex.getMessage());
    }
    catch (SAXException | ParserConfigurationException | IOException ex) {
      String message = "Could not parse font xml file " + fileName;
      Logger.getInstance(FontFamilyParser.class).debug(message, ex);
      return new ParseErrorResult(message);
    }
  }

  private static ParseResult parseFontReference(@NonNull InputStream xmlStream, @NonNull String fileName)
    throws SAXException, ParserConfigurationException, IOException {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(true);
    SAXParser parser = factory.newSAXParser();
    FontFamilyHandler handler = new FontFamilyHandler(fileName);
    parser.parse(xmlStream, handler);
    return handler.getResult();
  }

  static class ParseErrorResult extends ParseResult {
    private final String myMessage;

    ParseErrorResult(@NonNull String message) {
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

    private final String myFileName;
    private ParseResult myResult;

    private FontFamilyHandler(@NonNull String fileName) {
      myFileName = fileName;
    }

    @NonNull
    private ParseResult getResult() {
      if (myResult == null) {
        myResult = new ParseErrorResult("The font file is empty");
      }
      return myResult;
    }

    @Override
    public void startElement(@NonNull String uri, @NonNull String localName, @NonNull String name, @NonNull Attributes attributes)
      throws SAXException {
      switch (name) {
        case FONT_FAMILY:
          myResult = parseQuery(getAttributeValue(attributes, ATTR_AUTHORITY), getAttributeValue(attributes, ATTR_QUERY));
          break;
        case FONT:
          String fontName = getAttributeValue(attributes, ATTR_FONT);
          int weight = parseInt(getAttributeValue(attributes, ATTR_FONT_WEIGHT), -1);
          float width = parseFloat(getAttributeValue(attributes, ATTR_FONT_WIDTH), DEFAULT_WIDTH);
          String fontStyle = getAttributeValue(attributes, ATTR_FONT_STYLE);
          float italics = parseFontStyle(fontStyle);
          boolean hasExplicitStyle = fontStyle != null;
          myResult = addFont(fontName, weight, width, italics, hasExplicitStyle);
          break;
        default:
          Logger.getInstance(FontFamilyParser.class).warn("Unrecognized tag: " + name + " in file: " + myFileName);
          break;
      }
    }

    @Nullable
    private static String getAttributeValue(@NonNull Attributes attributes, @NonNull String attrName) {
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
    private ParseResult parseQuery(@Nullable String authority, @Nullable String query) {
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
      return QueryResolver.parseDownloadableFont(authority, query);
    }

    private ParseResult addFont(@Nullable String fontName, int weight, float width, float italics, boolean hasExplicitStyle) {
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

  public static class CompoundFontResult extends ParseResult {
    private Map<String, MutableFontDetail> myFonts;

    CompoundFontResult() {
      myFonts = new LinkedHashMap<>();
    }

    @NonNull
    public Map<String, MutableFontDetail> getFonts() {
      return myFonts;
    }

    private void addFont(@NonNull String fontName, int weight, float width, float italics, boolean hasExplicitStyle) {
      myFonts.put(fontName, new MutableFontDetail(fontName, weight, width, italics, hasExplicitStyle));
    }
  }

  static int parseInt(@Nullable String intAsString, int defaultValue) {
    if (intAsString == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(intAsString);
    }
    catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  static float parseFloat(@Nullable String floatAsString, float defaultValue) {
    if (floatAsString == null) {
      return defaultValue;
    }
    try {
      return Float.parseFloat(floatAsString);
    }
    catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  static float parseFontStyle(@Nullable String fontStyle) {
    return fontStyle != null && fontStyle.startsWith("italic") ? ITALICS : NORMAL;
  }
}
