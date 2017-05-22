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
package com.android.tools.idea.apk.viewer.diff;

import com.android.tools.apk.analyzer.Archive;
import com.android.tools.apk.analyzer.Archives;
import com.android.tools.apk.analyzer.internal.ApkDiffParser;
import com.android.tools.idea.apk.viewer.ApkFileSystem;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.tree.DefaultMutableTreeNode;

public class ApkDiff {
  private static final ListeningExecutorService ourExecutorService = MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);

  private final VirtualFile myApkRootOld;
  private final VirtualFile myApkRootNew;

  private ListenableFuture<DefaultMutableTreeNode> myTreeStructure;

  public ApkDiff(@NotNull VirtualFile apkRootOld, @NotNull VirtualFile apkRootNew) {
    if (!apkRootOld.getFileSystem().equals(ApkFileSystem.getInstance())) {
      throw new IllegalArgumentException("Invalid Old APK");
    }

    if (!apkRootNew.getFileSystem().equals(ApkFileSystem.getInstance())) {
      throw new IllegalArgumentException("Invalid New APK");
    }

    myApkRootOld = apkRootOld;
    myApkRootNew = apkRootNew;
  }

  @NotNull
  public synchronized ListenableFuture<DefaultMutableTreeNode> constructTreeStructure() {
    if (myTreeStructure == null) {
      myTreeStructure = ourExecutorService.submit(() -> {
        try (Archive archive1 = Archives.open(VfsUtilCore.virtualToIoFile(myApkRootOld).toPath());
        Archive archive2 = Archives.open(VfsUtilCore.virtualToIoFile(myApkRootNew).toPath())) {
          return ApkDiffParser.createTreeNode(archive1, archive2);
        }
      });
    }

    return myTreeStructure;
  }
}
