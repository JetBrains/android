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
import com.android.annotations.NonNull;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.xml.AndroidManifestParser;
import com.android.ide.common.xml.ManifestData;
import com.android.tools.apk.analyzer.*;
import com.android.tools.apk.analyzer.internal.AppBundleArchive;
import com.android.tools.idea.log.LogWrapper;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ApkParser {
  private static final ListeningExecutorService ourExecutorService = MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);

  private final ArchiveContext myArchiveContext;
  private final ApkSizeCalculator myApkSizeCalculator;

  @Nullable private ListenableFuture<ArchiveNode> myTreeStructure;
  @Nullable private ListenableFuture<ArchiveNode> myTreeStructureWithDownloadSizes;
  @Nullable private ListenableFuture<Long> myRawFullApkSize;
  @Nullable private ListenableFuture<Long> myCompressedFullApkSize;

  public ApkParser(@NotNull ArchiveContext archiveContext, @NotNull ApkSizeCalculator sizeCalculator) {
    myArchiveContext = archiveContext;
    myApkSizeCalculator = sizeCalculator;
  }

  @NotNull
  public Archive getArchive() {
    return myArchiveContext.getArchive();
  }

  public synchronized void cancelAll(){
    ListenableFuture[] futures = {
      myTreeStructureWithDownloadSizes,
      myTreeStructure,
      myRawFullApkSize,
      myCompressedFullApkSize
    };
    for (ListenableFuture future : futures) {
      if (future != null) {
        future.cancel(true);
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
      myTreeStructureWithDownloadSizes = Futures.transform(constructTreeStructure(), input -> {
        ArchiveTreeStructure.updateDownloadFileSizes(input, myApkSizeCalculator);
        return input;
      }, PooledThreadExecutor.INSTANCE);
    }
    return myTreeStructureWithDownloadSizes;
  }

  @NotNull
  public synchronized ListenableFuture<AndroidApplicationInfo> getApplicationInfo(@NotNull Path pathToAapt, @Nullable ArchiveEntry entry) {
    return ourExecutorService.submit(() -> getAppInfo(pathToAapt, entry));
  }

  @NotNull
  public synchronized ListenableFuture<Long> getUncompressedApkSize() {
    if (myRawFullApkSize == null) {
      myRawFullApkSize = ourExecutorService.submit(() -> myApkSizeCalculator.getFullApkRawSize(myArchiveContext.getArchive().getPath()));
    }
    return myRawFullApkSize;
  }

  @NotNull
  public synchronized ListenableFuture<Long> getCompressedFullApkSize() {
    if (myCompressedFullApkSize == null) {
      myCompressedFullApkSize = ourExecutorService.submit(() -> myApkSizeCalculator.getFullApkDownloadSize(myArchiveContext.getArchive().getPath()));
    }

    return myCompressedFullApkSize;
  }

  @NotNull
  private ArchiveNode createTreeNode() throws IOException {
    ArchiveNode node = ArchiveTreeStructure.create(myArchiveContext);
    ArchiveTreeStructure.updateRawFileSizes(node, myApkSizeCalculator);
    return node;
  }

  @NotNull
  public static AndroidApplicationInfo getAppInfo(@NonNull Path pathToAapt, @Nullable Archive archive) {
    if (archive == null){
      return AndroidApplicationInfo.UNKNOWN;
    }
    Path path = archive.getContentRoot().resolve(SdkConstants.FN_ANDROID_MANIFEST_XML);
    return getAppInfo(pathToAapt, new ArchivePathEntry(archive, path, ""));
  }

  @NotNull
  public static AndroidApplicationInfo getAppInfo(@NonNull Path pathToAapt, @Nullable ArchiveEntry archiveEntry) {
    if (archiveEntry == null){
      return AndroidApplicationInfo.UNKNOWN;
    }
    try {
      if (archiveEntry.getArchive() instanceof AppBundleArchive) {
        return getAppInfoFromAppBundle(archiveEntry);
      }
      else {
        return getAppInfoFromApk(pathToAapt, archiveEntry);
      }
    }
    catch (Throwable e) {
      Logger.getInstance(ApkViewPanel.class).warn("Unable to retrieve application info from artifact", e);
      return AndroidApplicationInfo.UNKNOWN;
    }
  }

  private static AndroidApplicationInfo getAppInfoFromAppBundle(@NotNull ArchiveEntry entry) throws Exception {
    // Note: This code below is a little convoluted, it would be better to update AndroidManifestParser
    //       to support ProtoXmlPullParser, which can decode xml protobuf streams automatically.

    // Decode XML protobuf into XML document string representation
    byte[] content = Files.readAllBytes(entry.getPath());
    String doc = new ProtoXmlPrettyPrinterImpl().prettyPrint(content);

    // Parse the XML string into manifest data and extract
    byte[] decodedContent = doc.getBytes(StandardCharsets.UTF_8);
    try (ByteArrayInputStream stream = new ByteArrayInputStream(decodedContent)) {
      ManifestData data = AndroidManifestParser.parse(stream);
      return new AndroidApplicationInfo(data.getPackage(), data.getVersionName(), data.getVersionCode());
    }
  }

  @NotNull
  private static AndroidApplicationInfo getAppInfoFromApk(@NonNull Path pathToAapt, @NotNull ArchiveEntry archiveEntry)
    throws ProcessException {
    AaptInvoker invoker = new AaptInvoker(pathToAapt, new LogWrapper(ApkParser.class));

    File archiveFile = archiveEntry.getArchive().getPath().toFile();
    // Note: aapt paths don't start with a "/"
    String entryPath = archiveEntry.getPath().toString();
    if (entryPath.startsWith("/")) {
      entryPath = entryPath.substring(1);
    }
    List<String> xmlTree = invoker.getXmlTree(archiveFile, entryPath);
    return AndroidApplicationInfo.parse(xmlTree);
  }
}
