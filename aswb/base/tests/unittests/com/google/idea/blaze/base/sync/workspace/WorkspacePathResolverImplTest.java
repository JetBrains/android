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
package com.google.idea.blaze.base.sync.workspace;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for workspace path resolver */
@RunWith(JUnit4.class)
public class WorkspacePathResolverImplTest extends BlazeTestCase {
  private static final WorkspaceRoot WORKSPACE_ROOT = new WorkspaceRoot(new File("/path/to/root"));

  @Test
  public void testResolveToIncludeDirectories() {
    WorkspacePathResolver workspacePathResolver = new WorkspacePathResolverImpl(WORKSPACE_ROOT);
    ImmutableList<File> files =
        workspacePathResolver.resolveToIncludeDirectories(new WorkspacePath("tools/fast"));
    assertThat(files).containsExactly(new File("/path/to/root/tools/fast"));
  }

  @Test
  public void testResolveToIncludeDirectoriesForExecRootPath() {
    WorkspacePathResolver workspacePathResolver = new WorkspacePathResolverImpl(WORKSPACE_ROOT);
    ImmutableList<File> files =
        workspacePathResolver.resolveToIncludeDirectories(
            new WorkspacePath("blaze-out/crosstool/bin/tools/fast"));
    assertThat(files).containsExactly(new File("/path/to/root/blaze-out/crosstool/bin/tools/fast"));
  }

  @Test
  public void testResolveToFile() {
    WorkspacePathResolver workspacePathResolver = new WorkspacePathResolverImpl(WORKSPACE_ROOT);
    WorkspacePath relativePath = new WorkspacePath("third_party/tools");
    assertThat(workspacePathResolver.resolveToFile(relativePath))
        .isEqualTo(WORKSPACE_ROOT.fileForPath(relativePath));
  }
}
