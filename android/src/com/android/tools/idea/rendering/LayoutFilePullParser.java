/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.LayoutlibCallback;
import com.android.ide.common.res2.ValueXmlHelper;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import static com.android.SdkConstants.*;

/**
 * Modified {@link KXmlParser} that adds the methods of {@link ILayoutPullParser}, and
 * performs other layout-specific parser behavior like translating fragment tags into
 * include tags.
 * <p/>
 * It will return a given parser when queried for one through
 * {@link ILayoutPullParser#getParser(String)} for a given name.
 */
public class LayoutFilePullParser extends KXmlParser implements ILayoutPullParser {
  /**
   * The callback to request parsers from
   */
  private final LayoutlibCallback myLayoutlibCallback;
  /**
   * The layout to be shown for the current {@code <fragment>} tag. Usually null.
   */
  private String myFragmentLayout = null;

  /**
   * Crates a new {@link LayoutFilePullParser} for the given XML file.
   */
  public static LayoutFilePullParser create(@NotNull LayoutlibCallback layoutlibCallback, @NotNull File xml)
      throws XmlPullParserException, IOException {
    String xmlText = Files.toString(xml, Charsets.UTF_8);
    return create(layoutlibCallback, xmlText);
  }

  /**
   * Crates a new {@link LayoutFilePullParser} for the given XML text.
   */
  @NotNull
  public static LayoutFilePullParser create(@NotNull LayoutlibCallback layoutlibCallback, @NotNull String xmlText)
      throws XmlPullParserException {
    LayoutFilePullParser parser = new LayoutFilePullParser(layoutlibCallback);
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
    parser.setInput(new StringReader(xmlText));
    return parser;
  }

  /**
   * Creates a new {@link LayoutFilePullParser}
   *
   * @param layoutlibCallback the associated callback
   */
  private LayoutFilePullParser(LayoutlibCallback layoutlibCallback) {
    super();
    myLayoutlibCallback = layoutlibCallback;
  }
  // --- Layout lib API methods

  /**
   * this is deprecated but must still be implemented for older layout libraries.
   * @deprecated use {@link LayoutlibCallback#getParser(String)}.
   */
  @Override
  @Deprecated
  @SuppressWarnings("deprecation") // Required to support older layoutlib versions
  public ILayoutPullParser getParser(String layoutName) {
    return myLayoutlibCallback.getParser(layoutName);
  }

  @Override
  @Nullable
  public Object getViewCookie() {
    String name = super.getName();
    if (name == null) {
      return null;
    }

    // Store tools attributes if this looks like a layout we'll need adapter view
    // bindings for in the LayoutlibCallback.
    if (LIST_VIEW.equals(name) || EXPANDABLE_LIST_VIEW.equals(name) || GRID_VIEW.equals(name) || SPINNER.equals(name)) {
      Map<String, String> map = null;
      int count = getAttributeCount();
      for (int i = 0; i < count; i++) {
        String namespace = getAttributeNamespace(i);
        if (namespace != null && namespace.equals(TOOLS_URI)) {
          String attribute = getAttributeName(i);
          if (attribute.equals(ATTR_IGNORE)) {
            continue;
          }
          if (map == null) {
            map = Maps.newHashMapWithExpectedSize(4);
          }
          map.put(attribute, getAttributeValue(i));
        }
      }

      return map;
    }

    return null;
  }

  // --- KXMLParser override

  @Override
  public String getName() {
    String name = super.getName();

    // At designtime, replace fragments with includes.
    if (name.equals(VIEW_FRAGMENT)) {
      myFragmentLayout = LayoutMetadata.getProperty(this, LayoutMetadata.KEY_FRAGMENT_LAYOUT);
      if (myFragmentLayout != null) {
        return VIEW_INCLUDE;
      }
    }
    else {
      myFragmentLayout = null;
    }


    return name;
  }

  @Nullable
  @Override
  public String getAttributeValue(String namespace, String localName) {
    if (ATTR_LAYOUT.equals(localName) && myFragmentLayout != null) {
      return myFragmentLayout;
    }

    String value = super.getAttributeValue(namespace, localName);

    // on the fly convert match_parent to fill_parent for compatibility with older
    // platforms.
    if (VALUE_MATCH_PARENT.equals(value) &&
        (ATTR_LAYOUT_WIDTH.equals(localName) || ATTR_LAYOUT_HEIGHT.equals(localName)) &&
        ANDROID_URI.equals(namespace)) {
      return VALUE_FILL_PARENT;
    }

    if (namespace != null) {
      if (namespace.equals(ANDROID_URI)) {
        // Allow the tools namespace to override the framework attributes at designtime
        String designValue = super.getAttributeValue(TOOLS_URI, localName);
        if (designValue != null) {
          if (value != null && designValue.isEmpty()) {
            // Empty when there is a runtime attribute set means unset the runtime attribute
            value = null;
          } else {
            value = designValue;
          }
        }
      } else if (value == null) {
        // Auto-convert http://schemas.android.com/apk/res-auto resources. The lookup
        // will be for the current application's resource package, e.g.
        // http://schemas.android.com/apk/res/foo.bar, but the XML document will
        // be using http://schemas.android.com/apk/res-auto in library projects:
        value = super.getAttributeValue(AUTO_URI, localName);
      }
    }

    if (value != null) {
      // Handle unicode and XML escapes
      for (int i = 0, n = value.length(); i < n; i++) {
        char c = value.charAt(i);
        if (c == '&' || c == '\\') {
          value = ValueXmlHelper.unescapeResourceString(value, true, false);
          break;
        }
      }
    }

    return value;
  }
}
