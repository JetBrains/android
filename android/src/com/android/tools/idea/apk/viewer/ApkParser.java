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
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.*;

public class ApkParser {
  private static final ListeningExecutorService ourExecutorService = MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);

  private final VirtualFile myApkRoot;
  private final File myApk;

  private ListenableFuture<DefaultMutableTreeNode> myTreeStructure;
  private ListenableFuture<DefaultMutableTreeNode> myTreeStructureWithCompressedSizes;

  private ListenableFuture<AndroidApplicationInfo> myApplicationInfo;
  private ListenableFuture<Long> myCompressedFullApkSize;

  public ApkParser(@NotNull VirtualFile baseFile, @NotNull VirtualFile apkRoot) {
    if (!apkRoot.getFileSystem().equals(ApkFileSystem.getInstance())) {
      throw new IllegalArgumentException("Invalid APK");
    }

    myApkRoot = apkRoot;
    myApk = VfsUtilCore.virtualToIoFile(baseFile);
  }

  @NotNull
  public synchronized ListenableFuture<DefaultMutableTreeNode> constructTreeStructure() {
    if (myTreeStructure == null) {
      myTreeStructure = ourExecutorService.submit(() -> createTreeNode(myApkRoot));
    }

    return myTreeStructure;
  }

  @NotNull
  public synchronized ListenableFuture<DefaultMutableTreeNode> constructTreeStructureWithCompressedSizes() {
    if (myTreeStructureWithCompressedSizes == null) {
      myTreeStructureWithCompressedSizes = ourExecutorService.submit(() -> {
        // first obtain the compressed apk
        File compressedApk = getZipCompressedApk(myApk);

        // then update the existing tree structure with info about size of each file in the apk when it is compressed
        try (ZipFile zip = new ZipFile(compressedApk)) {
          return updateTreeStructure(constructTreeStructure().get(), zip);
        }
      });
    }

    return myTreeStructureWithCompressedSizes;
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
    return Futures.transform(constructTreeStructure(), (Function<DefaultMutableTreeNode, Long>)input -> {
      ApkEntry entry = ApkEntry.fromNode(input);
      assert entry != null;

      return entry.size;
    });
  }

  @NotNull
  public synchronized ListenableFuture<Long> getCompressedFullApkSize() {
    if (myCompressedFullApkSize == null) {
      myCompressedFullApkSize = ourExecutorService.submit(() -> getApkServedByPlay(myApk).length());
    }

    return myCompressedFullApkSize;
  }

  @VisibleForTesting
  @NotNull
  static DefaultMutableTreeNode createTreeNode(@NotNull VirtualFile file) {
    String originalName = null;
    DefaultMutableTreeNode node = new DefaultMutableTreeNode();

    long size = 0;

    if (StringUtil.equals(file.getExtension(), SdkConstants.EXT_ZIP)) {
      VirtualFile zipRoot = ApkFileSystem.getInstance().extractAndGetContentRoot(file);
      if (zipRoot != null) {
        originalName = file.getName();
        file = zipRoot;
      }
    }

    if (file.isDirectory()) {
      //noinspection UnsafeVfsRecursion (no symlinks inside an APK)
      for (VirtualFile child : file.getChildren()) {
        DefaultMutableTreeNode childNode = createTreeNode(child);
        node.add(childNode);

        size += ((ApkEntry)childNode.getUserObject()).size;
      }
    }
    else {
      size = file.getLength();
    }

    node.setUserObject(new ApkEntry(file, originalName, size));

    sort(node);

    return node;
  }

  /**
   * Updates and returns the given tree structure with info about the compressed size of each node.
   */
  @NotNull
  private static DefaultMutableTreeNode updateTreeStructure(@NotNull DefaultMutableTreeNode treeNode, @NotNull ZipFile compressedApk) {
    long compressedSize = 0;

    ApkEntry entry = ApkEntry.fromNode(treeNode);
    assert entry != null;

    if (treeNode.getChildCount() > 0) {
      for (int i = 0; i < treeNode.getChildCount(); i++) {
        DefaultMutableTreeNode childNode = updateTreeStructure((DefaultMutableTreeNode)treeNode.getChildAt(i), compressedApk);
        compressedSize += ((ApkEntry)childNode.getUserObject()).getCompressedSize();
      }
    }
    else {
      ZipEntry ze = compressedApk.getEntry(ApkFileSystem.getInstance().getRelativePath(entry.file));
      compressedSize = ze.getCompressedSize();
    }

    entry.setCompressedSize(compressedSize);
    return treeNode;
  }

  private static void sort(@NotNull DefaultMutableTreeNode node) {
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
      return Long.compare(entry2.size, entry1.size);
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

      ProcessOutput xmlTree = invoker.getXmlTree(myApk, SdkConstants.FN_ANDROID_MANIFEST_XML);
      return AndroidApplicationInfo.fromXmlTree(xmlTree);
    }
    catch (ExecutionException e) {
      Logger.getInstance(ApkViewPanel.class).warn("Unable to run aapt", e);
      return AndroidApplicationInfo.UNKNOWN;
    }
  }

  /**
   * @return the input file compressed using "gzip -9" and saved in a temporary location
   */
  private static File getApkServedByPlay(@NotNull File apk) {
    Path tempFile;
    try {
      tempFile =
        Files.createTempFile("compressed", SdkConstants.DOT_ANDROID_PACKAGE,
                             PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    }
    catch (IOException e) {
      Logger.getInstance(ApkParser.class).warn(e);
      return apk;
    }

    // There is a difference between uncompressing the apk, and then compressing again using gzip -9, versus just compressing the apk
    // itself using gzip -9. But the difference seems to be negligible, and we are only aiming at an estimate of what Play provides, so
    // this should suffice. This also seems to be the same approach taken by https://github.com/googlesamples/apk-patch-size-estimator

    File compressedFile = tempFile.toFile();
    compressedFile.deleteOnExit();

    try (GZIPOutputStream zos = new MaxGzipOutputStream(new FileOutputStream(compressedFile))) {
      Files.copy(apk.toPath(), zos);
      zos.flush();
    }
    catch (IOException e) {
      Logger.getInstance(ApkParser.class).warn(e);
      return apk;
    }

    return compressedFile;
  }

  /**
   * Provides a zip archive that is compressed at level 9, but still maintains archive information. This implies that it will be slightly
   * larger than compressing using gzip (which only compresses a single file, not an archive). But having compression information per file
   * is sometimes useful to get an approximate idea of how well each file compresses.
   *
   * @return the input file compressed using "zip -9" and saved in a temporary location.
   */
  static File getZipCompressedApk(@NotNull File apk) {
    Path tempFile;
    try {
      tempFile =
        Files.createTempFile(FileUtil.getNameWithoutExtension(apk), SdkConstants.DOT_ANDROID_PACKAGE,
                             PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    }
    catch (IOException e) {
      return apk;
    }

    File compressedFile = tempFile.toFile();
    compressedFile.deleteOnExit();

    // copy entire contents of one zip file to another, where the destination zip is written to with the maximum compression level
    try (
      ZipInputStream zis = new ZipInputStream(new FileInputStream(apk));
      ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(compressedFile))) {

      ZipEntry ze;
      while ((ze = zis.getNextEntry()) != null) {
        ZipEntry compressedZe = new ZipEntry(ze.getName());
        compressedZe.setMethod(ZipEntry.DEFLATED);
        compressedZe.setTime(ze.getTime());
        zos.putNextEntry(compressedZe);

        byte[] buf = new byte[4096];
        int n;
        while ((n = zis.read(buf)) > 0) {
          zos.write(buf, 0, n);
        }
      }
    }
    catch (IOException e) {
      return apk;
    }

    return compressedFile;
  }

  private static final class MaxGzipOutputStream extends GZIPOutputStream {
    public MaxGzipOutputStream(OutputStream out) throws IOException {
      super(out);
      def.setLevel(Deflater.BEST_COMPRESSION); // Currently, Google Play serves an APK that is compressed using gzip -9
    }
  }

  private static final class MaxZipOutputStream extends ZipOutputStream {
    public MaxZipOutputStream(OutputStream out) throws IOException {
      super(out);
      def.setLevel(Deflater.BEST_COMPRESSION);
    }
  }
}
