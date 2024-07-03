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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_DEFAULT_NAV_HOST;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_NAV_GRAPH;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.CLASS_FRAGMENT;
import static com.android.AndroidXConstants.CLASS_V4_FRAGMENT;
import static com.android.SdkConstants.FQCN_NAV_HOST_FRAGMENT;
import static com.android.SdkConstants.VALUE_TRUE;

import com.android.resources.ResourceType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.sdklib.AndroidCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import java.util.EnumSet;
import java.util.List;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handler for the {@code <fragment>} tag
 */
class BaseFragmentHandler extends ViewHandler {

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_NAME,
      ATTR_LAYOUT,
      ATTR_CLASS,
      ATTR_NAV_GRAPH);
  }

  @NotNull
  @Override
  public String getTitleAttributes(@NotNull NlComponent component) {
    String name = component.getAttribute(ANDROID_URI, ATTR_NAME);
    return StringUtil.isEmpty(name) ? "" : name;
  }

  @Override
  @Language("XML")
  @NotNull
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
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
    if (insertType != InsertType.CREATE) {
      return true;
    }

    NlModel model = newChild.getModel();
    String className = newChild.getAttribute(ANDROID_URI, ATTR_NAME);
    if (className != null) {
      if (!FQCN_NAV_HOST_FRAGMENT.equals(className)) {
        return true;
      }

      String src = browseNavs(model, null);
      if (src == null) {
        // Remove the view; the insertion was canceled
        return false;
      }

      return NlWriteCommandActionUtil.compute(newChild, "Create Fragment", () -> {
        newChild.setAttribute(AUTO_URI, ATTR_NAV_GRAPH, src);
        newChild.setAttribute(AUTO_URI, ATTR_DEFAULT_NAV_HOST, VALUE_TRUE);
        return true;
      });
    }
    String src = browseClasses(model, null);
    if (src == null) {
      return false;
    }

    return NlWriteCommandActionUtil.compute(newChild, "Create Fragment", () -> {
      newChild.setAttribute(ANDROID_URI, ATTR_NAME, src);
      return true;
    });
  }

  @Nullable
  static String browseClasses(@NotNull NlModel model, @Nullable String existingValue) {
    return ViewEditor.displayClassInput(model,
                                        "Fragments",
                                        Sets.newHashSet(CLASS_FRAGMENT, CLASS_V4_FRAGMENT.oldName(), CLASS_V4_FRAGMENT.newName()),
                                        existingValue);
  }

  @Nullable
  static String browseNavs(@NotNull NlModel model, @Nullable String existingValue) {
    return ViewEditor.displayResourceInput(model, "Navigation Graphs", EnumSet.of(ResourceType.NAVIGATION));
  }

  @Override
  public void onActivateInDesignSurface(@NotNull NlComponent component, @AndroidCoordinate int x, @AndroidCoordinate int y) {
    String graph = component.getAttribute(AUTO_URI, ATTR_NAV_GRAPH);
    if (graph != null) {
      ViewEditor.openResourceFile(component.getModel(), graph);
    }
  }
}
