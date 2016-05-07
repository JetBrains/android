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
import com.android.tools.idea.apk.AndroidApkFileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class ApkFileSystem extends ArchiveFileSystem {
  public static final String PROTOCOL = "apk";
  public static final String APK_SEPARATOR = URLUtil.JAR_SEPARATOR;

  public static ApkFileSystem getInstance() {
    return (ApkFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  @NotNull
  @Override
  public String getProtocol() {
    return PROTOCOL;
  }

  @NotNull
  @Override
  protected String extractLocalPath(@NotNull String rootPath) {
    return StringUtil.trimEnd(rootPath, APK_SEPARATOR);
  }

  @NotNull
  @Override
  protected String composeRootPath(@NotNull String localPath) {
    return localPath + APK_SEPARATOR;
  }

  @NotNull
  @Override
  protected ArchiveHandler getHandler(@NotNull VirtualFile entryFile) {
    return VfsImplUtil.getHandler(this, entryFile, ZipHandler::new);
  }

  /**
   * Returns the relative path of the file within the APK file system.
   * Same implementation as super, but public access.
   */
  @Override
  @NotNull
  public String getRelativePath(@NotNull VirtualFile file) {
    String path = file.getPath();
    String relativePath = path.substring(extractRootPath(path).length());
    return StringUtil.startsWithChar(relativePath, '/') ? relativePath.substring(1) : relativePath;
  }

  @NotNull
  @Override
  protected String extractRootPath(@NotNull String path) {
    final int apkSeparatorIndex = path.indexOf(APK_SEPARATOR);
    assert apkSeparatorIndex >= 0 : "Path passed to ApkFileSystem must have apk separator '!/': " + path;
    return path.substring(0, apkSeparatorIndex + APK_SEPARATOR.length());
  }

  @Nullable
  @Override
  public VirtualFile findFileByPath(@NotNull @NonNls String path) {
    return VfsImplUtil.findFileByPath(this, path);
  }

  @Nullable
  @Override
  public VirtualFile findFileByPathIfCached(@NotNull @NonNls String path) {
    return VfsImplUtil.findFileByPathIfCached(this, path);
  }

  @Nullable
  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return VfsImplUtil.refreshAndFindFileByPath(this, path);
  }

  @Override
  public void refresh(boolean asynchronous) {
    VfsImplUtil.refresh(this, asynchronous);
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    byte[] bytes = super.contentsToByteArray(file);
    if (!isBinaryXml(file)) {
      return bytes;
    }

    return BinaryXmlParser.decodeXml(file.getName(), bytes);
  }

  @Override
  protected boolean isCorrectFileType(@NotNull VirtualFile local) {
    return FileTypeRegistry.getInstance().getFileTypeByFileName(local.getName()) == AndroidApkFileType.INSTANCE;
  }

  /**
   * @return Whether the given file is a binary XML file within the APK.
   */
  public boolean isBinaryXml(@NotNull VirtualFile file) {
    return isBinaryXml(getRelativePath(file));
  }

  public static boolean isBinaryXml(@NotNull String relativePath) {
    return relativePath.equals(SdkConstants.FN_ANDROID_MANIFEST_XML) ||
           relativePath.startsWith(SdkConstants.FD_RES) && relativePath.endsWith(SdkConstants.DOT_XML);
  }

  public boolean isArsc(@NotNull VirtualFile file) {
    return isArsc(getRelativePath(file));
  }

  public static boolean isArsc(@NotNull String relativePath) {
    return "resources.arsc".equals(relativePath);
  }
}
