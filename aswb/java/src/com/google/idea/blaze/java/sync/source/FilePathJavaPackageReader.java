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
package com.google.idea.blaze.java.sync.source;

import com.google.common.base.Strings;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.util.PackagePrefixCalculator;
import java.io.File;

/** Gets the package from a java file by its file path alone (i.e. without opening the file). */
public final class FilePathJavaPackageReader extends JavaPackageReader {
  @Override
  public String getDeclaredPackageOfJavaFile(
      BlazeContext context,
      ArtifactLocationDecoder artifactLocationDecoder,
      SourceArtifact sourceArtifact) {
    String parentPath = new File(sourceArtifact.artifactLocation.getRelativePath()).getParent();
    return PackagePrefixCalculator.packagePrefixOf(
        new WorkspacePath(Strings.nullToEmpty(parentPath)));
  }
}
