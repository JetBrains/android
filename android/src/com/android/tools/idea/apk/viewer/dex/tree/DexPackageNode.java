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
package com.android.tools.idea.apk.viewer.dex.tree;

import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.TypeReference;

import javax.swing.*;

public class DexPackageNode extends DexElementNode {

  public DexPackageNode(@NotNull String name) {
    super(name, true);
  }

  @Override
  public Icon getIcon(){
    return PlatformIcons.PACKAGE_ICON;
  }

  public void insertMethod(TypeReference typeRef,
                           MethodReference methodRef,
                           String qcn,
                           @NotNull String methodSig,
                           boolean hasClassDefinition,
                           boolean isRemoved) {
    DexClassNode classNode = getOrInsertClass(typeRef, "", qcn, hasClassDefinition, false, !isRemoved);
    DexMethodNode methodNode = classNode.getChildByType(methodSig, DexMethodNode.class);
    if (methodNode == null){
      methodNode = new DexMethodNode(methodSig, methodRef);
      classNode.add(methodNode);
    }
    if (!isRemoved) {
      methodNode.myMethodReferencesCount++;
      if (hasClassDefinition) {
        methodNode.myDefinedMethodsCount++;
      }
    }
    methodNode.removed = isRemoved;
    methodNode.hasClassDefinition = hasClassDefinition;
  }

  public void insertField(TypeReference typeRef, FieldReference fieldRef, String qcn, @NotNull String fieldSig, boolean hasClassDefinition,
                          boolean isRemoved) {

    DexClassNode classNode = getOrInsertClass(typeRef, "", qcn, hasClassDefinition, false, false);
    DexFieldNode fieldNode = classNode.getChildByType(fieldSig, DexFieldNode.class);
    if (fieldNode == null){
      fieldNode = new DexFieldNode(fieldSig, fieldRef);
      classNode.add(fieldNode);
    }
    fieldNode.removed = isRemoved;
    fieldNode.hasClassDefinition = fieldNode.hasClassDefinition || hasClassDefinition;
  }

  public DexClassNode getOrInsertClass(@Nullable TypeReference typeRef,
                                       @NotNull String parentPackage,
                                       @NotNull String qcn,
                                       boolean hasClassDefinition,
                                       boolean isRemoved,
                                       boolean addMethodReference) {
    if (addMethodReference){
      myMethodReferencesCount++;
      if (hasClassDefinition) {
        myDefinedMethodsCount++;
      }
    }
    int i = qcn.indexOf(".");
    if (i < 0) {
      DexClassNode classNode = getChildByType(qcn, DexClassNode.class);
      if (classNode == null){
        classNode = new DexClassNode(qcn, typeRef);
        add(classNode);
      }
      classNode.removed = classNode.removed && isRemoved;
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
      DexPackageNode packageNode = getChildByType(segment, DexPackageNode.class);
      if (packageNode == null){
        packageNode = new DexPackageNode(segment);
        add(packageNode);
      }
      packageNode.removed = packageNode.removed && isRemoved;
      packageNode.hasClassDefinition = packageNode.hasClassDefinition || hasClassDefinition;
      return packageNode.getOrInsertClass(typeRef, combine(parentPackage, segment), nextSegment, hasClassDefinition, isRemoved, addMethodReference);
    }
  }

  private static String combine(@NotNull String parentPackage, @NotNull String childName) {
    return parentPackage.isEmpty() ? childName : parentPackage + "." + childName;
  }
}