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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

class HierarchyUtils {
  private static final Logger LOG = Logger.getInstance(HierarchyUtils.class.getName());

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

  static void display(@Nullable RenderedView root) {
    if (root == null) {
      return;
    }
    new Object() {
      public void display(RenderedView view, String indent) {
        LOG.info(indent + getTagName(view) + " " + NavigationView.getViewId(view));
        for (RenderedView c : view.getChildren()) {
          display(c, "  " + indent);
        }
      }
    }.display(root, "");
  }

  static void display(@Nullable ViewInfo root) {
    if (root == null) {
      return;
    }
    new Object() {
      public void display(ViewInfo view, String indent) {
        LOG.info(indent + getTagName(view) + " " + NavigationView.getViewId(view));
        for (ViewInfo c : view.getChildren()) {
          display(c, ".." + indent);
        }
      }
    }.display(root, "");
  }
}
