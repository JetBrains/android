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

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.intellij.openapi.util.text.StringUtil;
import icons.AndroidIcons;
import org.intellij.lang.annotations.Language;

import javax.swing.*;
import java.util.EnumSet;
import java.util.List;

import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_VISIBILITY;

/**
 * Handler for the {@code <include>} tag
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
    return StringUtil.isEmpty(layout) ? "" : "- " + layout;
  }

  @Override
  @NotNull
  public Icon getIcon(@NotNull String tagName) {
    return AndroidIcons.Views.Include;
  }

  @Override
  @NotNull
  public Icon getIcon(@NotNull NlComponent component) {
    return AndroidIcons.Views.Include;
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
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    // When dropping an include tag, ask the user which layout to include.
    if (insertType == InsertType.CREATE) { // NOT InsertType.CREATE_PREVIEW
      String src = editor.displayResourceInput(EnumSet.of(ResourceType.LAYOUT), null);
      if (src != null) {
        newChild.setAttribute(null, ATTR_LAYOUT, src);
        return true;
      }
      else {
        // Remove the view; the insertion was canceled
        return false;
      }
    }

    return true;
  }
}
