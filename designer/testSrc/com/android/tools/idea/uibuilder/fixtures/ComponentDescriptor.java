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
package com.android.tools.idea.uibuilder.fixtures;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlTag;
import junit.framework.TestCase;

import java.awt.*;
import java.util.List;

import static com.android.SdkConstants.*;

public class ComponentDescriptor {
  @NonNull private final String myTagName;
  @NonNull List<Pair<String, String>> myAttributes = Lists.newArrayList();
  @NonNull private ComponentDescriptor[] myChildren = new ComponentDescriptor[0];
  @AndroidCoordinate private int myX;
  @AndroidCoordinate private int myY;
  @AndroidCoordinate private int myWidth;
  @AndroidCoordinate private int myHeight;
  @Nullable private ViewInfo myViewInfo;
  @Nullable private Object myViewObject;
  @Nullable private Object myLayoutParamsObject;

  public ComponentDescriptor(@NonNull String tagName) {
    myTagName = tagName;
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

  public ComponentDescriptor withAttribute(@NonNull String name, @NonNull String value) {
    myAttributes.add(Pair.create(name, value));
    return this;
  }

  public ComponentDescriptor id(@NonNull String id) {
    return withAttribute(ANDROID_URI, ATTR_ID, id);
  }

  public ComponentDescriptor text(@NonNull String text) {
    return withAttribute(ANDROID_URI, ATTR_TEXT, text);
  }

  public ComponentDescriptor width(@NonNull String width) {
    return withAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, width);
  }

  public ComponentDescriptor height(@NonNull String height) {
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

  public ComponentDescriptor withAttribute(@NonNull String namespace, @NonNull String name, @NonNull String value) {
    if (ANDROID_URI.equals(namespace)) {
      return withAttribute(PREFIX_ANDROID + name, value);
    }
    else if (TOOLS_URI.equals(namespace)) {
      return withAttribute(TOOLS_PREFIX + ":" + name, value);
    }
    myAttributes.add(Pair.create(name, value));
    return this;
  }

  private Rectangle getBounds() {
    return new Rectangle(myX, myY, myWidth, myHeight);
  }

  public ComponentDescriptor children(@NonNull ComponentDescriptor... children) {
    // Make sure that all the children have bounds that fit within this component
    Rectangle bounds = getBounds();
    for (ComponentDescriptor child : children) {
      TestCase.assertTrue("Expected parent layout with bounds " +
                          bounds +
                          " to fully contain child bounds " +
                          child.getBounds() +
                          " where parent=" +
                          this +
                          " and child=" +
                          child, bounds.contains(child.getBounds()));
    }

    myChildren = children;
    return this;
  }

  public ComponentDescriptor viewObject(@Nullable Object viewObject) {
    myViewObject = viewObject;
    return this;
  }

  public ComponentDescriptor layoutParamsObject(@Nullable Object layoutParamsObject) {
    myLayoutParamsObject = layoutParamsObject;
    return this;
  }

  public void appendXml(@NonNull StringBuilder sb, int depth) {
    for (int i = 0; i < depth; i++) {
      sb.append("  ");
    }
    sb.append('<');
    sb.append(myTagName);
    if (depth == 0) {
      sb.append(" xmlns:android=\"http://schemas.android.com/apk/res/android\"");
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
        child.appendXml(sb, depth + 1);
      }
      sb.append("</").append(myTagName).append(">\n");
    }
    else {
      sb.append("/>\n");
    }
  }

  @NonNull
  public ViewInfo createViewInfo(@Nullable ComponentDescriptor parent, @NonNull XmlTag tag) {
    TestCase.assertNull(myViewInfo);
    int left = myX;
    int top = myY;
    if (parent != null) {
      left -= parent.myX;
      top -= parent.myY;
    }
    int right = left + myWidth;
    int bottom = top + myHeight;
    myViewInfo = new ViewInfo(myTagName, tag, left, top, right, bottom, myViewObject, myLayoutParamsObject);

    List<ViewInfo> childList = Lists.newArrayList();
    XmlTag[] subTags = tag.getSubTags();
    TestCase.assertEquals(subTags.length, myChildren.length);
    for (int i = 0; i < subTags.length; i++) {
      ComponentDescriptor childDescriptor = myChildren[i];
      XmlTag childTag = subTags[i];
      childList.add(childDescriptor.createViewInfo(this, childTag));
    }
    myViewInfo.setChildren(childList);
    return myViewInfo;
  }
}
