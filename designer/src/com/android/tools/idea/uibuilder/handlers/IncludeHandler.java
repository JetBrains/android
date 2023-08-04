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

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.sdklib.AndroidCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_VISIBILITY;

/**
 * Handler for the {@code <include>} tag
 * <p>
 * <b> {@code layout} attribute does not take any namespace</b>.
 */
public final class IncludeHandler extends ViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_LAYOUT,
      ATTR_VISIBILITY);
  }

  @Override
  @NotNull
  public String getTitle(@NotNull String tagName) {
    return "<include>";
  }

  @Override
  @NotNull
  public String getTitle(@NotNull NlComponent component) {
    return "<include>";
  }

  @NotNull
  @Override
  public String getTitleAttributes(@NotNull NlComponent component) {
    String layout = component.getAttribute(null, ATTR_LAYOUT);
    return StringUtil.isEmpty(layout) ? "" : layout;
  }

  @Override
  @Language("XML")
  @NotNull
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case COMPONENT_CREATION:
        return "<include/>";
      default:
        return NO_PREVIEW;
    }
  }

  @Override
  public boolean onCreate(@Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    // When dropping an include tag, ask the user which layout to include if
    // the layout attribute is not pre-populated.
    String layoutAttr = newChild.getAttribute(null, ATTR_LAYOUT);
    if (insertType == InsertType.CREATE && layoutAttr == null) { // NOT InsertType.CREATE_PREVIEW
      String src = ViewEditor.displayResourceInput(newChild.getModel(), EnumSet.of(ResourceType.LAYOUT));
      if (src != null) {
        return NlWriteCommandActionUtil.compute(newChild, "Create Include", () -> {
          newChild.setAttribute(null, ATTR_LAYOUT, src);
          return true;
        });
      }
      else {
        // Remove the view; the insertion was canceled
        return false;
      }
    }

    return true;
  }

  @Override
  public void onActivateInComponentTree(@NotNull NlComponent component) {
    openIncludedLayout(component);
  }

  @Override
  public void onActivateInDesignSurface(@NotNull NlComponent component, @AndroidCoordinate int x, @AndroidCoordinate int y) {
    openIncludedLayout(component);
  }

  /**
   * Open the layout referenced by the attribute {@link SdkConstants#ATTR_LAYOUT} in
   * the provided {@link SdkConstants#VIEW_INCLUDE}.
   *
   * @param component  The include component
   */
  public static void openIncludedLayout(@NotNull NlComponent component) {
    String attribute = component.getAttribute(null, ATTR_LAYOUT);
    if (attribute == null) {
      return;
    }
    ViewEditor.openResourceFile(component.getModel(), attribute);
  }
}
