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
package com.android.tools.idea.apk.viewer;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.tools.apk.analyzer.ApkSizeCalculator;
import com.android.tools.apk.analyzer.Archive;
import com.android.tools.apk.analyzer.ArchiveNode;
import com.android.tools.apk.analyzer.ArchiveTreeStructure;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.*;

public class ApkParser {
  private static final ListeningExecutorService ourExecutorService = MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);

  private final Archive myArchive;
  private final ApkSizeCalculator myApkSizeCalculator;

  private ListenableFuture<ArchiveNode> myTreeStructure;

  private ListenableFuture<AndroidApplicationInfo> myApplicationInfo;
  private ListenableFuture<Long> myCompressedFullApkSize;

  public ApkParser(@NotNull Archive archive, @NotNull ApkSizeCalculator sizeCalculator) {
    myArchive = archive;
    myApkSizeCalculator = sizeCalculator;
  }

  @NotNull
  public synchronized ListenableFuture<ArchiveNode> constructTreeStructure() {
    if (myTreeStructure == null) {
      myTreeStructure = ourExecutorService.submit(this::createTreeNode);
    }

    return myTreeStructure;
  }

  public ArchiveNode updateTreeWithDownloadSizes(@NotNull ArchiveNode root) {
    ArchiveTreeStructure.updateDownloadFileSizes(root, myApkSizeCalculator);
    return root;
  }

  @NotNull
  public synchronized ListenableFuture<AndroidApplicationInfo> getApplicationInfo() {
    if (myApplicationInfo == null) {
      myApplicationInfo = ourExecutorService.submit(this::getAppInfo);
    }

    return myApplicationInfo;
  }

  @NotNull
  public synchronized ListenableFuture<Long> getUncompressedApkSize() {
    return ourExecutorService.submit(myApkSizeCalculator::getFullApkRawSize);
  }

  @NotNull
  public synchronized ListenableFuture<Long> getCompressedFullApkSize() {
    if (myCompressedFullApkSize == null) {
      myCompressedFullApkSize = ourExecutorService.submit(myApkSizeCalculator::getFullApkDownloadSize);
    }

    return myCompressedFullApkSize;
  }

  @NotNull
  private ArchiveNode createTreeNode() throws IOException {
    ArchiveNode node = ArchiveTreeStructure.create(myArchive);
    ArchiveTreeStructure.updateRawFileSizes(node, myApkSizeCalculator);
    return node;
  }

  public static void sort(@NotNull DefaultMutableTreeNode node) {
    if (node.getChildCount() == 0) {
      return;
    }

    List<DefaultMutableTreeNode> children = new ArrayList<>();
    for (int i = 0; i < node.getChildCount(); i++) {
      children.add((DefaultMutableTreeNode)node.getChildAt(i));
    }

    Collections.sort(children, (o1, o2) -> {
      ApkEntry entry1 = ApkEntry.fromNode(o1);
      ApkEntry entry2 = ApkEntry.fromNode(o2);
      if (entry1 == null || entry2 == null) {
        return 0;
      }
      return Long.compare(entry2.getSize(), entry1.getSize());
    });

    node.removeAllChildren();
    for (DefaultMutableTreeNode child : children) {
      node.add(child);
    }
  }

  @NotNull
  private AndroidApplicationInfo getAppInfo() {
    try {
      AaptInvoker invoker = AaptInvoker.getInstance();
      if (invoker == null) {
        return AndroidApplicationInfo.UNKNOWN;
      }

      ProcessOutput xmlTree = invoker.getXmlTree(myArchive.getPath().toFile(), SdkConstants.FN_ANDROID_MANIFEST_XML);
      return AndroidApplicationInfo.fromXmlTree(xmlTree);
    }
    catch (ExecutionException e) {
      Logger.getInstance(ApkViewPanel.class).warn("Unable to run aapt", e);
      return AndroidApplicationInfo.UNKNOWN;
    }
  }
}
