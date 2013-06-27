/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.ViewInfo;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RenderedViewHierarchy {
  private final List<RenderedView> myRoots;
  private final PsiFile myFile;

  private RenderedViewHierarchy(@NotNull PsiFile file, @NotNull List<RenderedView> roots) {
    myFile = file;
    myRoots = roots;
  }

  @NotNull
  public static RenderedViewHierarchy create(@NotNull PsiFile file, @NotNull List<ViewInfo> roots) {
    return new RenderedViewHierarchy(file, convert(null, roots, 0, 0));
  }

  public List<RenderedView> getRoots() {
    return myRoots;
  }

  @NotNull
  private static List<RenderedView> convert(@Nullable RenderedView parent, @NotNull List<ViewInfo> roots, int parentX, int parentY) {
    List<RenderedView> views = new ArrayList<RenderedView>(roots.size());
    for (ViewInfo info : roots) {
      XmlTag tag = null;
      Object cookie = info.getCookie();
      if (cookie instanceof XmlTag) {
        tag = (XmlTag)cookie;
      }
      int x = info.getLeft();
      int y = info.getTop();
      int width = info.getRight() - x;
      int height = info.getBottom() - y;
      x += parentX;
      y += parentY;
      RenderedView view = new RenderedView(parent, tag, x, y, width, height);
      List<ViewInfo> children = info.getChildren();
      if (children != null && !children.isEmpty()) {
        view.setChildren(convert(view, children, x, y));
      }
      views.add(view);
    }
    return views;
  }

  // If I create maps, count views first:
  //private static int count(@NotNull List<ViewInfo> views) {
  //  int count = 0;
  //  for (ViewInfo info : views) {
  //    count++;
  //    List<ViewInfo> children = info.getChildren();
  //    if (children != null && !children.isEmpty()) {
  //      count += count(children);
  //    }
  //  }
  //
  //  return count;
  //}

  @Nullable
  public RenderedView findByOffset(int offset) {
    XmlTag tag = PsiTreeUtil.findElementOfClassAtOffset(myFile, offset, XmlTag.class, false);
    return (tag != null) ? findViewByTag(tag) : null;
  }

  @Nullable
  public RenderedView findLeafAt(int x, int y) {
    // Search BACKWARDS such that if the children are painted on top of each
    // other (as is the case in a FrameLayout) I pick the last one which will
    // be topmost!
    for (int i = myRoots.size() - 1; i >= 0; i--) {
      RenderedView view = myRoots.get(i);
      RenderedView leaf = view.findLeafAt(x, y);
      if (leaf != null) {
        return leaf;
      }
    }

    return null;
  }

  @Nullable
  public RenderedView findViewByTag(@NotNull XmlTag tag) {
    // TODO: Consider using lookup map
    for (RenderedView view : myRoots) {
      RenderedView match = view.findViewByTag(tag);
      if (match != null) {
        return match;
      }
    }

    return null;
  }
}
