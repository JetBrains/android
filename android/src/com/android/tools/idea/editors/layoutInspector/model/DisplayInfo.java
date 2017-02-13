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
package com.android.tools.idea.editors.layoutInspector.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DisplayInfo {

  public final int left;
  public final int top;
  public final int width;
  public final int height;
  public final int scrollX;
  public final int scrollY;

  public final boolean willNotDraw;
  public final boolean clipChildren;

  public final float translateX;
  public final float translateY;
  public final float scaleX;
  public final float scaleY;

  @Nullable public final String contentDesc;
  public final boolean isVisible;

  DisplayInfo(@NotNull ViewNode node) {
    left = getInt(node.getProperty("mLeft", "layout:mLeft"), 0);
    top = getInt(node.getProperty("mTop", "layout:mTop"), 0);
    width = getInt(node.getProperty("getWidth()", "layout:getWidth()"), 10);
    height = getInt(node.getProperty("getHeight()", "layout:getHeight()"), 10);
    scrollX = getInt(node.getProperty("mScrollX", "scrolling:mScrollX"), 0);
    scrollY = getInt(node.getProperty("mScrollY", "scrolling:mScrollY"), 0);

    willNotDraw = getBoolean(node.getProperty("willNotDraw()", "drawing:willNotDraw()"), false);
    clipChildren = getBoolean(node.getProperty("getClipChildren()", "drawing:getClipChildren()"), true);

    translateX = getFloat(node.getProperty("getTranslationX", "drawing:getTranslationX()"), 0);
    translateY = getFloat(node.getProperty("getTranslationY", "drawing:getTranslationY()"), 0);
    scaleX = getFloat(node.getProperty("getScaleX()", "drawing:getScaleX()"), 1);
    scaleY = getFloat(node.getProperty("getScaleY()", "drawing:getScaleY()"), 1);

    ViewProperty descProp = node.getProperty("accessibility:getContentDescription()");
    String contentDescription = descProp != null && descProp.getValue() != null && !descProp.getValue().equals("null")
                       ? descProp.getValue() : null;
    if (contentDescription == null) {
      descProp = node.getProperty("text:mText");
      contentDescription = descProp != null && descProp.getValue() != null && !descProp.getValue().equals("null")
                         ? descProp.getValue() : null;
    }
    this.contentDesc = contentDescription;

    ViewProperty visibility = node.getProperty("getVisibility()", "misc:getVisibility()");
    isVisible = visibility == null ||
                "0".equals(visibility.getValue()) || "VISIBLE".equals(visibility.getValue());
  }

  private boolean getBoolean(@Nullable ViewProperty p, boolean defaultValue) {
    if (p != null) {
      try {
        return Boolean.parseBoolean(p.getValue());
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  private int getInt(@Nullable ViewProperty p, int defaultValue) {
    if (p != null) {
      try {
        return Integer.parseInt(p.getValue());
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  private float getFloat(@Nullable ViewProperty p, float defaultValue) {
    if (p != null) {
      try {
        return Float.parseFloat(p.getValue());
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

}
