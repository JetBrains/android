/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint;

import static com.android.tools.idea.res.IdeResourcesUtil.resolveStringValue;

import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.resources.LayoutDirection;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.decorator.TextWidgetConstants;
import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility functions
 */
public final class ConstraintUtilities {

  private static HashMap<String, Integer> alignmentMap_ltr = new HashMap<>();
  private static HashMap<String, Integer> alignmentMap_rtl = new HashMap<>();
  static String[]mode = {"0","1","2","3","center","START","END"};
  private static final String[][] CHARS_MAP = {
    {"&quot;", "\""},
    {"&apos;", "'"},
    {"&lt;", "<"},
    {"&gt;", ">"},
    {"&amp;", "&"},
  };

  static {
    alignmentMap_rtl.put(SdkConstants.TextAlignment.CENTER, TextWidgetConstants.TEXT_ALIGNMENT_CENTER);
    alignmentMap_rtl.put(SdkConstants.TextAlignment.TEXT_START, TextWidgetConstants.TEXT_ALIGNMENT_TEXT_START);
    alignmentMap_rtl.put(SdkConstants.TextAlignment.TEXT_END, TextWidgetConstants.TEXT_ALIGNMENT_TEXT_END);
    alignmentMap_rtl.put(SdkConstants.TextAlignment.VIEW_START, TextWidgetConstants.TEXT_ALIGNMENT_VIEW_END);
    alignmentMap_rtl.put(SdkConstants.TextAlignment.VIEW_END, TextWidgetConstants.TEXT_ALIGNMENT_VIEW_START);

    alignmentMap_ltr.put(SdkConstants.TextAlignment.CENTER, TextWidgetConstants.TEXT_ALIGNMENT_CENTER);
    alignmentMap_ltr.put(SdkConstants.TextAlignment.TEXT_START, TextWidgetConstants.TEXT_ALIGNMENT_TEXT_START);
    alignmentMap_ltr.put(SdkConstants.TextAlignment.TEXT_END, TextWidgetConstants.TEXT_ALIGNMENT_TEXT_END);
    alignmentMap_ltr.put(SdkConstants.TextAlignment.VIEW_START, TextWidgetConstants.TEXT_ALIGNMENT_VIEW_START);
    alignmentMap_ltr.put(SdkConstants.TextAlignment.VIEW_END, TextWidgetConstants.TEXT_ALIGNMENT_VIEW_END);
  }

  public static int getAlignment(String s, boolean rtl) {
    HashMap<String, Integer> alignmentMap = (rtl) ? alignmentMap_rtl : alignmentMap_ltr;
    if (alignmentMap.containsKey(s)) {
       return alignmentMap.get(s).intValue();
    }
    return TextWidgetConstants.TEXT_ALIGNMENT_VIEW_START;
  }

  /**
   * Gets the specified margin value in dp. If the specified margin is
   * SdkConstants.ATTR_LAYOUT_MARGIN_START or SdkConstants.ATTR_LAYOUT_MARGIN_END
   * and cannot be found, this method falls back to SdkConstants.ATTR_LAYOUT_MARGIN_LEFT
   * or SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT.
   *
   * @param component the component we are looking at
   * @param widget    the margin attribute name
   * @return the margin in dp or 0 if it cannot be found
   */
  static int getMargin(@NotNull NlComponent component, @NotNull String attr) {
    String margin = component.getLiveAttribute(SdkConstants.ANDROID_URI, attr);
    if (margin == null) {
      if (attr == SdkConstants.ATTR_LAYOUT_MARGIN_START) {
        margin = component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
      }
      else if (attr == SdkConstants.ATTR_LAYOUT_MARGIN_END) {
        margin = component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
      }
    }
    if (margin != null) {
      return getDpValue(component, margin);
    }
    return 0;
  }

  /**
   * Is this component displayed in RTL mode
   *
   * @param component
   * @return
   */
  static public boolean isInRTL(@NotNull NlComponent component) {
    Configuration configuration = component.getModel().getConfiguration();
    if (configuration == null) {
      return false;
    }
    LayoutDirectionQualifier qualifier = configuration.getFullConfig().getLayoutDirectionQualifier();
    if (qualifier == null) {
      return false;
    }
    return qualifier.getValue() != LayoutDirection.RTL ? false : true;
  }

  /**
   * Return a dp value correctly resolved. This is only intended for generic
   * dimensions (number + unit). Do not use this if the string can contain
   * wrap_content or match_parent. See {@link #getLayoutDimensionDpValue(NlComponent, String)}.
   *
   * @param component the component we are looking at
   * @param value     the attribute value we want to parse
   * @return the value of the attribute in Dp, or zero if impossible to resolve
   */
  @AndroidDpCoordinate
  public static int getDpValue(@NotNull NlComponent component, String value) {
    if (value != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      if (resourceResolver != null) {
        Integer px = ViewEditor.resolveDimensionPixelSize(resourceResolver, value, configuration);
        return px == null ? 0 : Coordinates.pxToDp(component.getModel(), px);
      }
    }
    return 0;
  }

  @NotNull
  static String resolveStringResource(@NotNull NlComponent component, @NotNull String text) {
    Configuration configuration = component.getModel().getConfiguration();
    ResourceResolver resourceResolver = configuration.getResourceResolver();
    if (resourceResolver != null) {
      return resolveStringValue(resourceResolver, text);
    }
    return "";
  }

  /**
   * Get the 'text' attribute from a component.
   *
   * @return The resolved 'text' attribute or an empty string if the attribute is not present.
   */
  @NotNull
  public static String getResolvedText(@NotNull NlComponent component) {
    String text = getResolvedAttribute(component, SdkConstants.ATTR_TEXT);
    if (text == null) {
      return "";
    }
    return text;
  }

  /**
   * Get the 'text_on' or 'text_off' attribute from a component. Looks for the 'checked' attribute to choose the current state, defaults to
   * 'text_off' if there is no 'checked' attribute.
   *
   * @return The resolved text attribute or an empty string if the attribute is not present.
   */
  @NotNull
  public static String getResolvedToggleText(@NotNull NlComponent component) {
    String checkedText = getResolvedAttribute(component, SdkConstants.ATTR_CHECKED);

    String toggleTextAttr =
      checkedText != null && checkedText.equals(SdkConstants.VALUE_TRUE) ? SdkConstants.ATTR_TEXT_ON : SdkConstants.ATTR_TEXT_OFF;

    String text = getResolvedAttribute(component, toggleTextAttr);

    if (text == null) {
      return "";
    }
    return text;
  }

  /**
   * Get an attribute from a component. Looks for 'tools' attributes first.
   */
  @Nullable
  private static String getResolvedAttribute(@NotNull NlComponent component, @NotNull String attribute) {
    String resolvedAttribute = component.getAttribute(SdkConstants.TOOLS_URI, attribute);

    if (resolvedAttribute == null) {
      // Check on android namespace.
      resolvedAttribute = component.getAttribute(SdkConstants.ANDROID_URI, attribute);
    }
    if (resolvedAttribute != null && resolvedAttribute.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
      // Check if it comes from a resource and resolve.
      resolvedAttribute = resolveStringResource(component, resolvedAttribute);
    }
    return replaceSpecialChars(resolvedAttribute);
  }

  /** Looks and substitutes special characters. */
  @VisibleForTesting
  @Nullable
  static String replaceSpecialChars(@Nullable String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    int offset = 0;
    int pos = 0;
    if (text.indexOf("&") >= 0) {
      boolean notDone = true;
      while (notDone) {
        notDone = false;
        for (int i = 0; i < CHARS_MAP.length; i++) {
          if ((pos = text.indexOf(CHARS_MAP[i][0],offset)) >= 0) {
            notDone = true;
            text = text.replace(CHARS_MAP[i][0], CHARS_MAP[i][1]);
            offset = pos + CHARS_MAP[i][0].length();
          }
        }

        if (offset < text.length() && text.substring(offset).matches(".*&#[0-9]+;.*")) {
          int begin = text.indexOf("&#");
          int end = text.indexOf(";", begin);
          char part = (char)Integer.parseInt(text.substring(begin + 2, end));
          text = text.replace(text.substring(begin, end + 1), "" + part);
          notDone = true;
          offset = end;
        }
        if (offset < text.length() && text.substring(offset).matches(".*&#x[a-fA-F0-9]+;.*")) {
          int begin = text.indexOf("&#x");
          int end = text.indexOf(";", begin);
          char part = (char)Integer.parseInt(text.substring(begin + 3, end), 16);
          text = text.replace(text.substring(begin, end + 1), "" + part);
          notDone = true;
          offset = end;
        }
      }
    }
    return text;
  }
}
