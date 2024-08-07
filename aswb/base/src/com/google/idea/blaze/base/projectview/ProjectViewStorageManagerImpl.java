/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.projectview;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.file.Files;

/** Project view storage implementation. */
final class ProjectViewStorageManagerImpl extends ProjectViewStorageManager {

  @Override
  public String loadProjectView(File projectViewFile) throws IOException {
    try (InputStreamReader reader =
        new InputStreamReader(InputStreamProvider.getInstance().forFile(projectViewFile), UTF_8)) {
      return CharStreams.toString(reader);
    }
  }

  @Override
  public void writeProjectView(String projectViewText, File projectViewFile) throws IOException {
    try (Writer fileWriter = Files.newBufferedWriter(projectViewFile.toPath(), UTF_8)) {
      fileWriter.write(projectViewText);
    }
    LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(projectViewFile));
  }
}
