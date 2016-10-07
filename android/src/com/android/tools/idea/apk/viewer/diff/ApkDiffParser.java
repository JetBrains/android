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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.apk.viewer.ApkFileSystem;
import com.android.tools.idea.apk.viewer.ApkParser;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.tree.DefaultMutableTreeNode;

public class ApkDiffParser {
  private static final ListeningExecutorService ourExecutorService = MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);

  private final VirtualFile myApkRootOld;
  private final VirtualFile myApkRootNew;

  private ListenableFuture<DefaultMutableTreeNode> myTreeStructure;

  public ApkDiffParser(@NotNull VirtualFile apkRootOld, @NotNull VirtualFile apkRootNew) {
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
      myTreeStructure = ourExecutorService.submit(() -> createTreeNode(myApkRootOld, myApkRootNew));
    }

    return myTreeStructure;
  }

  @VisibleForTesting
  @NotNull
  static DefaultMutableTreeNode createTreeNode(@Nullable VirtualFile oldFile, @Nullable VirtualFile newFile) {
    if(oldFile == null && newFile == null) {
      throw new IllegalArgumentException("Both old and new files are null");
    }

    DefaultMutableTreeNode node = new DefaultMutableTreeNode();

    long oldSize = 0;
    long newSize = 0;

    HashSet<String> childrenInOldFile = new HashSet<>();
    final String name = oldFile == null ? newFile.getName() : oldFile.getName();
    if (oldFile != null) {
      if (StringUtil.equals(oldFile.getExtension(), SdkConstants.EXT_ZIP)) {
        VirtualFile zipRoot = ApkFileSystem.getInstance().extractAndGetContentRoot(oldFile);
        if (zipRoot != null) {
          oldFile = zipRoot;
        }
      }

      if (oldFile.isDirectory()) {
        //noinspection UnsafeVfsRecursion (no symlinks inside an APK)
        for (VirtualFile oldChild : oldFile.getChildren()) {
          VirtualFile newChild = newFile == null ? null : newFile.findChild(oldChild.getName());
          childrenInOldFile.add(oldChild.getName());
          DefaultMutableTreeNode childNode = createTreeNode(oldChild, newChild);
          node.add(childNode);

          ApkDiffEntry entry = (ApkDiffEntry)childNode.getUserObject();
          oldSize += entry.getOldSize();
          newSize += entry.getNewSize();
        }

        if (oldFile.getLength() > 0) {
          // This is probably a zip inside the apk, and we should use it's size
          oldSize = oldFile.getLength();
        }
      }
      else {
        oldSize += oldFile.getLength();
      }
    }
    if (newFile != null) {
      if (StringUtil.equals(newFile.getExtension(), SdkConstants.EXT_ZIP)) {
        VirtualFile zipRoot = ApkFileSystem.getInstance().extractAndGetContentRoot(newFile);
        if (zipRoot != null) {
          newFile = zipRoot;
        }
      }

      if (newFile.isDirectory()) {
        //noinspection UnsafeVfsRecursion (no symlinks inside an APK)
        for (VirtualFile newChild : newFile.getChildren()) {
          if(childrenInOldFile.contains(newChild.getName())) {
            continue;
          }

          DefaultMutableTreeNode childNode = createTreeNode(null, newChild);
          node.add(childNode);

          ApkDiffEntry entry = (ApkDiffEntry)childNode.getUserObject();
          oldSize += entry.getOldSize();
          newSize += entry.getNewSize();
        }

        if (newFile.getLength() > 0) {
          // This is probably a zip inside the apk, and we should use it's size
          newSize = newFile.getLength();
        }
      }
      else {
        newSize += newFile.getLength();
      }
    }

    node.setUserObject(new ApkDiffEntry(name, oldFile, newFile, oldSize, newSize));

    ApkParser.sort(node);

    return node;
  }

}
