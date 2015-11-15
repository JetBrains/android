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
package com.android.tools.idea.gradle.structure.configurables.editor.treeview;

import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class GradleNode extends SimpleNode {
  protected static final GradleNode[] NO_CHILDREN = new GradleNode[0];

  private GradleNode[] myChildren;
  private boolean myAutoExpand;

  protected GradleNode(@Nullable GradleNode parentDescriptor) {
    super(null, parentDescriptor);
  }

  @Override
  public Object getElement() {
    return this;
  }

  public void setChildren(@NotNull Collection<GradleNode> children) {
    setChildren(children.toArray(new GradleNode[children.size()]));
  }

  public void setChildren(@NotNull GradleNode...children) {
    myChildren = children;
  }

  @Override
  @Nullable
  public GradleNode[] getChildren() {
    return myChildren;
  }

  public boolean isAutoExpand() {
    return myAutoExpand;
  }

  public void setAutoExpand(boolean autoExpand) {
    myAutoExpand = autoExpand;
  }
}
