/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.common.Context;
import java.nio.file.Path;

/**
 * A {@link PackageReader} that accepts paths relative to the workspace root and then delegates to a
 * {@link PackageReader} that expects an absolute file path.
 */
public class WorkspaceResolvingPackageReader implements PackageReader {

  private final Path workspaceRoot;
  private final PackageReader absolutePackageReader;

  public WorkspaceResolvingPackageReader(
      Path workspaceRoot, PackageReader absolutePackageReader) {
    this.workspaceRoot = workspaceRoot;
    this.absolutePackageReader = absolutePackageReader;
  }

  @Override
  public String readPackage(Context<?> context, Path path) {
    return absolutePackageReader.readPackage(context, workspaceRoot.resolve(path));
  }
}
