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

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.IProjectCallback;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;

import java.util.Map;

import static com.android.SdkConstants.*;

/**
 * Modified {@link org.kxml2.io.KXmlParser} that adds the methods of {@link com.android.ide.common.rendering.api.ILayoutPullParser}, and
 * performs other layout-specific parser behavior like translating fragment tags into
 * include tags.
 * <p/>
 * It will return a given parser when queried for one through
 * {@link com.android.ide.common.rendering.api.ILayoutPullParser#getParser(String)} for a given name.
 */
public class ContextPullParser extends KXmlParser implements ILayoutPullParser {
  /**
   * The callback to request parsers from
   */
  private final IProjectCallback myProjectCallback;
  /**
   * The layout to be shown for the current {@code <fragment>} tag. Usually null.
   */
  private String myFragmentLayout = null;

  /**
   * Creates a new {@link ContextPullParser}
   *
   * @param projectCallback the associated callback
   */
  public ContextPullParser(IProjectCallback projectCallback) {
    super();
    myProjectCallback = projectCallback;
  }

  // --- Layout lib API methods

  @SuppressWarnings("deprecation") // Required to support older layoutlib versions
  @Override
  /**
   * this is deprecated but must still be implemented for older layout libraries.
   * @deprecated use {@link com.android.ide.common.rendering.api.IProjectCallback#getParser(String)}.
   */
  @Deprecated
  public ILayoutPullParser getParser(String layoutName) {
    return myProjectCallback.getParser(layoutName);
  }

  @Override
  @Nullable
  public Object getViewCookie() {
    String name = super.getName();
    if (name == null) {
      return null;
    }

    // Store tools attributes if this looks like a layout we'll need adapter view
    // bindings for in the ProjectCallback.
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
        SdkConstants.NS_RESOURCES.equals(namespace)) {
      return VALUE_FILL_PARENT;
    }

    if (value != null) {
      if (value.indexOf('&') != -1) {
        value = StringUtil.unescapeXml(value);
      }

      // Handle unicode escapes
      if (value.indexOf('\\') != -1) {
        value = XmlTagPullParser.replaceUnicodeEscapes(value);
      }
    }

    return value;
  }
}
