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
import com.google.common.collect.Lists;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@linkplain com.android.tools.idea.rendering.RenderedViewHierarchy} builds up and queries a hierarchy of
 * {@link com.android.tools.idea.rendering.RenderedView}, which corresponds to a {@link com.android.ide.common.rendering.api.ViewInfo}
 * tree returned from layoutlib. In addition to building it up, it provides methods to find views corresponding to {@code x,y} coordinates
 * as well as to find the corresponding bounds for views, identified by {@link XmlTag} elements.
 */
public class RenderedViewHierarchy {
  private final List<RenderedView> myRoots;
  private final PsiFile myFile;
  private final List<RenderedView> myIncludedRoots;

  private RenderedViewHierarchy(@NotNull PsiFile file, @NotNull List<RenderedView> roots, boolean computeIncludeBounds) {
    myFile = file;
    myRoots = roots;

    if (computeIncludeBounds) {
      myIncludedRoots = Lists.newArrayList();
      for (RenderedView root : myRoots) {
        addIncludedBounds(root);
      }
    } else {
      myIncludedRoots = null;
    }
  }

  @NotNull
  public static RenderedViewHierarchy create(@NotNull PsiFile file, @NotNull List<ViewInfo> roots, boolean computeIncludeBounds) {
    return new RenderedViewHierarchy(file, convert(null, roots, 0, 0), computeIncludeBounds);
  }

  @NotNull
  public List<RenderedView> getRoots() {
    return myRoots;
  }

  /**
   * Returns the list of root views that were included in a layout that was rendered as included in another. This is typically
   * just one view, but can be more than one when the {@code <include/>} tag loads a layout whose root tag is a {@code <merge/>}.
   * Can be null when there are no included views.
   *
   * @return a list of included view roots, or null
   */
  @Nullable
  public List<RenderedView> getIncludedRoots() {
    return myIncludedRoots;
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
      ViewInfo bounds = RenderService.getSafeBounds(info);
      int x = bounds.getLeft();
      int y = bounds.getTop();
      int width = bounds.getRight() - x;
      int height = bounds.getBottom() - y;
      x += parentX;
      y += parentY;
      RenderedView view = new RenderedView(parent, info, tag, x, y, width, height);
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

  private void addIncludedBounds(RenderedView view) {
    if (view.tag != null) {
      myIncludedRoots.add(view);
    } else {
      for (RenderedView child : view.getChildren()) {
        addIncludedBounds(child);
      }
    }
  }

  @Nullable
  public List<RenderedView> findByOffset(int offset) {
    XmlTag tag = PsiTreeUtil.findElementOfClassAtOffset(myFile, offset, XmlTag.class, false);
    return (tag != null) ? findViewsByTag(tag) : null;
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

  @Nullable
  public List<RenderedView> findViewsByTag(@NotNull XmlTag tag) {
    List<RenderedView> result = null;
    for (RenderedView view : myRoots) {
      List<RenderedView> matches = view.findViewsByTag(tag);
      if (matches != null) {
        if (result != null) {
          result.addAll(matches);
        } else {
          result = matches;
        }
      }
    }

    return result;
  }
}
