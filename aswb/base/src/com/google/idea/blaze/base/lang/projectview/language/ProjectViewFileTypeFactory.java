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
package com.google.idea.blaze.base.lang.projectview.language;

import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/** Factory for ProjectViewFileType */
public class ProjectViewFileTypeFactory extends FileTypeFactory {

  @Override
  public void createFileTypes(@NotNull final FileTypeConsumer consumer) {
    FileNameMatcher[] matchers =
        ProjectViewStorageManager.VALID_EXTENSIONS
            .stream()
            .map(ExtensionFileNameMatcher::new)
            .toArray(ExtensionFileNameMatcher[]::new);
    consumer.consume(ProjectViewFileType.INSTANCE, matchers);
  }
}
