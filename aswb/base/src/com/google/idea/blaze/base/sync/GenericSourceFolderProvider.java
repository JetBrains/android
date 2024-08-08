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
import com.google.idea.blaze.base.util.UrlUtil;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import java.io.File;

/** An implementation of {@link SourceFolderProvider} with no language-specific settings. */
public class GenericSourceFolderProvider implements SourceFolderProvider {

  public static final GenericSourceFolderProvider INSTANCE = new GenericSourceFolderProvider();

  private GenericSourceFolderProvider() {}

  @Override
  public ImmutableMap<File, SourceFolder> initializeSourceFolders(ContentEntry contentEntry) {
    String url = contentEntry.getUrl();
    return ImmutableMap.of(UrlUtil.urlToFile(url), contentEntry.addSourceFolder(url, false));
  }

  @Override
  public SourceFolder setSourceFolderForLocation(
      ContentEntry contentEntry, SourceFolder parentFolder, File file, boolean isTestSource) {
    return contentEntry.addSourceFolder(UrlUtil.fileToIdeaUrl(file), isTestSource);
  }
}
