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

import com.intellij.debugger.impl.DebuggerUtilsEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jf.dexlib2.iface.reference.MethodReference;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

public class PackageTreeNode implements TreeNode {
  public enum NodeType { PACKAGE, CLASS, METHOD };

  @NotNull private final String myPackageName;
  @NotNull private final String myName;
  @NotNull private final NodeType myNodeType;
  @Nullable private final PackageTreeNode myParent;
  @NotNull private final List<PackageTreeNode> myNodes;

  private int myMethodReferencesCount = 0;
  private int myDefinedMethodsCount = 0;

  public PackageTreeNode(@NotNull String packageName, @NotNull String name, @NotNull NodeType type, @Nullable PackageTreeNode parent) {
    myPackageName = packageName;
    myName = name;
    myNodeType = type;
    myParent = parent;
    myNodes = new ArrayList<>();
  }

  public void sortByCount() {
    for (PackageTreeNode node : myNodes) {
      node.sortByCount();
    }

    Collections.sort(myNodes, (o1, o2) -> o2.getMethodRefCount() - o1.getMethodRefCount());
  }

  public void insert(@NotNull String parentPackage, @NotNull String qcn, @NotNull MethodReference ref, boolean hasClassDefinition) {
    int i = qcn.indexOf(".");
    if (i < 0) {
      insertClass(parentPackage, qcn, ref, hasClassDefinition);
    }
    else {
      String segment = qcn.substring(0, i);
      String nextSegment = qcn.substring(i + 1);
      PackageTreeNode node = getOrCreateChild(parentPackage, segment, NodeType.PACKAGE);
      node.insert(combine(parentPackage, segment), nextSegment, ref, hasClassDefinition);
      myMethodReferencesCount++;
      if (hasClassDefinition) {
        myDefinedMethodsCount++;
      }
    }
  }

  private static String combine(@NotNull String parentPackage, @NotNull String childName) {
    return parentPackage.isEmpty() ? childName : parentPackage + "." + childName;
  }

  private void insertClass(@NotNull String parentPackage,
                           @NotNull String className,
                           @NotNull MethodReference ref,
                           boolean hasClassDefinition) {
    myMethodReferencesCount++;
    if (hasClassDefinition) {
      myDefinedMethodsCount++;
    }

    PackageTreeNode classNode = getOrCreateChild(parentPackage, className, NodeType.CLASS);
    classNode.insertMethod(ref, hasClassDefinition);
  }

  private void insertMethod(@NotNull MethodReference ref, boolean hasClassDefinition) {
    myMethodReferencesCount++;
    if (hasClassDefinition) {
      myDefinedMethodsCount++;
    }

    PackageTreeNode methodNode = new PackageTreeNode(ref.getDefiningClass(), formatMethod(ref), NodeType.METHOD, this);
    methodNode.myMethodReferencesCount++;
    if (hasClassDefinition) {
      methodNode.myDefinedMethodsCount++;
    }
    myNodes.add(methodNode);
  }

  private PackageTreeNode getOrCreateChild(String parentPackage, @NotNull String name, @NotNull NodeType type) {
    for (PackageTreeNode node : myNodes) {
      if (name.equals(node.getName())) {
        return node;
      }
    }

    PackageTreeNode node = new PackageTreeNode(parentPackage, name, type, this);
    myNodes.add(node);

    return node;
  }

  @NotNull
  private static String formatMethod(@NotNull MethodReference ref) {
    StringBuilder sb = new StringBuilder();

    sb.append(DebuggerUtilsEx.signatureToName(ref.getReturnType()));
    sb.append(' ');

    sb.append(ref.getName());

    String paramList = ref.getParameterTypes()
      .stream()
      .map(typeDesc -> DebuggerUtilsEx.signatureToName(typeDesc.toString()))
      .collect(Collectors.joining(", "));

    sb.append('(');
    sb.append(paramList);
    sb.append(')');

    return sb.toString();
  }

  @NotNull
  public NodeType getNodeType() {
    return myNodeType;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getQualifiedName() {
    return combine(myPackageName, myName);
  }

  @Override
  public TreeNode getChildAt(int i) {
    return myNodes.get(i);
  }

  @Override
  public int getChildCount() {
    return myNodes.size();
  }

  @Override
  public TreeNode getParent() {
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
}