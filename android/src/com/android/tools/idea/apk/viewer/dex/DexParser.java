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
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.reference.DexBackedMethodReference;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Future;

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

    Multimap<String, MethodReference> methodsByClassName = getMethodsByClassName(dexFile);
    for (String className : methodsByClassName.keySet()) {
      Collection<MethodReference> methods = methodsByClassName.get(className);
      for (MethodReference ref : methods) {
        root.insert("", DebuggerUtilsEx.signatureToName(className), ref);
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
      return new DexFileStats(-1);
    }

    return new DexFileStats(dexFile.getMethodCount());
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
    public final int referencedMethodCount;

    private DexFileStats(int referencedMethodCount) {
      this.referencedMethodCount = referencedMethodCount;
    }
  }
}