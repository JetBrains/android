/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.FD_RES_MENU;

/**
 * An extension of {@link LayoutPsiPullParser} specific for Menu xml files.
 */
public class MenuPsiPullParser extends LayoutPsiPullParser.AttributeFilteredLayoutParser {

  // List of attributes to omit.
  private static final String[] FILTERS = {"onClick", "actionViewClass", "actionLayout"};

  public MenuPsiPullParser(XmlFile file, RenderLogger logger) {
    super(file, logger, new RenderService.AttributeFilter() {
      @Nullable
      @Override
      public String getAttribute(@NotNull XmlTag node, @Nullable String namespace, @NotNull String localName) {
        if (ANDROID_URI.equals(namespace)) {
          for (String filter : FILTERS) {
            if (filter.equals(localName)) {
              // An empty return value means, remove the attribute and null means no preference.
              return "";
            }
          }
        }
        return null;
      }
    });
  }

  @Nullable
  @Override
  public Object getViewCookie() {
    if (myProvideViewCookies) {
      Element element = getCurrentNode();
      if (element != null) {
        // <menu> tags means that we are adding a sub-menu. Since we don't show the submenu, we
        // return the enclosing tag.
        if (element.tag.equals(FD_RES_MENU)) {
          Element previousElement = getPreviousNode();
          if (previousElement != null) {
            return previousElement.cookie;
          }
        }
        return element.cookie;
      }
    }

    return null;
  }
}
