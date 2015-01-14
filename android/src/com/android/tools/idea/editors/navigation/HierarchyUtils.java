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

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.rendering.RenderedView;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

class HierarchyUtils {
  private static String getTagName(@Nullable RenderedView leaf) {
    if (leaf != null) {
      if (leaf.tag != null) {
        return leaf.tag.getName();
      }
    }
    return "null";
  }

  private static String getTagName(@Nullable ViewInfo leaf) {
    if (leaf != null) {
      Object cookie = leaf.getCookie();
      if (cookie instanceof XmlTag) {
        XmlTag tag = (XmlTag)cookie;
        return tag.getName();
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

  static String toString(@Nullable RenderedView root) {
    if (root == null) {
      return "";
    }
    final StringBuilder buffer = new StringBuilder();
    new Object() {
      public void display(RenderedView view, String indent) {
        //noinspection StringConcatenationInsideStringBufferAppend
        buffer.append(indent + getClassName(view) + " " + getTagName(view) + " " + NavigationView.getViewId(view) + "\n");
        for (RenderedView c : view.getChildren()) {
          display(c, "  " + indent);
        }
      }
    }.display(root, "");
    return buffer.toString();
  }

  static String toString(@Nullable ViewInfo root) {
    if (root == null) {
      return "";
    }
    final StringBuilder buffer = new StringBuilder();
    new Object() {
      public void display(ViewInfo view, String indent) {
        //noinspection StringConcatenationInsideStringBufferAppend
        buffer.append(indent + getClassName(view) + " " + getTagName(view) + " " + NavigationView.getViewId(view) + "\n");
        for (ViewInfo c : view.getChildren()) {
          display(c, ".." + indent);
        }
      }
    }.display(root, "");
    return buffer.toString();
  }
}
