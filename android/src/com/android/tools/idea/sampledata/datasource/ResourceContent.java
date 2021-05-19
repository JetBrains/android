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

import com.android.ide.common.util.PathString;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.util.StudioPathManager;
import com.google.common.base.Charsets;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.function.Function;

public class ResourceContent implements Function<OutputStream, Exception> {
  private static final Logger LOG = Logger.getInstance(ResourceContent.class);


  byte[] myContent;

  private ResourceContent(@NotNull byte[] content) {
    myContent = content;
  }

  /**
   * Returns the base directory for the Sample Data directory contents
   */
  @Nullable
  public static File getSampleDataBaseDir() {
    String homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath());
    final String[] paths = {
      FileUtil.join(homePath, "plugins/android/lib/sampleData"), // Bundled path
      StudioPathManager.isRunningFromSources()
      ? FileUtil.join(StudioPathManager.getSourcesRoot(), "tools/adt/idea/android/lib/sampleData")
      : null, // Development path
      FileUtil.join(homePath, "/community/android/android/lib/sampleData") // IDEA plugin Development path
    };

    StringBuilder notFoundPaths = new StringBuilder();
    for (String jarPath : paths) {
      if (jarPath == null) continue;

      File rootFile = new File(jarPath);
      if (rootFile.exists()) {
        LOG.debug("Sample data base dir found at " + jarPath);
        return rootFile;
      }
      else {
        notFoundPaths.append(jarPath).append('\n');
      }
    }

    LOG.warn("Unable to sampleData in paths:\n" + notFoundPaths);
    return null;
  }

  /**
   * Returns the Sample Data directory created by the user in the provided facet's module.
   */
  @Nullable
  public static File getSampleDataUserDir(AndroidFacet facet) {
    PathString sampleDataDirectory = ProjectSystemUtil.getModuleSystem(facet.getModule()).getSampleDataDirectory();
    return sampleDataDirectory != null ? sampleDataDirectory.toFile() : null;
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
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int count;
      while ((count = stream.read(buffer)) != -1) {
        bytes.write(buffer, 0, count);
      }
      content = bytes.toByteArray();
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
