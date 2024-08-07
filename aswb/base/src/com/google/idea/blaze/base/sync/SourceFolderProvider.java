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
package com.google.idea.blaze.base.sync;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import java.io.File;

/** Provides source folders for each content entry during sync. */
public interface SourceFolderProvider {

  /** Iterates over the available sync plugins, requesting a SourceFolderProvider. */
  static SourceFolderProvider getSourceFolderProvider(BlazeProjectData projectData) {
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      SourceFolderProvider provider = syncPlugin.getSourceFolderProvider(projectData);
      if (provider != null) {
        return provider;
      }
    }
    return GenericSourceFolderProvider.INSTANCE;
  }

  /**
   * Creates the initial source folders for the given {@link ContentEntry}. These source folders are
   * 'initial' because the 'is test' property (and potentially additional test source folders) are
   * added later.
   */
  ImmutableMap<File, SourceFolder> initializeSourceFolders(ContentEntry contentEntry);

  /**
   * Sets the source folder for the given file, incorporating the test information as appropriate.
   */
  SourceFolder setSourceFolderForLocation(
      ContentEntry contentEntry, SourceFolder parentFolder, File file, boolean isTestSource);
}
