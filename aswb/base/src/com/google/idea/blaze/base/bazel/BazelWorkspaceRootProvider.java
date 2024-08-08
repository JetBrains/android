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
package com.google.idea.blaze.base.bazel;

import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import java.io.File;
import javax.annotation.Nullable;

/** Implementation of WorkspaceHelper. */
public class BazelWorkspaceRootProvider implements WorkspaceRootProvider {

  public static final BazelWorkspaceRootProvider INSTANCE = new BazelWorkspaceRootProvider();

  private BazelWorkspaceRootProvider() {}

  /** Checks for the existence of a WORKSPACE file in the given directory. */
  @Override
  public boolean isWorkspaceRoot(File file) {
    return FileOperationProvider.getInstance().isFile(new File(file, "WORKSPACE"))
        || FileOperationProvider.getInstance().isFile(new File(file, "WORKSPACE.bazel"));
  }

  @Nullable
  @Override
  public WorkspaceRoot findWorkspaceRoot(File file) {
    while (file != null) {
      if (isWorkspaceRoot(file)) {
        return new WorkspaceRoot(file);
      }
      file = file.getParentFile();
    }
    return null;
  }
}
