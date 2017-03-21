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

import com.android.tools.idea.apk.viewer.dex.tree.DexElementNode;
import com.android.tools.idea.apk.viewer.dex.tree.DexPackageNode;
import com.android.tools.proguard.ProguardMap;
import com.android.tools.proguard.ProguardSeedsMap;
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
import org.jf.dexlib2.immutable.reference.ImmutableTypeReference;
import org.jf.dexlib2.util.ReferenceUtil;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackageTreeCreator {
  public static final String PARAMS_DELIMITER = ",";

  @Nullable private final ProguardMap myProguardMap;
  @Nullable private final ProguardSeedsMap mySeedsMap;
  @Nullable private final ProguardUsagesMap myUsagesMap;
  private final boolean myDeobfuscateNames;

  public PackageTreeCreator(@Nullable ProguardMappings proguardMappings, boolean deobfuscateNames) {
    myProguardMap = proguardMappings == null ? null : proguardMappings.map;
    myUsagesMap = proguardMappings == null ? null : proguardMappings.usage;
    mySeedsMap = proguardMappings == null ? null : proguardMappings.seeds;
    myDeobfuscateNames = deobfuscateNames;
  }

  @NotNull
  private static Multimap<String, MethodReference> getAllMethodReferencesByClassName(@NotNull DexBackedDexFile dexFile) {
    Multimap<String, MethodReference> methodsByClass = ArrayListMultimap.create();
    for (int i = 0, m = dexFile.getMethodCount(); i < m; i++) {
      MethodReference methodRef = ImmutableMethodReference.of(new DexBackedMethodReference(dexFile, i));
      methodsByClass.put(methodRef.getDefiningClass(), methodRef);
    }

    return methodsByClass;
  }

  @NotNull
  private static Multimap<String, FieldReference> getAllFieldReferencesByClassName(@NotNull DexBackedDexFile dexFile) {
    Multimap<String, FieldReference> fieldsByClass = ArrayListMultimap.create();
    for (int i = 0, m = dexFile.getFieldCount(); i < m; i++) {
      FieldReference fieldRef = ImmutableFieldReference.of(new DexBackedFieldReference(dexFile, i));
      fieldsByClass.put(fieldRef.getDefiningClass(), fieldRef);
    }

    return fieldsByClass;
  }

  @NotNull
  private static Map<String, TypeReference> getAllTypeReferencesByClassName(@NotNull DexBackedDexFile dexFile) {
    HashMap<String, TypeReference> typesByName = new HashMap<>();
    for (int i = 0, m = dexFile.getTypeCount(); i < m; i++) {
      TypeReference typeRef = ImmutableTypeReference.of(new DexBackedTypeReference(dexFile, i));
      typesByName.put(typeRef.getType(), typeRef);
    }

    return typesByName;
  }

  public DexPackageNode constructPackageTree(@NotNull DexBackedDexFile dexFile) {
    DexPackageNode root = new DexPackageNode("root");

    //get all methods, fields and types referenced in this dex (includes defined)
    Multimap<String, MethodReference> methodRefsByClassName = getAllMethodReferencesByClassName(dexFile);
    Multimap<String, FieldReference> fieldRefsByClassName = getAllFieldReferencesByClassName(dexFile);
    Map<String, TypeReference> typeRefsByName = getAllTypeReferencesByClassName(dexFile);

    //get only defined class names
    Collection<String> classes = dexFile.getClasses().stream().map(DexBackedClassDef::getType).collect(Collectors.toList());

    for (String className : methodRefsByClassName.keySet()) {
      TypeReference typeRef = typeRefsByName.get(className);
      String cleanClassName = decodeClassName(className);
      boolean isClassDefined = classes.contains(typeRef.getType());
      boolean isSeed = mySeedsMap != null && mySeedsMap.hasClass(className);
      root.getOrInsertClass(typeRef, "", cleanClassName, isClassDefined, isSeed, false, false);
      addMethods(root, cleanClassName, typeRef, methodRefsByClassName.get(className), isClassDefined);
    }

    for (String className : fieldRefsByClassName.keySet()) {
      TypeReference typeRef = typeRefsByName.get(className);
      String cleanClassName = decodeClassName(className);
      boolean isClassDefined = classes.contains(typeRef.getType());
      boolean isSeed = mySeedsMap != null && mySeedsMap.hasClass(className);
      root.getOrInsertClass(typeRef, "", cleanClassName, isClassDefined, isSeed,false, false);
      addFields(root, cleanClassName, typeRef, fieldRefsByClassName.get(className), isClassDefined);
    }

    //add classes, methods and fields removed by Proguard
    if (myUsagesMap != null) {
      for (String className : myUsagesMap.getClasses()) {
        root.getOrInsertClass(null, "", className, false, false, true, false);
      }
      Multimap<String, String> removedMethodsByClass = myUsagesMap.getMethodsByClass();
      for (String className : removedMethodsByClass.keySet()) {
        for (String removedMethodName : removedMethodsByClass.get(className)) {
          root.insertMethod(null, null, className, removedMethodName, false, false, true);
        }
      }
      Multimap<String, String> removedFieldsByClass = myUsagesMap.getFieldsByClass();
      for (String className : removedFieldsByClass.keySet()) {
        for (String removedFieldName : removedFieldsByClass.get(className)) {
          root.insertField(null, null, className, removedFieldName, false, false, true);
        }
      }
    }

    root.sort(Comparator.comparing(DexElementNode::getMethodRefCount).reversed());
    return root;
  }

  private void addMethods(DexPackageNode root,
                          String className,
                          TypeReference typeRef,
                          Iterable<? extends MethodReference> methodRefs,
                          boolean defined) {
    for (MethodReference methodRef : methodRefs) {
      String methodName = decodeMethodName(methodRef);
      String returnType = decodeClassName(methodRef.getReturnType());
      String params = decodeMethodParams(methodRef);
      String methodSig = returnType + " " + methodName + params;
      boolean seed = mySeedsMap != null
                     && (mySeedsMap.hasMethod(className, methodName + params)
                         || (methodName.equals("<init>")
                             && mySeedsMap.hasMethod(className, DebuggerUtilsEx.getSimpleName(className) + params)));
      root.insertMethod(typeRef, methodRef, className, methodSig, defined, seed, false);
    }
  }

  private void addFields(DexPackageNode root,
                         String className,
                         TypeReference typeRef,
                         Iterable<? extends FieldReference> fieldRefs,
                         boolean defined) {
    for (FieldReference fieldRef : fieldRefs) {
      String fieldName = fieldRef.getName();
      String fieldType = decodeClassName(fieldRef.getType());

      if (myProguardMap != null && myDeobfuscateNames) {
        fieldName = myProguardMap.getFieldName(className, fieldName);
      }

      String fieldSig = fieldType + " " + fieldName;
      boolean seed = mySeedsMap != null && mySeedsMap.hasField(className, fieldSig);
      root.insertField(typeRef, fieldRef, className, fieldSig, defined, seed, false);
    }
  }

  public String decodeMethodParams(@NotNull MethodReference methodRef) {
    Stream<String> params = methodRef.getParameterTypes().stream()
      .map(String::valueOf)
      .map(DebuggerUtilsEx::signatureToName);
    if (myProguardMap != null && myDeobfuscateNames) {
      params = params.map(myProguardMap::getClassName);
    }
    return "(" + params.collect(Collectors.joining(PARAMS_DELIMITER)) + ")";
  }


  private String decodeMethodName(@NotNull MethodReference methodRef) {
    if (myProguardMap != null && myDeobfuscateNames) {
      String className = myProguardMap.getClassName(DebuggerUtilsEx.signatureToName(methodRef.getDefiningClass()));
      String methodName = methodRef.getName();
      String sigWithoutName = ReferenceUtil.getMethodDescriptor(methodRef, true).substring(methodName.length());
      ProguardMap.Frame frame = myProguardMap.getFrame(className, methodName, sigWithoutName, null, -1);
      return frame.methodName;
    }
    else {
      return methodRef.getName();
    }
  }

  private String decodeClassName(@NotNull String className) {
    className = DebuggerUtilsEx.signatureToName(className);
    if (myProguardMap != null && myDeobfuscateNames) {
      className = myProguardMap.getClassName(className);
    }
    return className;
  }
}
