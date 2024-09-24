/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.java;

import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.deps.ArtifactMetadata;
import java.io.IOException;

/** Package name as read from a Java source file. */
public class JavaSourcePackageNameMetadata implements ArtifactMetadata {

  private final PackageStatementParser packageParser;

  public JavaSourcePackageNameMetadata(PackageStatementParser packageParser) {
    this.packageParser = packageParser;
  }

  @Override
  public String key() {
    return "JavaSourcePackageName";
  }

  @Override
  public String extract(CachedArtifact buildArtifact, Object nameForLogs) throws BuildException {
    try {
      return packageParser.readPackage(buildArtifact.byteSource().openStream());
    } catch (IOException e) {
      throw new BuildException("Failed to read package statement from " + nameForLogs, e);
    }
  }
}
