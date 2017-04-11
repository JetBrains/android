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

import com.android.tools.idea.apk.viewer.dex.tree.*;
import com.android.tools.proguard.ProguardMap;
import com.android.tools.proguard.ProguardUsagesMap;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.DexBackedField;
import org.jf.dexlib2.dexbacked.DexBackedMethod;
import org.jf.dexlib2.dexbacked.reference.DexBackedFieldReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedMethodReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedTypeReference;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.TypeReference;
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.util.ReferenceUtil;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackageTreeCreator {
  public static final String PARAMS_DELIMITER = ",";

  @Nullable private final ProguardMap myProguardMap;
  @Nullable private final ProguardUsagesMap myUsagesMap;

  public PackageTreeCreator(@Nullable ProguardMappings proguardMappings, boolean deobfuscateNames) {
    myProguardMap = (deobfuscateNames && proguardMappings != null) ? proguardMappings.map : null;
    myUsagesMap = proguardMappings == null ? null : proguardMappings.usage;
  }

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
      FieldReference fieldRef = new DexBackedFieldReference(dexFile, i);
      fieldsByClass.put(fieldRef.getDefiningClass(), fieldRef);
    }

    return fieldsByClass;
  }

  @NotNull
  private static Map<String, TypeReference> getAllTypeReferencesByClassName(@NotNull DexBackedDexFile dexFile) {
    HashMap<String, TypeReference> typesByName = new HashMap<>();
    for (int i = 0, m = dexFile.getTypeCount(); i < m; i++) {
      TypeReference typeRef = new DexBackedTypeReference(dexFile, i);
      typesByName.put(typeRef.getType(), typeRef);
    }

    return typesByName;
  }

  @NotNull
  public DexPackageNode constructPackageTree(@NotNull Map<Path, DexBackedDexFile> dexFiles) {
    DexPackageNode root = new DexPackageNode("root");
    for (Map.Entry<Path, DexBackedDexFile> dexFile : dexFiles.entrySet()) {
      constructPackageTree(root, dexFile.getKey(), dexFile.getValue());
    }
    return root;
  }

  @NotNull
  public DexPackageNode constructPackageTree(@NotNull DexBackedDexFile dexFile) {
    DexPackageNode root = new DexPackageNode("root");
    constructPackageTree(root, null, dexFile);
    return root;
  }

  @NotNull
  public DexPackageNode constructPackageTree(@NotNull DexPackageNode root, @Nullable Path dexFilePath, @NotNull DexBackedDexFile dexFile) {
    //get all methods, fields and types referenced in this dex (includes defined)
    Multimap<String, MethodReference> methodRefsByClassName = getAllMethodReferencesByClassName(dexFile);
    Multimap<String, FieldReference> fieldRefsByClassName = getAllFieldReferencesByClassName(dexFile);
    Map<String, TypeReference> typeRefsByName = getAllTypeReferencesByClassName(dexFile);

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
      TypeReference typeRef = typeRefsByName.get(classDef.getType());
      String className = decodeClassName(classDef.getType(), myProguardMap);
      DexClassNode classNode = root.getOrCreateClass("", className, typeRef);
      classNode.setUserObject(dexFilePath);
      classNode.setDefined(true);
      addMethods(classNode, classDef.getMethods(), dexFilePath);
      addFields(classNode, classDef.getFields(), dexFilePath);
    }

    //add method references which are not in a class defined in this dex file to the tree
    for (String className : methodRefsByClassName.keySet()) {
      TypeReference typeRef = typeRefsByName.get(className);
      String cleanClassName = decodeClassName(className, myProguardMap);
      DexClassNode classNode = root.getOrCreateClass("", cleanClassName, typeRef);
      addMethods(classNode, methodRefsByClassName.get(className), dexFilePath);
    }

    //add field references which are not in a class defined in this dex file
    for (String className : fieldRefsByClassName.keySet()) {
      TypeReference typeRef = typeRefsByName.get(className);
      String cleanClassName = decodeClassName(className, myProguardMap);
      DexClassNode classNode = root.getOrCreateClass("", cleanClassName, typeRef);
      addFields(classNode, fieldRefsByClassName.get(className), dexFilePath);
    }

    //add classes, methods and fields removed by Proguard
    if (myUsagesMap != null) {
      for (String className : myUsagesMap.getClasses()) {
        DexClassNode classNode = root.getOrCreateClass("", className, null);
        classNode.setDefined(false);
        classNode.setRemoved(true);
      }
      Multimap<String, String> removedMethodsByClass = myUsagesMap.getMethodsByClass();
      for (String className : removedMethodsByClass.keySet()) {
        DexClassNode classNode = root.getOrCreateClass("", className, null);
        for (String removedMethodName : removedMethodsByClass.get(className)) {
          DexMethodNode methodNode = new DexMethodNode(removedMethodName, null);
          methodNode.setDefined(false);
          methodNode.setRemoved(true);
          classNode.add(methodNode);
        }
      }
      Multimap<String, String> removedFieldsByClass = myUsagesMap.getFieldsByClass();
      for (String className : removedFieldsByClass.keySet()) {
        DexClassNode classNode = root.getOrCreateClass("", className, null);
        for (String removedFieldName : removedFieldsByClass.get(className)) {
          DexFieldNode fieldNode = new DexFieldNode(removedFieldName, null);
          fieldNode.setDefined(false);
          fieldNode.setRemoved(true);
          classNode.add(fieldNode);
        }
      }
    }

    root.update();
    root.sort(Comparator.comparing(DexElementNode::getMethodReferencesCount).reversed());
    return root;
  }

  private void addMethods(@NotNull DexClassNode classNode,
                          @NotNull Iterable<? extends MethodReference> methodRefs, Path dexFilePath) {
    for (MethodReference methodRef : methodRefs) {
      String methodName = decodeMethodName(methodRef, myProguardMap);
      String returnType = decodeClassName(methodRef.getReturnType(), myProguardMap);
      String params = decodeMethodParams(methodRef, myProguardMap);
      String methodSig = returnType + " " + methodName + params;
      DexMethodNode methodNode = classNode.getChildByType(methodSig, DexMethodNode.class);
      if (methodNode == null){
        methodNode = new DexMethodNode(methodSig, ImmutableMethodReference.of(methodRef));
        classNode.add(methodNode);
      }
      if (methodRef instanceof DexBackedMethod) {
        methodNode.setDefined(true);
        methodNode.setUserObject(dexFilePath);
      }
    }
  }

  private void addFields(@NotNull DexClassNode classNode,
                         @NotNull Iterable<? extends FieldReference> fieldRefs, Path dexFilePath) {
    for (FieldReference fieldRef : fieldRefs) {
      String fieldName = decodeFieldName(fieldRef, myProguardMap);
      String fieldType = decodeClassName(fieldRef.getType(), myProguardMap);
      String fieldSig = fieldType + " " + fieldName;
      DexFieldNode fieldNode = classNode.getChildByType(fieldSig, DexFieldNode.class);
      if (fieldNode == null){
        fieldNode = new DexFieldNode(fieldSig, ImmutableFieldReference.of(fieldRef));
        classNode.add(fieldNode);
      }
      if (fieldRef instanceof DexBackedField) {
        fieldNode.setDefined(true);
        fieldNode.setUserObject(dexFilePath);
      }
    }
  }

  public static String decodeFieldName(@NotNull FieldReference fieldRef, @Nullable ProguardMap proguardMap) {
    String fieldName = fieldRef.getName();
    if (proguardMap != null) {
      String className = decodeClassName(fieldRef.getDefiningClass(), proguardMap);
      fieldName = proguardMap.getFieldName(className, fieldName);
    }
    return fieldName;
  }

  public static String decodeMethodParams(@NotNull MethodReference methodRef, @Nullable ProguardMap proguardMap) {
    Stream<String> params = methodRef.getParameterTypes().stream()
      .map(String::valueOf)
      .map(DebuggerUtilsEx::signatureToName);
    if (proguardMap != null) {
      params = params.map(proguardMap::getClassName);
    }
    return "(" + params.collect(Collectors.joining(PARAMS_DELIMITER)) + ")";
  }


  public static String decodeMethodName(@NotNull MethodReference methodRef, @Nullable ProguardMap proguardMap) {
    if (proguardMap != null) {
      String className = proguardMap.getClassName(DebuggerUtilsEx.signatureToName(methodRef.getDefiningClass()));
      String methodName = methodRef.getName();
      String sigWithoutName = ReferenceUtil.getMethodDescriptor(methodRef, true).substring(methodName.length());
      ProguardMap.Frame frame = proguardMap.getFrame(className, methodName, sigWithoutName, null, -1);
      return frame.methodName;
    }
    else {
      return methodRef.getName();
    }
  }

  public static String decodeClassName(@NotNull String className, @Nullable ProguardMap proguardMap) {
    className = DebuggerUtilsEx.signatureToName(className);
    if (proguardMap != null) {
      className = proguardMap.getClassName(className);
    }
    return className;
  }
}
