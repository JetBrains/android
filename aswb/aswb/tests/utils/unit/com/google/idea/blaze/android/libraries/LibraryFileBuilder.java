/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.idea.blaze.android.libraries;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.NonNull;
import com.google.common.base.Joiner;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Util class to generate temp aar or jar file used during test */
public class LibraryFileBuilder {
  private static final Logger LOG = Logger.getInstance(LibraryFileBuilder.class);
  private final File file;
  private final Map<String, byte[]> resourceNameToContent;

  public static LibraryFileBuilder aar(
      @NonNull WorkspaceRoot workspaceRoot, @NonNull String aarFilePath) {
    return new LibraryFileBuilder(workspaceRoot, aarFilePath);
  }

  LibraryFileBuilder(@NonNull WorkspaceRoot workspaceRoot, @NonNull String filePath) {
    this(workspaceRoot.fileForPath(new WorkspacePath(filePath)));
  }

  public LibraryFileBuilder(@NonNull File file) {
    this.file = file;
    this.resourceNameToContent = new HashMap<>();
  }

  @CanIgnoreReturnValue
  public LibraryFileBuilder addContent(String relativePath, List<String> contentLines) {
    resourceNameToContent.put(relativePath, Joiner.on("\n").join(contentLines).getBytes(UTF_8));
    return this;
  }

  @CanIgnoreReturnValue
  public LibraryFileBuilder addContent(String relativePath, byte[] content) {
    resourceNameToContent.put(relativePath, content);
    return this;
  }

  @CanIgnoreReturnValue
  public File build() {
    try {
      // create file if it does not exist
      file.getParentFile().mkdirs();
      file.createNewFile();
      try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
        for (Map.Entry<String, byte[]> entry : resourceNameToContent.entrySet()) {
          out.putNextEntry(new ZipEntry(entry.getKey()));
          out.write(entry.getValue(), 0, entry.getValue().length);
          out.closeEntry();
        }
      }
    } catch (Exception e) {
      LOG.error(e);
    }
    return file;
  }
}
