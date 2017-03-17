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
package com.android.tools.idea.apk.viewer.dex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.*;

public class PackageTreeNode implements TreeNode {

  public enum NodeType { PACKAGE, CLASS, METHOD, FIELD };

  @NotNull private final String myName;
  @NotNull private final NodeType myNodeType;
  @NotNull private final List<PackageTreeNode> myNodes;
  @Nullable private final PackageTreeNode myParent;

  private int myMethodReferencesCount = 0;
  private int myDefinedMethodsCount = 0;
  private boolean hasClassDefinition;

  public PackageTreeNode(@NotNull String name, @NotNull NodeType type, @Nullable PackageTreeNode parent) {
    myName = name;
    myNodeType = type;
    myParent = parent;
    myNodes = new ArrayList<>();
  }

  public void sort(Comparator<PackageTreeNode> comparator) {
    for (PackageTreeNode node : myNodes) {
      node.sort(comparator);
    }

    Collections.sort(myNodes, comparator);
  }

  public void insertMethod(String qcn, @NotNull String methodSig, boolean hasClassDefinition) {
    PackageTreeNode classNode = getOrInsertClass("", qcn, hasClassDefinition, true);
    PackageTreeNode methodNode = classNode.getOrCreateChild(methodSig, NodeType.METHOD);
    methodNode.myMethodReferencesCount++;
    if (hasClassDefinition) {
      methodNode.myDefinedMethodsCount++;
    }
    methodNode.hasClassDefinition = hasClassDefinition;
  }

  public void insertField(String qcn, @NotNull String fieldSig, boolean hasClassDefinition) {

    PackageTreeNode classNode = getOrInsertClass("", qcn, hasClassDefinition, false);
    PackageTreeNode fieldNode = classNode.getOrCreateChild(fieldSig, NodeType.FIELD);
    fieldNode.hasClassDefinition = fieldNode.hasClassDefinition || hasClassDefinition;
  }

  public PackageTreeNode getOrInsertClass(@NotNull String parentPackage, @NotNull String qcn, boolean hasClassDefinition,
                                          boolean addMethodReference) {
    if (addMethodReference){
      myMethodReferencesCount++;
      if (hasClassDefinition) {
        myDefinedMethodsCount++;
      }
    }
    int i = qcn.indexOf(".");
    if (i < 0) {
      PackageTreeNode classNode = getOrCreateChild(qcn, NodeType.CLASS);
      classNode.hasClassDefinition = classNode.hasClassDefinition || hasClassDefinition;
      if (addMethodReference){
        classNode.myMethodReferencesCount++;
        if (hasClassDefinition) {
          classNode.myDefinedMethodsCount++;
        }
      }
      return classNode;
    }
    else {
      String segment = qcn.substring(0, i);
      String nextSegment = qcn.substring(i + 1);
      PackageTreeNode packageNode = getOrCreateChild(segment, NodeType.PACKAGE);
      packageNode.hasClassDefinition = packageNode.hasClassDefinition || hasClassDefinition;
      return packageNode.getOrInsertClass(combine(parentPackage, segment), nextSegment, hasClassDefinition, addMethodReference);
    }
  }

  private static String combine(@NotNull String parentPackage, @NotNull String childName) {
    return parentPackage.isEmpty() ? childName : parentPackage + "." + childName;
  }


  protected PackageTreeNode getOrCreateChild(@NotNull String name, @NotNull NodeType type) {
    for (PackageTreeNode node : myNodes) {
      if (name.equals(node.getName()) && type.equals(node.getNodeType())) {
        return node;
      }
    }

    PackageTreeNode node = new PackageTreeNode(name, type, this);

    myNodes.add(node);
    return node;
  }

  @NotNull
  public NodeType getNodeType() {
    return myNodeType;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public PackageTreeNode getChildAt(int i) {
    return myNodes.get(i);
  }

  @Override
  public int getChildCount() {
    return myNodes.size();
  }

  @Nullable
  @Override
  public PackageTreeNode getParent() {
    return myParent;
  }

  @Override
  public int getIndex(TreeNode treeNode) {
    //noinspection SuspiciousMethodCalls
    return myNodes.indexOf(treeNode);
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public boolean isLeaf() {
    return myNodes.isEmpty();
  }

  @Override
  public Enumeration children() {
    return Collections.enumeration(myNodes);
  }

  public int getMethodRefCount() {
    return myMethodReferencesCount;
  }

  public int getDefinedMethodsCount() {
    return myDefinedMethodsCount;
  }

  public boolean hasClassDefinition() {
    return hasClassDefinition;
  }

}