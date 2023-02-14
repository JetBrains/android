/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.rendering.parsers;

import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.ImmutableSet;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.*;

/**
 * An extension of {@link LayoutPsiPullParser} specific for Menu xml files.
 */
class MenuPsiPullParser extends LayoutPsiPullParser.AttributeFilteredLayoutParser {

  // Some attributes are not supported by layoutlib and will throw an exception.
  private static final ImmutableSet<String> UNSUPPORTED_ATTRIBUTES = ImmutableSet.of("onClick", "actionViewClass");

  public MenuPsiPullParser(XmlFile file, ILayoutLog logger, ResourceRepositoryManager resourceRepositoryManager) {
    super(file, logger, new RenderTask.AttributeFilter() {
      @Nullable
      @Override
      public String getAttribute(@NotNull XmlTag node, @Nullable String namespace, @NotNull String localName) {
        if (ANDROID_URI.equals(namespace)) {
          if (localName.equals(ATTR_SHOW_AS_ACTION)) {
            return getShowAsActionValue(node);
          }
          else if (localName.equals("actionLayout")) {
            // Check if the attribute is in the app namespace
            XmlAttribute actionLayout = node.getAttribute("actionLayout", AUTO_URI);
            return actionLayout != null ? actionLayout.getValue() : null;
          }
          else if (UNSUPPORTED_ATTRIBUTES.contains(localName)) {
            return "";
          }
        }
        return null;
      }

      @Nullable
      private String getShowAsActionValue(@NotNull XmlTag node) {
        // Search for the attribute in the android and tools namespace.
        if (node.getAttribute(ATTR_SHOW_AS_ACTION, ANDROID_URI) != null || node.getAttribute(ATTR_SHOW_AS_ACTION, TOOLS_URI) != null) {
          // Return null to indicate that we don't want to filter this attribute.
          return null;
        }
        // For appcompat, the attribute may be present in the app's namespace.
        // Try with res-auto namespace.
        XmlAttribute attr = node.getAttribute(ATTR_SHOW_AS_ACTION, AUTO_URI);
        if (attr != null) {
          return attr.getValue();
        }
        // No match found.
        return null;
      }
    }, resourceRepositoryManager);
  }

  @Nullable
  @Override
  public Object getViewCookie() {
    if (myProvideViewCookies) {
      TagSnapshot element = getCurrentNode();
      if (element != null) {
        // <menu> tags means that we are adding a sub-menu. Since we don't show the submenu, we
        // return the enclosing tag.
        if (element.tagName.equals(FD_RES_MENU)) {
          return getPreviousNode();
        }
        return element;
      }
    }

    return null;
  }
}
