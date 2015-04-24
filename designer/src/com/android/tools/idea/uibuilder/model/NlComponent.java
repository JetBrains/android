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
package com.android.tools.idea.uibuilder.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.AndroidPsiUtils;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Represents a component editable in the UI builder. A component has properties,
 * if visual it has bounds, etc.
 */
public class NlComponent {
  @NonNull  public XmlTag tag;
  @Nullable public List<NlComponent> children;
  @Nullable public ViewInfo viewInfo;
  @AndroidCoordinate public int x;
  @AndroidCoordinate public int y;
  @AndroidCoordinate public int w;
  @AndroidCoordinate public int h;
  private NlComponent myParent;

  public NlComponent(@NonNull XmlTag tag) {
    this.tag = tag;
  }

  public void setBounds(@AndroidCoordinate int x, @AndroidCoordinate int y, @AndroidCoordinate int w, @AndroidCoordinate int h) {
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;
  }

  public void addChild(@NonNull NlComponent component) {
    if (children == null) {
      children = Lists.newArrayList();
    }
    children.add(component);
    component.setParent(this);
  }

  public void removeChild(@NonNull NlComponent component) {
    if (children != null) {
      children.remove(component);
    }
    component.setParent(null);
  }

  @NonNull
  public Iterable<NlComponent> getChildren() {
    return children != null ? children : Collections.<NlComponent>emptyList();
  }

  @Nullable
  public NlComponent findViewByTag(@NonNull XmlTag tag) {
    if (this.tag == tag) {
      return this;
    }

    if (children != null) {
      for (NlComponent child : children) {
        NlComponent result = child.findViewByTag(tag);
        if (result != null) {
          return result;
        }
      }
    }

    return null;
  }

  @Nullable
  public List<NlComponent> findViewsByTag(@NonNull XmlTag tag) {
    List<NlComponent> result = null;

    if (children != null) {
      for (NlComponent child : children) {
        List<NlComponent> matches = child.findViewsByTag(tag);
        if (matches != null) {
          if (result != null) {
            result.addAll(matches);
          } else {
            result = matches;
          }
        }
      }
    }

    if (this.tag == tag) {
      if (result == null) {
        return Lists.newArrayList(this);
      }
      result.add(this);
    }

    return result;
  }


  @Nullable
  public NlComponent findLeafAt(@AndroidCoordinate int px, @AndroidCoordinate int py) {
    if (children != null) {
      // Search BACKWARDS such that if the children are painted on top of each
      // other (as is the case in a FrameLayout) I pick the last one which will
      // be topmost!
      for (int i = children.size() - 1; i >= 0; i--) {
        NlComponent child = children.get(i);
        NlComponent result = child.findLeafAt(px, py);
        if (result != null) {
          return result;
        }
      }
    }

    return (x <= px && y <= py && x + w >= px && y + h >= py) ? this : null;
  }

  public boolean isRoot() {
    return !(tag.getParent() instanceof XmlTag);
  }


  public static String toTree(@NonNull List<NlComponent> roots) {
    StringBuilder sb = new StringBuilder(200);
    for (NlComponent root : roots) {
      describe(sb, root, 0);
    }
    return sb.toString().trim();
  }

  private static void describe(@NonNull StringBuilder sb, @NonNull NlComponent component, int depth) {
    for (int i = 0; i < depth; i++) {
      sb.append("    ");
    }
    sb.append(describe(component));
    sb.append('\n');
    for (NlComponent child : component.getChildren()) {
      describe(sb, child, depth + 1);
    }
  }

  private static String describe(@NonNull NlComponent root) {
    return Objects.toStringHelper(root).omitNullValues()
      .add("tag", describe(root.tag))
      .add("bounds",  "[" + root.x + "," + root.y + ":" + root.w + "x" + root.h)
      .toString();
  }

  private static String describe(@Nullable XmlTag tag) {
    if (tag == null) {
      return "";
    } else {
      return '<' + tag.getName() + '>';
    }
  }

  /** Returns the ID of this component */
  @Nullable
  public String getId() {
    String id = AndroidPsiUtils.getAttributeSafely(tag, ANDROID_URI, ATTR_ID);
    if (id != null) {
      if (id.startsWith(NEW_ID_PREFIX)) {
        return id.substring(NEW_ID_PREFIX.length());
      } else if (id.startsWith(ID_PREFIX)) {
        return id.substring(ID_PREFIX.length());
      }
    }
    return null;
  }

  public int getBaseline() {
    try {
      if (viewInfo != null) {
        Object viewObject = viewInfo.getViewObject();
        return (Integer)viewObject.getClass().getMethod("getBaseline").invoke(viewObject);
      }
    }
    catch (Throwable ignore) {
    }

    return -1;
  }

  private Insets myMargins;
  private Insets myPadding;

  private static int fixDefault(int value) {
    return value == Integer.MIN_VALUE ? 0 : value;
  }

  @NotNull
  public Insets getMargins() {
    if (myMargins == null) {
      if (viewInfo == null) {
        return Insets.NONE;
      }
      try {
        Object layoutParams = viewInfo.getLayoutParamsObject();
        Class<?> layoutClass = layoutParams.getClass();

        int left = fixDefault(layoutClass.getField("leftMargin").getInt(layoutParams)); // TODO: startMargin?
        int top = fixDefault(layoutClass.getField("topMargin").getInt(layoutParams));
        int right = fixDefault(layoutClass.getField("rightMargin").getInt(layoutParams));
        int bottom = fixDefault(layoutClass.getField("bottomMargin").getInt(layoutParams));
        if (left == 0 && top == 0 && right == 0 && bottom == 0) {
          myMargins = Insets.NONE;
        } else {
          myMargins = new Insets(left, top, right, bottom);
        }
      }
      catch (Throwable e) {
        myMargins = Insets.NONE;
      }
    }
    return myMargins;
  }

  @NotNull
  public Insets getPadding() {
    if (myPadding == null) {
      if (viewInfo == null) {
        return Insets.NONE;
      }
      try {
        Object layoutParams = viewInfo.getViewObject();
        Class<?> layoutClass = layoutParams.getClass();

        int left = fixDefault((Integer)layoutClass.getMethod("getPaddingLeft").invoke(layoutParams)); // TODO: getPaddingStart!
        int top = fixDefault((Integer)layoutClass.getMethod("getPaddingTop").invoke(layoutParams));
        int right = fixDefault((Integer)layoutClass.getMethod("getPaddingRight").invoke(layoutParams));
        int bottom = fixDefault((Integer)layoutClass.getMethod("getPaddingBottom").invoke(layoutParams));
        if (left == 0 && top == 0 && right == 0 && bottom == 0) {
          myPadding = Insets.NONE;
        } else {
          myPadding = new Insets(left, top, right, bottom);
        }
      }
      catch (Throwable e) {
        myPadding = Insets.NONE;
      }
    }
    return myPadding;
  }

  @Nullable
  public NlComponent getParent() {
    return myParent;
  }

  public void setParent(@Nullable NlComponent parent) {
    myParent = parent;
  }
}
