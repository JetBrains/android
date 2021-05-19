/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Properties;

public final class PropertiesFiles {
  private PropertiesFiles() {
  }

  @NotNull
  public static Properties getProperties(@NotNull File filePath) throws IOException {
    return ReadAction.compute(() -> {
      VirtualFile virtualFile = VfsUtil.findFileByIoFile(filePath, false);
      if (virtualFile == null) {
        return new Properties();
      }
      if (virtualFile.isDirectory()) {
        throw new IllegalArgumentException(String.format("The path '%1$s' belongs to a directory!", filePath.getPath()));
      }
      Properties properties = new Properties();
      try (Reader reader = new InputStreamReader(virtualFile.getInputStream(), StandardCharsets.UTF_8)) {
        properties.load(reader);
      }
      return properties;
    });
  }

  public static void savePropertiesToFile(@NotNull Properties properties, @NotNull File filePath, @Nullable String comments)
    throws IOException {
    WriteAction.computeAndWait(
      () -> {
        VirtualFile directory = VfsUtil.createDirectoryIfMissing(filePath.getParent());
        if (directory == null) {
          throw new IllegalStateException("Cannot find or create a VFS file for directory: " + filePath.getParent());
        }
        VirtualFile virtualFile = directory.findOrCreateChildData(PropertiesFiles.class, filePath.getName());
        try (OutputStream out = virtualFile.getOutputStream(PropertiesFiles.class)) {
          // Note that we don't write the properties files in UTF-8; this will *not* write the
          // files with the default platform encoding; instead, it will write it using ISO-8859-1 and
          // \\u escaping syntax for other characters. This will work with older versions of the Gradle
          // plugin which does not read the .properties file with UTF-8 encoding. In the future when
          // nobody is using older (0.7.x) versions of the Gradle plugin anymore we can upgrade this
          properties.store(out, comments);
        }
        return null;
      }
    );
  }
}
