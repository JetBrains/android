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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.reference.DexBackedMethodReference;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class DexParser {
  private final ListeningExecutorService myExecutor;
  private final Future<DexBackedDexFile> myDexFileFuture;

  public DexParser(@NotNull ListeningExecutorService executorService, @NotNull VirtualFile file) {
    myExecutor = executorService;
    myDexFileFuture = myExecutor.submit(() -> getDexFile(file));
  }

  public ListenableFuture<PackageTreeNode> constructMethodRefCountTree() {
    return myExecutor.submit(this::constructMethodRefTree);
  }

  public ListenableFuture<DexFileStats> getDexFileStats() {
    return myExecutor.submit(this::getDexStats);
  }

  @NotNull
  private PackageTreeNode constructMethodRefTree() {
    DexBackedDexFile dexFile;
    try {
      dexFile = myDexFileFuture.get();
    }
    catch (Exception e) {
      return new PackageTreeNode("Unknown", e.toString(), PackageTreeNode.NodeType.PACKAGE, null);
    }

    return constructMethodRefTreeForDex(dexFile);
  }

  @NotNull
  static PackageTreeNode constructMethodRefTreeForDex(@NotNull DexBackedDexFile dexFile) {
    PackageTreeNode root = new PackageTreeNode("", "root", PackageTreeNode.NodeType.PACKAGE, null);

    Set<String> classesWithDefinition = dexFile.getClasses()
      .stream()
      .map(DexBackedClassDef::getType)
      .collect(Collectors.toSet());

    Multimap<String, MethodReference> methodsByClassName = getMethodsByClassName(dexFile);
    for (String className : methodsByClassName.keySet()) {
      Collection<MethodReference> methods = methodsByClassName.get(className);
      for (MethodReference ref : methods) {
        root.insert("", DebuggerUtilsEx.signatureToName(className), ref, classesWithDefinition.contains(className));
      }
    }

    root.sortByCount();
    return root;
  }

  @NotNull
  private static Multimap<String, MethodReference> getMethodsByClassName(@NotNull DexBackedDexFile dexFile) {
    Multimap<String, MethodReference> methodsByClass = ArrayListMultimap.create();
    for (int i = 0, m = dexFile.getMethodCount(); i < m; i++) {
      MethodReference methodRef = new DexBackedMethodReference(dexFile, i);
      methodsByClass.put(methodRef.getDefiningClass(), methodRef);
    }

    return methodsByClass;
  }

  @NotNull
  private DexFileStats getDexStats() {
    DexBackedDexFile dexFile;
    try {
      dexFile = myDexFileFuture.get();
    }
    catch (Exception e) {
      return new DexFileStats(-1, -1, -1);
    }

    int definedMethodCount = 0;
    Set<? extends DexBackedClassDef> classes = dexFile.getClasses();
    for (DexBackedClassDef dexBackedClassDef : classes) {
      definedMethodCount += Iterables.size(dexBackedClassDef.getMethods());
    }

    return new DexFileStats(classes.size(), definedMethodCount, dexFile.getMethodCount());
  }

  @NotNull
  private static DexBackedDexFile getDexFile(@NotNull VirtualFile file) throws IOException {
    byte[] contents = file.contentsToByteArray();
    return getDexFile(contents);
  }

  @NotNull
  static DexBackedDexFile getDexFile(@NotNull byte[] contents) {
    return new DexBackedDexFile(new Opcodes(15), contents);
  }

  public static class DexFileStats {
    public final int classCount;
    public final int definedMethodCount;
    public final int referencedMethodCount;

    private DexFileStats(int classCount, int definedMethodCount, int referencedMethodCount) {
      this.classCount = classCount;
      this.definedMethodCount = definedMethodCount;
      this.referencedMethodCount = referencedMethodCount;
    }
  }
}