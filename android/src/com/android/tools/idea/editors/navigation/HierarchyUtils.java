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
package com.android.tools.idea.editors.navigation;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.editors.navigation.macros.Analyser;
import com.android.tools.idea.rendering.RenderedView;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class HierarchyUtils {
  @Nullable
  static String getViewId(@Nullable RenderedView leaf) {
    if (leaf != null) {
      XmlTag tag = leaf.tag;
      if (tag != null) {
        String attributeValue = tag.getAttributeValue("id", SdkConstants.ANDROID_URI);
        int prefixLength = Analyser.getPrefix(attributeValue).length();
        if (attributeValue != null && prefixLength != 0) {
          return attributeValue.substring(prefixLength);
        }
      }
    }
    return null;
  }

  @NotNull
  static RenderedView getRoot(@NotNull RenderedView view) {
    while (true) {
      RenderedView parent = view.getParent();
      if (parent == null) {
        return view;
      }
      view = parent;
    }
  }

  @Nullable
  static RenderedView getNamedParent(@Nullable RenderedView view) {
    while (view != null && getViewId(view) == null) {
      view = view.getParent();
    }
    return view;
  }

  private static String getTagName(@Nullable RenderedView leaf) {
    if (leaf != null) {
      if (leaf.tag != null) {
        return leaf.tag.getName();
      }
    }
    return "null";
  }

  private static String getClassName(@Nullable ViewInfo leaf) {
    if (leaf != null) {
      return leaf.getViewObject().getClass().getSimpleName();
    }
    return "null";
  }

  private static String getClassName(@Nullable RenderedView leaf) {
    if (leaf != null) {
      return getClassName(leaf.view);
    }
    return "null";
  }

  private static void appendToBuilderIndented(RenderedView view, String indent, StringBuilder buffer) {
    //noinspection StringConcatenationInsideStringBufferAppend
    buffer.append(indent + getClassName(view) + " " + getTagName(view) + " " + getViewId(view) + "\n");
    for (RenderedView c : view.getChildren()) {
      appendToBuilderIndented(c, "  " + indent, buffer);
    }
  }

  static String toString(@Nullable RenderedView root) {
    if (root == null) {
      return "";
    }
    final StringBuilder buffer = new StringBuilder();
    appendToBuilderIndented(root, "", buffer);
    return buffer.toString();
  }
}
