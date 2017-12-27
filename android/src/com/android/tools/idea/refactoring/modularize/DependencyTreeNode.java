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
package com.android.tools.idea.refactoring.modularize;

import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;

public abstract class DependencyTreeNode extends CheckedTreeNode {

  private static final SimpleTextAttributes STRIKEOUT_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT | SimpleTextAttributes.STYLE_ITALIC, null);

  private final int myReferenceCount;

  public DependencyTreeNode(Object userObject) {
    this(userObject, 0);
  }

  public DependencyTreeNode(Object userObject, int referenceCount) {
    super(userObject);
    myReferenceCount = referenceCount;
  }

  public int getReferenceCount() {
    return myReferenceCount;
  }

  public abstract void render(@NotNull ColoredTreeCellRenderer renderer);

  @NotNull
  public SimpleTextAttributes getTextAttributes() {
    return isChecked() ? SimpleTextAttributes.REGULAR_ATTRIBUTES : STRIKEOUT_ATTRIBUTES;
  }

  public void renderReferenceCount(@NotNull  ColoredTreeCellRenderer renderer, SimpleTextAttributes inheritedAttributes) {
    if (myReferenceCount > 1) {
      SimpleTextAttributes derivedAttributes = new SimpleTextAttributes(
        inheritedAttributes.getStyle() | SimpleTextAttributes.STYLE_ITALIC | SimpleTextAttributes.STYLE_SMALLER,
        inheritedAttributes.getFgColor());
      renderer.append(" (" + myReferenceCount + " usages)", derivedAttributes);
    }
  }

  public void sort(Comparator<DependencyTreeNode> comparator) {
    for (int i = 0; i < getChildCount(); i++) {
      DependencyTreeNode node = (DependencyTreeNode)getChildAt(i);
      node.sort(comparator);
    }
    if (children != null) {
      Collections.sort(children, comparator);
    }
  }
}
