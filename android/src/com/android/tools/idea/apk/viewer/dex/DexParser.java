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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;

import java.util.concurrent.Future;

import static com.android.tools.idea.apk.dex.DexFiles.getDexFile;

public class DexParser {
  private final ListeningExecutorService myExecutor;
  private final Future<DexBackedDexFile> myDexFileFuture;

  public DexParser(@NotNull ListeningExecutorService executorService,
                   @NotNull VirtualFile file) {
    myExecutor = executorService;
    myDexFileFuture = myExecutor.submit(() -> getDexFile(file));
  }

  public ListenableFuture<PackageTreeNode> constructMethodRefCountTree() {
    return myExecutor.submit(this::constructMethodRefTree);
  }

  public ListenableFuture<DexFileStats> getDexFileStats() {
    return myExecutor.submit(() -> DexFileStats.create(myDexFileFuture.get()));
  }

  @NotNull
  private PackageTreeNode constructMethodRefTree() {
    DexBackedDexFile dexFile;
    try {
      dexFile = myDexFileFuture.get();
    }
    catch (Exception e) {
      return new PackageTreeNode(e.toString(), PackageTreeNode.NodeType.PACKAGE, null);
    }
    PackageTreeCreator treeCreator = new PackageTreeCreator();
    return treeCreator.constructPackageTree(dexFile);
  }





}