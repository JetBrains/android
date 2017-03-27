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
import org.jf.dexlib2.immutable.reference.ImmutableReference;
import org.jf.dexlib2.immutable.reference.ImmutableTypeReference;
import org.jf.dexlib2.util.ReferenceUtil;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackageTreeCreator {
  public static final String PARAMS_DELIMITER = ",";

  @Nullable private final ProguardMap myProguardMap;
  @Nullable private final ProguardUsagesMap myUsagesMap;
  private final boolean myDeobfuscateNames;

  public PackageTreeCreator(@Nullable ProguardMappings proguardMappings, boolean deobfuscateNames) {
    myProguardMap = proguardMappings == null ? null : proguardMappings.map;
    myUsagesMap = proguardMappings == null ? null : proguardMappings.usage;
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

    Collection<String> definedClasses = new ArrayList<>();
    Collection<MethodReference> definedMethods = new ArrayList<>();

    for (DexBackedClassDef def : dexFile.getClasses()) {
      definedClasses.add(def.getType());
      for (DexBackedMethod method : def.getMethods()) {
        definedMethods.add(ImmutableMethodReference.of(method));
      }
    }

    for (String className : methodRefsByClassName.keySet()) {
      TypeReference typeRef = typeRefsByName.get(className);
      String cleanClassName = decodeClassName(className, myDeobfuscateNames ? myProguardMap : null);
      root.getOrInsertClass(typeRef, "", cleanClassName, definedClasses.contains(typeRef.getType()), false, false);
      for (MethodReference methodRef : methodRefsByClassName.get(className)) {
        String methodName = decodeMethodName(methodRef, myDeobfuscateNames ? myProguardMap : null);
        String returnType = decodeClassName(methodRef.getReturnType(), myDeobfuscateNames ? myProguardMap : null);
        String params = decodeMethodParams(methodRef, myDeobfuscateNames ? myProguardMap : null);
        String methodSig = returnType + " " + methodName + params;
        root.insertMethod(typeRef, methodRef, cleanClassName, methodSig, definedMethods.contains(methodRef), false);
      }
    }

    for (String className : fieldRefsByClassName.keySet()) {
      TypeReference typeRef = typeRefsByName.get(className);
      String cleanClassName = decodeClassName(className, myDeobfuscateNames ? myProguardMap : null);
      boolean isClassDefined = definedClasses.contains(typeRef.getType());
      root.getOrInsertClass(typeRef, "", cleanClassName, isClassDefined, false, false);
      addFields(root, cleanClassName, typeRef, fieldRefsByClassName.get(className), isClassDefined);
    }

    //add classes, methods and fields removed by Proguard
    if (myUsagesMap != null) {
      for (String className : myUsagesMap.getClasses()) {
        root.getOrInsertClass(null, "", className, false, true, false);
      }
      Multimap<String, String> removedMethodsByClass = myUsagesMap.getMethodsByClass();
      for (String className : removedMethodsByClass.keySet()) {
        for (String removedMethodName : removedMethodsByClass.get(className)) {
          root.insertMethod(null, null, className, removedMethodName, false, true);
        }
      }
      Multimap<String, String> removedFieldsByClass = myUsagesMap.getFieldsByClass();
      for (String className : removedFieldsByClass.keySet()) {
        for (String removedFieldName : removedFieldsByClass.get(className)) {
          root.insertField(null, null, className, removedFieldName, false, true);
        }
      }
    }

    root.sort(Comparator.comparing(DexElementNode::getMethodRefCount).reversed());
    return root;
  }

  private void addFields(DexPackageNode root,
                         String className,
                         TypeReference typeRef,
                         Iterable<? extends FieldReference> fieldRefs,
                         boolean defined) {
    for (FieldReference fieldRef : fieldRefs) {
      String fieldName = decodeFieldName(fieldRef, myDeobfuscateNames ? myProguardMap : null);
      String fieldType = decodeClassName(fieldRef.getType(), myDeobfuscateNames ? myProguardMap : null);
      String fieldSig = fieldType + " " + fieldName;
      root.insertField(typeRef, fieldRef, className, fieldSig, defined, false);
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
