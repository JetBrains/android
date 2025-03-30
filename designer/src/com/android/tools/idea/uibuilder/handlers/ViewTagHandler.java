/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.api.*;
import com.google.common.collect.ImmutableList;
import icons.StudioIcons;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <view>} tag.
 */
public class ViewTagHandler extends ViewHandler {
  private static final String DIVIDER_BACKGROUND = "?android:attr/listDivider";

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_CLASS, ATTR_STYLE);
  }

  @Override
  @NotNull
  public String getTitle(@NotNull String tagName) {
    return "<view>";
  }

  @Override
  @NotNull
  public String getTitle(@NotNull NlComponent component) {
    return "<view>";
  }

  @Override
  @Language("XML")
  @NotNull
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case COMPONENT_CREATION:
        return "<view/>";
      case PREVIEW_ON_PALETTE:
      case DRAG_PREVIEW:
        return NO_PREVIEW;
      default:
        return super.getXml(tagName, xmlType);
    }
  }

  @Override
  public boolean onCreate(@Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    if (insertType == InsertType.CREATE && newChild.getAttribute(null, ATTR_CLASS) == null &&
        !(isVerticalDivider(newChild) || isHorizontalDivider(newChild))) {
      String src = browseClasses(newChild.getModel(), null);
      if (src != null) {
        NlWriteCommandActionUtil.run(newChild, "Setting layout attribute", () -> newChild.setAttribute(null, ATTR_CLASS, src));
        return true;
      }
      else {
        // Remove the view; the insertion was canceled.
        return false;
      }
    }
    return true;
  }

  @Override
  @NotNull
  public Icon getIcon(@NotNull NlComponent component) {
    if (!component.getTagName().equals(VIEW_TAG)) {
      return super.getIcon(component);
    }
    if (isVerticalDivider(component)) {
      return StudioIcons.LayoutEditor.Palette.VERTICAL_DIVIDER;
    }
    if (isHorizontalDivider(component)) {
      return StudioIcons.LayoutEditor.Palette.HORIZONTAL_DIVIDER;
    }
    return StudioIcons.LayoutEditor.Palette.VIEW;
  }

  private static boolean isVerticalDivider(@NotNull NlComponent component) {
    return DIVIDER_BACKGROUND.equals(component.getAttribute(ANDROID_URI, ATTR_BACKGROUND)) && hasShortWidth(component, ATTR_LAYOUT_WIDTH);
  }

  private static boolean isHorizontalDivider(@NotNull NlComponent component) {
    return DIVIDER_BACKGROUND.equals(component.getAttribute(ANDROID_URI, ATTR_BACKGROUND)) && hasShortWidth(component, ATTR_LAYOUT_HEIGHT);
  }

  private static boolean hasShortWidth(@NotNull NlComponent component, @NotNull String attributeName) {
    String value = component.getAttribute(ANDROID_URI, attributeName);
    if (value == null) {
      return false;
    }
    switch (value) {
      case "1":
      case "1px":
      case "1dp":
      case "1dip":
        return true;
      default:
        return false;
    }
  }

  @Nullable
  private static String browseClasses(@NotNull NlModel model, @Nullable String existingValue) {
    return ViewEditor.displayClassInput(model, "Views", Collections.singleton(CLASS_VIEW), existingValue);
  }

  private static boolean isViewSuitableForLayout(@NotNull String qualifiedName) {
    // Don't include builtin views (these are already in the palette and likely not what the user is looking for).
    return !qualifiedName.startsWith(ANDROID_PKG_PREFIX) ||
           qualifiedName.startsWith(ANDROID_SUPPORT_PKG_PREFIX) ||
           qualifiedName.startsWith(ANDROIDX_PKG_PREFIX);
  }
}
