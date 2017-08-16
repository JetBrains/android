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
import com.android.ide.common.process.ProcessException;
import com.android.tools.apk.analyzer.*;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.log.LogWrapper;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.IOException;
import java.util.List;

public class ApkParser {
  private static final ListeningExecutorService ourExecutorService = MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);

  private final Archive myArchive;
  private final ApkSizeCalculator myApkSizeCalculator;

  @Nullable private ListenableFuture<ArchiveNode> myTreeStructure;
  @Nullable private ListenableFuture<ArchiveNode> myTreeStructureWithDownloadSizes;
  @Nullable private ListenableFuture<Long> myRawFullApkSize;
  @Nullable private ListenableFuture<Long> myCompressedFullApkSize;

  public ApkParser(@NotNull Archive archive, @NotNull ApkSizeCalculator sizeCalculator) {
    myArchive = archive;
    myApkSizeCalculator = sizeCalculator;
  }

  public synchronized void cancelAll(){
    ListenableFuture[] futures = new ListenableFuture[]{
      myTreeStructureWithDownloadSizes,
      myTreeStructure,
      myRawFullApkSize,
      myCompressedFullApkSize
    };
    for (int i = 0; i < futures.length; i++) {
      if (futures[i] != null){
        futures[i].cancel(true);
      }
    }
  }

  @NotNull
  public synchronized ListenableFuture<ArchiveNode> constructTreeStructure() {
    if (myTreeStructure == null) {
      myTreeStructure = ourExecutorService.submit(this::createTreeNode);
    }

    return myTreeStructure;
  }

  @NotNull
  public synchronized ListenableFuture<ArchiveNode> updateTreeWithDownloadSizes() {
    if (myTreeStructureWithDownloadSizes == null) {
      myTreeStructureWithDownloadSizes = Futures.transform(constructTreeStructure(), (Function<ArchiveNode, ArchiveNode>)input -> {
        assert input != null;
        ArchiveTreeStructure.updateDownloadFileSizes(input, myApkSizeCalculator);
        return input;
      }, PooledThreadExecutor.INSTANCE);
    }
    return myTreeStructureWithDownloadSizes;
  }

  @NotNull
  public synchronized ListenableFuture<AndroidApplicationInfo> getApplicationInfo(@Nullable Archive archive) {
    return ourExecutorService.submit(() -> getAppInfo(archive));
  }

  @NotNull
  public synchronized ListenableFuture<Long> getUncompressedApkSize() {
    if (myRawFullApkSize == null) {
      myRawFullApkSize = ourExecutorService.submit(() -> myApkSizeCalculator.getFullApkRawSize(myArchive.getPath()));
    }
    return myRawFullApkSize;
  }

  @NotNull
  public synchronized ListenableFuture<Long> getCompressedFullApkSize() {
    if (myCompressedFullApkSize == null) {
      myCompressedFullApkSize = ourExecutorService.submit(() -> myApkSizeCalculator.getFullApkDownloadSize(myArchive.getPath()));
    }

    return myCompressedFullApkSize;
  }

  @NotNull
  private ArchiveNode createTreeNode() throws IOException {
    ArchiveNode node = ArchiveTreeStructure.create(myArchive);
    ArchiveTreeStructure.updateRawFileSizes(node, myApkSizeCalculator);
    return node;
  }

  @NotNull
  public static AndroidApplicationInfo getAppInfo(@Nullable Archive archive) {
    if (archive == null){
      return AndroidApplicationInfo.UNKNOWN;
    }
    try {
      AaptInvoker invoker = new AaptInvoker(AndroidSdks.getInstance().tryToChooseSdkHandler(), new LogWrapper(ApkParser.class));

      List<String> xmlTree = invoker.getXmlTree(archive.getPath().toFile(), SdkConstants.FN_ANDROID_MANIFEST_XML);
      return AndroidApplicationInfo.parse(xmlTree);
    }
    catch (ProcessException e) {
      Logger.getInstance(ApkViewPanel.class).warn("Unable to run aapt", e);
      return AndroidApplicationInfo.UNKNOWN;
    }
  }
}
