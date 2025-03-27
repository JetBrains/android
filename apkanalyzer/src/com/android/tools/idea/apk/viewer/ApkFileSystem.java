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
import com.android.tools.apk.analyzer.BinaryXmlParser;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Shorts;
import com.google.devrel.gmscore.tools.apk.arsc.Chunk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.openapi.vfs.impl.jar.TimedZipHandler;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.util.io.URLUtil;
import java.io.IOException;
import java.util.Set;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApkFileSystem extends ArchiveFileSystem {
  public static final Set<String> EXTENSIONS = ImmutableSet.of(
    SdkConstants.EXT_ANDROID_PACKAGE,
    SdkConstants.EXT_AAR,
    SdkConstants.EXT_INSTANTAPP_PACKAGE,
    SdkConstants.EXT_ZIP,
    SdkConstants.EXT_APP_BUNDLE
  );

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
    return VfsImplUtil.getHandler(this, entryFile, SystemInfo.isWindows ? TimedZipHandler::new : ZipHandler::new);
  }

  /**
   * Returns the relative path of the file within the APK file system.
   * Same implementation as super, but public access.
   */
  @Override
  @NotNull
  public String getRelativePath(@NotNull VirtualFile file) {
    return super.getRelativePath(file);
  }

  @NotNull
  @Override
  protected String extractRootPath(@NotNull String normalizedPath) {
    int apkSeparatorIndex = normalizedPath.indexOf(APK_SEPARATOR);
    return apkSeparatorIndex > 0 ? normalizedPath.substring(0, apkSeparatorIndex + APK_SEPARATOR.length()) : "";
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

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    byte[] bytes = super.contentsToByteArray(file);
    if (!isBinaryXml(file, bytes)) {
      return bytes;
    }

    return BinaryXmlParser.decodeXml(bytes);
  }

  @Override
  protected boolean isCorrectFileType(@NotNull VirtualFile local) {
    return EXTENSIONS.contains(local.getExtension());
  }

  /**
   * @return Whether the given file is a binary XML file within the APK.
   */
  public boolean isBinaryXml(VirtualFile file, byte[] bytes) {
    return isBinaryXml(getRelativePath(file), bytes);
  }

  public static boolean isBinaryXml(String relativePath, byte[] bytes) {
    if (!relativePath.endsWith(SdkConstants.DOT_XML)) {
      return false;
    }

    boolean encodedXmlPath = relativePath.equals(SdkConstants.FN_ANDROID_MANIFEST_XML) ||
                             (relativePath.startsWith(SdkConstants.FD_RES) &&
                              !relativePath.startsWith(SdkConstants.FD_RES + "/" + SdkConstants.FD_RES_RAW));
    if (!encodedXmlPath) {
      return false;
    }

    short code = Shorts.fromBytes(bytes[1], bytes[0]);
    return code == Chunk.Type.XML.code();
  }
}
