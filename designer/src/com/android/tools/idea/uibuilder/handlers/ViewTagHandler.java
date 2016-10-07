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

import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <view>} tag
 */
public class ViewTagHandler extends ViewHandler {
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
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    if (insertType == InsertType.CREATE) { // NOT InsertType.CREATE_PREVIEW
      String src = editor.displayClassInput(Sets.newHashSet(CLASS_VIEW), qualifiedName -> {
        // Don't include builtin views (these are already in the palette and likely not what the user is looking for)
        return !qualifiedName.startsWith(ANDROID_PKG_PREFIX) || qualifiedName.startsWith(ANDROID_SUPPORT_PKG_PREFIX);
      }, null);
      if (src != null) {
        newChild.setAttribute(null, ATTR_CLASS, src);
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
