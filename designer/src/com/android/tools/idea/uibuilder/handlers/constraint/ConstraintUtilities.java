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

import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.resources.LayoutDirection;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.sherpa.drawing.decorator.TextWidget;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

import static com.android.SdkConstants.SAMPLE_PREFIX;
import static com.android.SdkConstants.TOOLS_SAMPLE_PREFIX;
import static com.android.tools.idea.res.ResourceHelper.resolveStringValue;

/**
 * Utility functions
 */
public class ConstraintUtilities {

  private static HashMap<String, Integer> alignmentMap_ltr = new HashMap<>();
  private static HashMap<String, Integer> alignmentMap_rtl = new HashMap<>();
  static String[]mode = {"0","1","2","3","center","START","END"};

  static {
    alignmentMap_rtl.put(SdkConstants.TextAlignment.CENTER, TextWidget.TEXT_ALIGNMENT_CENTER);
    alignmentMap_rtl.put(SdkConstants.TextAlignment.TEXT_START, TextWidget.TEXT_ALIGNMENT_TEXT_START);
    alignmentMap_rtl.put(SdkConstants.TextAlignment.TEXT_END, TextWidget.TEXT_ALIGNMENT_TEXT_END);
    alignmentMap_rtl.put(SdkConstants.TextAlignment.VIEW_START, TextWidget.TEXT_ALIGNMENT_VIEW_END);
    alignmentMap_rtl.put(SdkConstants.TextAlignment.VIEW_END,  TextWidget.TEXT_ALIGNMENT_VIEW_START);

    alignmentMap_ltr.put(SdkConstants.TextAlignment.CENTER, TextWidget.TEXT_ALIGNMENT_CENTER);
    alignmentMap_ltr.put(SdkConstants.TextAlignment.TEXT_START,  TextWidget.TEXT_ALIGNMENT_TEXT_START);
    alignmentMap_ltr.put(SdkConstants.TextAlignment.TEXT_END,  TextWidget.TEXT_ALIGNMENT_TEXT_END);
    alignmentMap_ltr.put(SdkConstants.TextAlignment.VIEW_START, TextWidget.TEXT_ALIGNMENT_VIEW_START);
    alignmentMap_ltr.put(SdkConstants.TextAlignment.VIEW_END, TextWidget.TEXT_ALIGNMENT_VIEW_END);
  }

  public static int getAlignment(String s, boolean rtl) {
    HashMap<String, Integer> alignmentMap = (rtl) ? alignmentMap_rtl : alignmentMap_ltr;
    if (alignmentMap.containsKey(s)) {
       return alignmentMap.get(s).intValue();
    }
    return TextWidget.TEXT_ALIGNMENT_VIEW_START;
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
    String margin = component.getLiveAttribute(SdkConstants.NS_RESOURCES, attr);
    if (margin == null) {
      if (attr == SdkConstants.ATTR_LAYOUT_MARGIN_START) {
        margin = component.getLiveAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
      }
      else if (attr == SdkConstants.ATTR_LAYOUT_MARGIN_END) {
        margin = component.getLiveAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
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
  public static int getDpValue(@NotNull NlComponent component, String value) {
    if (value != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      if (resourceResolver != null) {
        Integer px = ViewEditor.resolveDimensionPixelSize(resourceResolver, value, configuration);
        return px == null ? 0 : (int)(0.5f + px / (configuration.getDensity().getDpiValue() / 160.0f));
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

  @NotNull
  public static String getResolvedText(@NotNull NlComponent component) {
    String text = component.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_TEXT);
    if (text != null) {
      if (text.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
        if (!text.startsWith(SAMPLE_PREFIX) && !text.startsWith(TOOLS_SAMPLE_PREFIX)) {
          return resolveStringResource(component, text);
        }
        else {
          return "";
        }
      }
      return text;
    }
    text = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT);
    if (text != null) {
      if (text.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
        return resolveStringResource(component, text);
      }
      return text;
    }
    return "";
  }

}
