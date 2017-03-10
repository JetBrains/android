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
package com.android.tools.idea.apk.viewer.dex;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import org.jetbrains.annotations.NotNull;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.DexBackedField;
import org.jf.dexlib2.dexbacked.DexBackedMethod;
import org.jf.dexlib2.dexbacked.reference.DexBackedFieldReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedMethodReference;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackageTreeCreator {
  public static final String PARAMS_DELIMITER = ",";

  @NotNull
  private static Multimap<String, MethodReference> getAllMethodReferencesByClassName(@NotNull DexBackedDexFile dexFile) {
    Multimap<String, MethodReference> methodsByClass = ArrayListMultimap.create();
    for (int i = 0, m = dexFile.getMethodCount(); i < m; i++) {
      MethodReference methodRef = new DexBackedMethodReference(dexFile, i);
      methodsByClass.put(methodRef.getDefiningClass(), methodRef);
    }

    return methodsByClass;
  }

  @NotNull
  private static Multimap<String, FieldReference> getAllFieldReferencesByClassName(@NotNull DexBackedDexFile dexFile) {
    Multimap<String, FieldReference> fieldsByClass = ArrayListMultimap.create();
    for (int i = 0, m = dexFile.getFieldCount(); i < m; i++) {
      DexBackedFieldReference fieldRef = new DexBackedFieldReference(dexFile, i);
      fieldsByClass.put(fieldRef.getDefiningClass(), fieldRef);
    }

    return fieldsByClass;
  }

  public PackageTreeNode constructPackageTree(@NotNull DexBackedDexFile dexFile) {
    PackageTreeNode root = new PackageTreeNode("root", PackageTreeNode.NodeType.PACKAGE, null);

    //get all methods and fields referenced in this dex (includes defined)
    Multimap<String, MethodReference> methodRefsByClassName = getAllMethodReferencesByClassName(dexFile);
    Multimap<String, FieldReference> fieldRefsByClassName = getAllFieldReferencesByClassName(dexFile);

    //remove methods and fields that are defined in this dex from the maps
    for (DexBackedClassDef classDef : dexFile.getClasses()) {
      for (DexBackedMethod method : classDef.getMethods()) {
        methodRefsByClassName.remove(classDef.getType(), method);
      }
      for (DexBackedField field : classDef.getFields()) {
        fieldRefsByClassName.remove(classDef.getType(), field);
      }
    }

    //add classes (and their methods and fields) defined in this file to the tree
    for (DexBackedClassDef classDef : dexFile.getClasses()) {
      String className = decodeClassName(classDef.getType());
      root.getOrInsertClass("", className, true, false);
      addMethods(root, className, classDef.getMethods(), true);
      addFields(root, className, classDef.getFields(), true);
    }

    //add method references which are not in a class defined in this dex file to the tree
    for (String className : methodRefsByClassName.keySet()) {
      String cleanClassName = decodeClassName(className);
      root.getOrInsertClass("", cleanClassName, false, false);
      addMethods(root, cleanClassName, methodRefsByClassName.get(className), false);
    }

    //add field references which are not in a class defined in this dex file to the tree
    for (String className : fieldRefsByClassName.keySet()) {
      String cleanClassName = decodeClassName(className);
      root.getOrInsertClass("", cleanClassName, false, false);
      addFields(root, cleanClassName, fieldRefsByClassName.get(className), false);
    }

    root.sort(Comparator.comparing(PackageTreeNode::getMethodRefCount).reversed());
    return root;
  }

  private void addMethods(PackageTreeNode root,
                          String className,
                          Iterable<? extends MethodReference> methodRefs,
                          boolean defined) {
    for (MethodReference methodRef : methodRefs) {
      String methodName = decodeMethodName(methodRef);
      String returnType = decodeClassName(methodRef.getReturnType());
      String params = decodeMethodParams(methodRef);
      String methodSig = returnType + " " + methodName + params;
      root.insertMethod(className, methodSig, defined);
    }
  }

  private void addFields(PackageTreeNode root,
                         String className,
                         Iterable<? extends FieldReference> fieldRefs,
                         boolean defined) {
    for (FieldReference fieldRef : fieldRefs) {
      String fieldName = fieldRef.getName();
      String fieldType = decodeClassName(fieldRef.getType());

      String fieldSig = fieldType + " " + fieldName;
      root.insertField(className, fieldSig, defined);
    }
  }

  private String decodeMethodParams(MethodReference methodRef) {
    String params = methodRef.getParameterTypes().stream()
      .map(String::valueOf)
      .map(DebuggerUtilsEx::signatureToName)
      .collect(Collectors.joining(PARAMS_DELIMITER));
    return "(" + params + ")";
  }


  private String decodeMethodName(MethodReference methodRef) {
    return methodRef.getName();
  }

  private String decodeClassName(String className) {
    return DebuggerUtilsEx.signatureToName(className);
  }
}
