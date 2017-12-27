/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.sampledata.datasource;

import com.google.common.base.Charsets;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import libcore.io.Streams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.function.Function;

public class ResourceContent implements Function<OutputStream, Exception> {
  private static final Logger LOG = Logger.getInstance(ResourceContent.class);
  private static final String[] LIB_CUSTOM_PATHS = {
    // Bundled path
    "/plugins/android/lib/sampleData",
    // Development path
    "/../adt/idea/android/lib/sampleData",
    // IDEA plugin Development path
    "/community/android/android/lib/sampleData"
  };

  byte[] myContent;

  private ResourceContent(@NotNull byte[] content) {
    myContent = content;
  }

  /**
   * Returns the base directory for the Sample Data directory contents
   */
  @Nullable
  private static File getSampleDataBaseDir() {
    String homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath());

    StringBuilder notFoundPaths = new StringBuilder();
    for (String path : LIB_CUSTOM_PATHS) {
      String jarPath = homePath + path;
      VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(jarPath));

      if (rootDir != null) {
        File rootFile = VfsUtilCore.virtualToIoFile(rootDir);
        if (rootFile.exists()) {
          LOG.debug("Sample data base dir found at " + jarPath);
          return rootFile;
        }
      }
      else {
        notFoundPaths.append(jarPath).append('\n');
      }
    }

    LOG.warn("Unable to sampleData in paths:\n" + notFoundPaths.toString());
    return null;
  }

  /**
   * Because directories can list multiple files that we have to provide direct access to, they can not be contained
   * within the JAR file. We always assume these directory is within the lib path.
   */
  @NotNull
  public static ResourceContent fromDirectory(@NotNull String relativePath) {
    File baseDir = getSampleDataBaseDir();
    File sampleDataPath = baseDir != null ? new File(baseDir, relativePath) : null;
    File[] files = sampleDataPath != null && sampleDataPath.isDirectory() ? sampleDataPath.listFiles() : null;

    StringBuilder content = new StringBuilder();

    if (files != null) {
      for (File file : files) {
        content.append(file.getAbsolutePath()).append('\n');
      }
    }

    return new ResourceContent(content.toString().getBytes(Charsets.UTF_8));
  }

  @NotNull
  public static ResourceContent fromInputStream(@NotNull InputStream stream) {
    byte[] content;
    try {
      content = Streams.readFully(stream);
    }
    catch (IOException e) {
      content = new byte[0];
    }
    return new ResourceContent(content);
  }

  @Override
  public Exception apply(OutputStream stream) {
    try {
      stream.write(myContent);
    }
    catch (IOException e) {
      return e;
    }

    return null;
  }
}
