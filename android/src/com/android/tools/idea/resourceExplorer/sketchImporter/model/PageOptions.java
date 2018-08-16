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
package com.android.tools.idea.resourceExplorer.sketchImporter.model;

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPage;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * {@link ObjectOptions} meant to hold the options the user chooses for each individual page that is imported,
 * i.e. how that page should be treated when parsing:
 * <ul>
 * <li><b>“Icons” page</b> -> for each artboard, attempt to convert to Vector Drawable</li>
 * <li><b>“Colors” page</b> -> TODO</li>
 * <li><b>“Symbols” page</b> -> TODO</li>
 * <li><b>“Text” page</b> -> TODO</li>
 * <li><b>“Mixed”</b> -> tries to render all the different types of assets</li>
 * </ul>
 */
public class PageOptions {
  public enum PageType {
    ICONS,
    SYMBOLS,
    COLORS,
    TEXT,
    MIXED  // EXPERIMENTAL - can be set as the default if we find a stable, reliable implementation
  }

  public final static PageType DEFAULT_PAGE_TYPE = PageType.ICONS;

  private final static String KEYWORD_ICONS = "icon";
  private final static String KEYWORD_SYMBOLS = "symbol";
  private final static String KEYWORD_COLORS = "color";
  private final static String KEYWORD_TEXT = "text";

  private String myName;
  private PageType myPageType;

  public PageOptions(@NotNull String name, @NotNull PageType pageType) {
    myName = name;
    myPageType = pageType;
  }

  public PageOptions(@NotNull SketchPage page) {
    myName = page.getName();
    myPageType = getDefaultPageType(page);
  }

  @NotNull
  private static PageType getDefaultPageType(@NotNull SketchPage page) {
    // Try to auto-detect type from the name using keywords
    String pageName = page.getName().toLowerCase(Locale.ENGLISH);
    if (pageName.contains(KEYWORD_ICONS)) {
      return PageOptions.PageType.ICONS;
    }
    else if (pageName.contains(KEYWORD_SYMBOLS)) {
      return PageOptions.PageType.SYMBOLS;
    }
    else if (pageName.contains(KEYWORD_COLORS)) {
      return PageOptions.PageType.COLORS;
    }
    else if (pageName.contains(KEYWORD_TEXT)) {
      return PageOptions.PageType.TEXT;
    }
    return DEFAULT_PAGE_TYPE;
  }

  @NotNull
  public PageType getPageType() {
    return myPageType;
  }

  public void setPageType(@NotNull PageType pageType) {
    myPageType = pageType;
  }

  @NotNull
  public String getName() {
    return myName;
  }
}