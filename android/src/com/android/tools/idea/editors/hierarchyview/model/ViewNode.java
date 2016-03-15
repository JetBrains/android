/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.hierarchyview.model;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.awt.*;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;

public class ViewNode implements TreeNode {

  // If the force state is set, the preview tries to render/hide the view
  // (depending on the parent's state)
  public enum ForcedState {
    NONE,
    VISIBLE,
    INVISIBLE;
  }

  @NotNull public final String name;
  public final Map<String, ViewProperty> namedProperties = Maps.newHashMap();
  public final List<ViewProperty> properties = Lists.newArrayList();
  public final List<ViewNode> children = Lists.newArrayList();

  @Nullable public final ViewNode parent;
  public final int index;
  @NotNull public final String hashCode;
  @NotNull public final String id;

  @NotNull public final DisplayInfo displayInfo;
  @NotNull public final Rectangle previewBox = new Rectangle();

  private boolean myParentVisible;
  private boolean myNodeDrawn;

  private ForcedState myForcedState = ForcedState.NONE;

  public ViewNode(@Nullable ViewNode parent, @NotNull String data) {
    this.parent = parent;
    index = this.parent == null ? 0 : this.parent.children.size();
    if (this.parent != null) {
      this.parent.children.add(this);
    }
    int delimIndex = data.indexOf('@');
    if (delimIndex < 0) {
      throw new IllegalArgumentException("Invalid format for ViewNode, missing @: " + data);
    }

    name = data.substring(0, delimIndex);
    data = data.substring(delimIndex + 1);
    delimIndex = data.indexOf(' ');
    hashCode = data.substring(0, delimIndex);

    if (data.length() > delimIndex + 1) {
      loadProperties(data.substring(delimIndex + 1).trim());
      id = getProperty("mID", "id").getValue();
    } else {
      // defaults in case properties are not available
      id = "unknown";
    }

    displayInfo = new DisplayInfo(this);
  }

  private void loadProperties(@NotNull String data) {
    int start = 0;
    boolean stop;

    do {
      int index = data.indexOf('=', start);
      ViewProperty property = new ViewProperty(data.substring(start, index));

      int index2 = data.indexOf(',', index + 1);
      int length = Integer.parseInt(data.substring(index + 1, index2));
      start = index2 + 1 + length;
      property.setValue(data.substring(index2 + 1, index2 + 1 + length));

      properties.add(property);
      namedProperties.put(property.fullName, property);

      stop = start >= data.length();
      if (!stop) {
        start += 1;
      }
    } while (!stop);

    Collections.sort(properties);
  }

  @Nullable
  public ViewProperty getProperty(String name, String... altNames) {
    ViewProperty property = namedProperties.get(name);
    for (int i = 0; property == null && i < altNames.length; i++) {
      property = namedProperties.get(altNames[i]);
    }
    return property;
  }

  /**
   * Recursively updates all the visibility parameter of the nodes.
   */
  public void updateNodeDrawn() {
    updateNodeDrawn(myParentVisible);
  }

  private void updateNodeDrawn(boolean parentVisible) {
    myParentVisible = parentVisible;
    if (myForcedState == ForcedState.NONE) {
      myNodeDrawn =  !displayInfo.willNotDraw && parentVisible && displayInfo.isVisible;
      parentVisible = parentVisible & displayInfo.isVisible;
    } else {
      myNodeDrawn = (myForcedState == ForcedState.VISIBLE) && parentVisible;
      parentVisible = myNodeDrawn;
    }
    for (ViewNode child : children) {
      child.updateNodeDrawn(parentVisible);
      myNodeDrawn |= (child.myNodeDrawn && child.displayInfo.isVisible);
    }
  }

  public boolean isDrawn() {
    return myNodeDrawn;
  }

  public boolean isParentVisible() {
    return myParentVisible;
  }

  @Override
  public String toString() {
    return name + "@" + hashCode;
  }

  @Override
  public ViewNode getChildAt(int childIndex) {
    return children.get(childIndex);
  }

  @Override
  public int getChildCount() {
    return children.size();
  }

  @Override
  public ViewNode getParent() {
    return parent;
  }

  @Override
  public int getIndex(TreeNode node) {
    return children.indexOf(node);
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public boolean isLeaf() {
    return getChildCount() == 0;
  }

  @Override
  public Enumeration children() {
    return Collections.enumeration(children);
  }


  public ForcedState getForcedState() {
    return myForcedState;
  }

  public void setForcedState(ForcedState forcedState) {
    myForcedState = forcedState;
  }

  /**
   * Parses the flat string representation of a view node and returns the root node.
   */
  public static ViewNode parseFlatString(@NotNull byte[] bytes) {
    String line;
    ViewNode root = null;
    ViewNode lastNode = null;
    int lastWhitespaceCount = Integer.MIN_VALUE;
    Stack<ViewNode> stack = new Stack<ViewNode>();

    final BufferedReader in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), Charsets.UTF_8));
    try {
      while ((line = in.readLine()) != null) {
        if ("DONE.".equalsIgnoreCase(line)) {
          break;
        }
        int whitespaceCount = 0;
        while (line.charAt(whitespaceCount) == ' ') {
          whitespaceCount++;
        }

        if (lastWhitespaceCount < whitespaceCount) {
          stack.push(lastNode);
        } else if (!stack.isEmpty()) {
          int count = lastWhitespaceCount - whitespaceCount;
          for (int i = 0; i < count; i++) {
            stack.pop();
          }
        }

        lastWhitespaceCount = whitespaceCount;
        ViewNode parent = null;
        if (!stack.isEmpty()) {
          parent = stack.peek();
        }
        lastNode = new ViewNode(parent, line.trim());
        if (root == null) {
          root = lastNode;
        }
      }
    } catch (IOException e) {
      return null;
    }

    if (root != null) {
      root.updateNodeDrawn(true);
    }
    return root;
  }
}
