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
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class RenderedView implements Iterable<RenderedView> {
  @Nullable public final RenderedView parent;
  @Nullable public final XmlTag tag;
  public final ViewInfo view;
  public final int x;
  public final int y;
  public final int w;
  public final int h;
  private List<RenderedView> myChildren;

  public RenderedView(@Nullable RenderedView parent, @Nullable ViewInfo view, @Nullable XmlTag tag, int x, int y, int w, int h) {
    this.parent = parent;
    this.view = view;
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;
    this.tag = tag;
  }

  public final int x2() {
    return x + w;
  }

  public final int y2() {
    return y + h;
  }

  @Nullable
  public RenderedView getParent() {
    return parent;
  }

  public void setChildren(List<RenderedView> children) {
    myChildren = children;
  }

  @NotNull
  public List<RenderedView> getChildren() {
    return myChildren != null ? myChildren : Collections.<RenderedView>emptyList();
  }

  @Nullable
  public RenderedView findViewByTag(XmlTag tag) {
    if (this.tag == tag) {
      return this;
    }

    if (myChildren != null) {
      for (RenderedView child : myChildren) {
        RenderedView result = child.findViewByTag(tag);
        if (result != null) {
          return result;
        }
      }
    }

    return null;
  }

  @Nullable
  public List<RenderedView> findViewsByTag(XmlTag tag) {
    if (this.tag == tag) {
      //return Lists.newArrayList(this);
      List<RenderedView> list = Lists.newArrayList();
      list.add(this);
      return list;
    }

    List<RenderedView> result = null;

    if (myChildren != null) {
      for (RenderedView child : myChildren) {
        List<RenderedView> matches = child.findViewsByTag(tag);
        if (matches != null) {
          if (result != null) {
            result.addAll(matches);
          } else {
            result = matches;
          }
        }
      }
    }

    return result;
  }

  @Nullable
  public RenderedView findLeafAt(int px, int py) {
    if (myChildren != null) {
      // Search BACKWARDS such that if the children are painted on top of each
      // other (as is the case in a FrameLayout) I pick the last one which will
      // be topmost!
      for (int i = myChildren.size() - 1; i >= 0; i--) {
        RenderedView child = myChildren.get(i);
        RenderedView result = child.findLeafAt(px, py);
        if (result != null) {
          return result;
        }
      }
    }

    return (x <= px && y <= py && x + w >= px && y + h >= py) ? this : null;
  }

  public boolean isRoot() {
    return tag == null || !(tag.getParent() instanceof XmlTag);
  }

  // ---- Implements Iterable<RenderedView> ----
  @Override
  public Iterator<RenderedView> iterator() {
    if (myChildren == null) {
      return Iterators.emptyIterator();
    }

    return myChildren.iterator();
  }

  public Rectangle getBounds() {
    return new Rectangle(x, y, w, h);
  }
}
