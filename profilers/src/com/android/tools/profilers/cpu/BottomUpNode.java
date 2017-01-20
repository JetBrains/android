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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.HNode;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class BottomUpNode extends CpuTreeNode<BottomUpNode> {

  private final List<HNode<MethodModel>> myPathNodes = new ArrayList<>();
  private final boolean myIsRoot;
  private boolean myChildrenBuilt;

  public BottomUpNode(String id) {
    super(id);
    myIsRoot = false;
    myChildrenBuilt = false;
  }

  public BottomUpNode(@NotNull HNode<MethodModel> node) {
    super("Root");
    myIsRoot = true;
    myChildrenBuilt = true;

    Queue<HNode<MethodModel>> queue = new LinkedList<>();
    queue.add(node);
    while (!queue.isEmpty()) {
      HNode<MethodModel> curNode = queue.poll();
      if (curNode.getParent() != null) {
        addPathNode(curNode);
        addNode(curNode);
      }
      for (HNode<MethodModel> child : curNode.getChildren()) {
        queue.add(child);
      }
    }

    Map<String, BottomUpNode> children = new HashMap<>();
    for (HNode<MethodModel> curNode : getNodes()) {
      assert curNode.getData() != null;
      String curId = curNode.getData().getId();
      BottomUpNode child = children.get(curId);
      if (child == null) {
        child = new BottomUpNode(curId);
        children.put(curId, child);
        addChild(child);
      }
      child.addPathNode(curNode);
      child.addNode(curNode);
    }

    for (BottomUpNode child : getChildren()) {
      child.buildChildren();
    }
  }

  private void addPathNode(@NotNull HNode<MethodModel> node) {
    myPathNodes.add(node);
  }

  public boolean buildChildren() {
    if (myChildrenBuilt) {
      return false;
    }

    Map<String, BottomUpNode> children = new HashMap<>();
    assert myPathNodes.size() == getNodes().size();
    for (int i = 0; i < myPathNodes.size(); ++i) {
      HNode<MethodModel> parent = myPathNodes.get(i).getParent();
      if (parent == null) {
        continue;
      }
      assert parent.getData() != null;
      String parentId = parent.getData().getId();
      BottomUpNode child = children.get(parentId);
      if (child == null) {
        child = new BottomUpNode(parentId);
        children.put(parentId, child);
        addChild(child);
      }
      child.addPathNode(parent);
      child.addNode(getNodes().get(i));
    }

    myChildrenBuilt = true;
    return true;
  }

  @Override
  public String getMethodName() {
    if (myIsRoot) {
      return "";
    }
    MethodModel method = myPathNodes.get(0).getData();
    return (method == null ? "" : method.getName());
  }

  @Override
  public String getPackage() {
    if (myIsRoot) {
      return "";
    }
    MethodModel method = myPathNodes.get(0).getData();
    return (method == null ? "" : method.getNameSpace());
  }
}
