/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.fixtures;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.rendering.api.ViewType;
import com.android.tools.idea.rendering.parsers.TagSnapshot;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.utils.XmlUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ComponentDescriptor {
  @NotNull private static final Splitter SPLITTER = Splitter.on(":").omitEmptyStrings().trimResults().limit(2);
  @NotNull private final String myTagName;
  @NotNull final List<Pair<String, String>> myAttributes = Lists.newArrayList();
  @NotNull private ComponentDescriptor[] myChildren = new ComponentDescriptor[0];
  @AndroidCoordinate private int myX;
  @AndroidCoordinate private int myY;
  @AndroidCoordinate private int myWidth;
  @AndroidCoordinate private int myHeight;

  @Nullable private Object myViewObject;
  @Nullable private Object myLayoutParamsObject;
  private ViewType myViewType;
  private boolean myUseMockView;

  public ComponentDescriptor(@NotNull String tagName) {
    myTagName = tagName;
  }

  /**
   * Creates a mock {@link android.view.View} object that matches the current component settings (i.e. bounds and children list).
   */
  @NotNull
  private android.view.View createMockView() {
    android.view.View view;
    if (myChildren.length > 0) {
      android.view.ViewGroup viewGroup = mock(android.view.ViewGroup.class);
      when(viewGroup.getChildCount()).thenReturn(myChildren.length);
      doAnswer(invocation -> {
        Integer i = invocation.getArgument(0);
        return myChildren[i].myViewObject;
      }).when(viewGroup).getChildAt(anyInt());
      view = viewGroup;
    }
    else {
      view = mock(android.view.View.class);
    }
    when(view.getX()).thenReturn((float)myX);
    when(view.getY()).thenReturn((float)myY);
    when(view.getWidth()).thenReturn(myWidth);
    when(view.getHeight()).thenReturn(myHeight);
    when(view.getMeasuredWidth()).thenReturn(myWidth);
    when(view.getMeasuredHeight()).thenReturn(myHeight);

    return view;
  }

  @NotNull
  public ComponentDescriptor withMockView() {
    assert myViewObject == null : "You already set a view object";

    myUseMockView = true;

    return this;
  }

  public ComponentDescriptor withBounds(@AndroidCoordinate int x,
                                        @AndroidCoordinate int y,
                                        @AndroidCoordinate int width,
                                        @AndroidCoordinate int height) {
    myX = x;
    myY = y;
    myWidth = width;
    myHeight = height;
    return this;
  }

  public ComponentDescriptor withAttribute(@NotNull String name, @NotNull String value) {
    myAttributes.add(Pair.create(name, value));
    return this;
  }

  public ComponentDescriptor id(@NotNull String id) {
    if (id.isEmpty()) {
      // Allow id to be specified as optional in the fluent API, e.g.
      //  component().text("foo").id(ids ? "@+id/foo" : "")
      return this;
    }
    return withAttribute(ANDROID_URI, ATTR_ID, id);
  }

  public ComponentDescriptor text(@NotNull String text) {
    return withAttribute(ANDROID_URI, ATTR_TEXT, text);
  }

  public ComponentDescriptor width(@NotNull String width) {
    return withAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, width);
  }

  public ComponentDescriptor height(@NotNull String height) {
    return withAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, height);
  }

  public ComponentDescriptor matchParentWidth() {
    return width(VALUE_MATCH_PARENT);
  }

  public ComponentDescriptor matchParentHeight() {
    return height(VALUE_MATCH_PARENT);
  }

  public ComponentDescriptor wrapContentWidth() {
    return width(VALUE_WRAP_CONTENT);
  }

  public ComponentDescriptor wrapContentHeight() {
    return height(VALUE_WRAP_CONTENT);
  }

  public ComponentDescriptor withAttribute(@NotNull String namespace, @NotNull String name, @NotNull String value) {
    switch (namespace) {
      case ANDROID_URI:
        return withAttribute(PREFIX_ANDROID + name, value);
      case TOOLS_URI:
        return withAttribute(TOOLS_PREFIX + ":" + name, value);
      case AUTO_URI:
        return withAttribute(APP_PREFIX + ":" + name, value);
    }
    myAttributes.add(Pair.create(name, value));
    return this;
  }

  private Rectangle getBounds() {
    return new Rectangle(myX, myY, myWidth, myHeight);
  }

  public ComponentDescriptor children(@NotNull ComponentDescriptor... children) {
    // Make sure that all the children have bounds that fit within this component
    Rectangle bounds = getBounds();
    for (ComponentDescriptor child : children) {
      if (!child.myTagName.equalsIgnoreCase("tag")) {
        assertTrue("Expected parent layout with bounds " +
                   bounds +
                   " to fully contain child bounds " +
                   child.getBounds() +
                   " where parent=" +
                   this +
                   " and child=" +
                   child, bounds.contains(child.getBounds()));
      }
    }

    myChildren = children;
    return this;
  }

  public ComponentDescriptor tags(@NotNull ComponentDescriptor... children) {
    myChildren = children;
    return this;
  }

  public ComponentDescriptor unboundedChildren(@NotNull ComponentDescriptor... children) {
    myChildren = children;
    return this;
  }

  public ComponentDescriptor viewObject(@Nullable Object viewObject) {
    assert !myUseMockView : "You can not set a view object if you already called withMockView()";

    myViewObject = viewObject;
    return this;
  }

  public ComponentDescriptor layoutParamsObject(@Nullable Object layoutParamsObject) {
    myLayoutParamsObject = layoutParamsObject;
    return this;
  }

  public ComponentDescriptor viewType(@NotNull ViewType viewType) {
    myViewType = viewType;
    return this;
  }

  public ComponentDescriptor removeChild(ComponentDescriptor child) {
    assertThat(myChildren).asList().contains(child);
    List<ComponentDescriptor> list = Lists.newArrayList(myChildren);
    list.remove(child);
    myChildren = list.toArray(new ComponentDescriptor[0]);
    return this;
  }

  public ComponentDescriptor addChild(@NotNull ComponentDescriptor child, @Nullable ComponentDescriptor before) {
    assertThat(myChildren).asList().doesNotContain(child);
    List<ComponentDescriptor> list = Lists.newArrayList(myChildren);
    if (before != null) {
      assertThat(myChildren).asList().contains(before);
      int index = ArrayUtil.indexOf(myChildren, before);
      list.add(index, child);
    }
    else {
      list.add(child);
    }
    myChildren = list.toArray(new ComponentDescriptor[0]);
    return this;
  }

  @Nullable
  public ComponentDescriptor findById(@NotNull String id) {
    assertThat(id).startsWith("@");
    for (Pair<String, String> pair : myAttributes) {
      if (ATTR_ID.equals(pair.getFirst())) {
        if (id.equals(pair.getSecond())) {
          return this;
        }
      }
    }
    for (ComponentDescriptor child : myChildren) {
      ComponentDescriptor match = child.findById(id);
      if (match != null) {
        return match;
      }
    }
    return null;
  }

  @Nullable
  public ComponentDescriptor findByPath(@NotNull String... path) {
    assertThat(path).asList().isNotEmpty();
    String tagName = path[0];
    if (myTagName.equals(tagName)) {
      if (path.length == 1) {
        return this;
      }
      String[] remainingPath = Arrays.copyOfRange(path, 1, path.length);
      for (ComponentDescriptor child : myChildren) {
        ComponentDescriptor match = child.findByPath(remainingPath);
        if (match != null) {
          return match;
        }
      }
    }
    return null;
  }

  @Nullable
  public ComponentDescriptor findByTag(@NotNull String tag) {
    if (myTagName.equals(tag)) {
      return this;
    }
    for (ComponentDescriptor child : myChildren) {
      ComponentDescriptor match = child.findByTag(tag);
      if (match != null) {
        return match;
      }
    }
    return null;
  }

  @Nullable
  public ComponentDescriptor findByBounds(@AndroidCoordinate int x,
                                          @AndroidCoordinate int y,
                                          @AndroidCoordinate int width,
                                          @AndroidCoordinate int height) {
    if (x == myX && y == myY && width == myWidth && height == myHeight) {
      return this;
    }
    for (ComponentDescriptor child : myChildren) {
      ComponentDescriptor match = child.findByBounds(x, y, width, height);
      if (match != null) {
        return match;
      }
    }
    return null;
  }

  public void appendXml(@NotNull StringBuilder sb, int depth) {
    for (int i = 0; i < depth; i++) {
      sb.append("  ");
    }
    sb.append('<');
    sb.append(myTagName);
    if (depth == 0) {
      appendReferencedNamespaces(sb);
    }
    for (Pair<String, String> attribute : myAttributes) {
      sb.append("\n");
      for (int i = 0; i < depth + 1; i++) {
        sb.append("  ");
      }
      String name = attribute.getFirst();
      String value = attribute.getSecond();
      sb.append(name).append("=\"").append(XmlUtils.toXmlAttributeValue(value)).append("\"");
    }

    if (myChildren.length > 0) {
      sb.append(">\n");
      for (ComponentDescriptor child : myChildren) {
        sb.append("\n");
        child.appendXml(sb, depth + 1);
      }
      sb.append("\n</").append(myTagName).append(">\n");
    }
    else {
      sb.append("/>\n");
    }
  }

  private void appendReferencedNamespaces(@NotNull StringBuilder sb) {
    Map<String, String> namespaces = new HashMap<>();
    findUsedNamespaces(namespaces);
    List<String> prefixes = new ArrayList<>(namespaces.keySet());
    prefixes.sort(String::compareTo);
    int indent = 1;
    if (namespaces.isEmpty()) {
      return;
    }
    for (String prefix : prefixes) {
      for (int i = 0; i < indent; i++) {
        sb.append(" ");
      }
      indent = myTagName.length() + 1;
      sb.append("xmlns:").append(prefix).append("=\"").append(namespaces.get(prefix)).append("\"\n");
    }
    // Remove the last \n
    sb.setLength(sb.length() - 1);
  }

  private void findUsedNamespaces(@NotNull Map<String, String> namespaces) {
    for (Pair<String, String> attribute : myAttributes) {
      String name = attribute.getFirst();
      String value = attribute.getSecond();
      List<String> prefixAndName = SPLITTER.splitToList(name);
      if (prefixAndName.size() != 2) {
        continue;
      }
      String prefix = prefixAndName.get(0);
      name = prefixAndName.get(1);
      if (prefix.equals(XMLNS)) {
        namespaces.put(name, value);
      }
      else if (!namespaces.containsKey(prefix)) {
        switch (prefix) {
          case ANDROID_NS_NAME:
            namespaces.put(prefix, ANDROID_URI);
            break;
          case APP_PREFIX:
            namespaces.put(prefix, AUTO_URI);
            break;
          case TOOLS_PREFIX:
            namespaces.put(prefix, TOOLS_URI);
            break;
          default:
            throw new IllegalArgumentException("Unknown namespace prefix: " + prefix);
        }
      }
    }
    for (ComponentDescriptor child : myChildren) {
      child.findUsedNamespaces(namespaces);
    }
  }

  @NotNull
  public ViewInfo createViewInfo(@Nullable ComponentDescriptor parent, @NotNull XmlTag tag) {
    if (myViewObject == null && myUseMockView) {
      myViewObject = createMockView();
    }

    int left = myX;
    int top = myY;
    if (parent != null) {
      left -= parent.myX;
      top -= parent.myY;
    }
    int right = left + myWidth;
    int bottom = top + myHeight;
    TagSnapshot snapshot = TagSnapshot.createTagSnapshotWithoutChildren(tag);

    TestViewInfo viewInfo = new TestViewInfo(myTagName, snapshot, left, top, right, bottom, myViewObject, myLayoutParamsObject);
    viewInfo.setExtendedInfo((int) (0.8 * (bottom - top)), 0, 0, 0, 0);
    if (myViewType != null) {
      viewInfo.setViewType(myViewType);
    }

    List<ViewInfo> childList = Lists.newArrayList();
    XmlTag[] subTags = tag.getSubTags();
    assertEquals(subTags.length, myChildren.length);
    for (int i = 0; i < subTags.length; i++) {
      ComponentDescriptor childDescriptor = myChildren[i];
      XmlTag childTag = subTags[i];
      childList.add(childDescriptor.createViewInfo(this, childTag));
    }
    viewInfo.setChildren(childList);
    return viewInfo;
  }

  private static final class TestViewInfo extends ViewInfo {
    private ViewType myViewType;

    private TestViewInfo(@NotNull String name,
                         @NotNull Object cookie,
                         int left,
                         int top,
                         int right,
                         int bottom,
                         @Nullable Object viewObject,
                         @Nullable Object layoutParamsObject) {
      super(name, cookie, left, top, right, bottom, viewObject, layoutParamsObject);
      myViewType = ViewType.USER;
    }

    @Override
    public ViewType getViewType() {
      return myViewType;
    }

    private void setViewType(@NotNull ViewType viewType) {
      myViewType = viewType;
    }
  }
}
