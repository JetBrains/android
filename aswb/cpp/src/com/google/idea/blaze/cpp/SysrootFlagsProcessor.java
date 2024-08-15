/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SysrootFlagsProcessor implements BlazeCompilerFlagsProcessor {

  static class Provider implements BlazeCompilerFlagsProcessor.Provider {

    @Override
    public Optional<BlazeCompilerFlagsProcessor> getProcessor(Project project) {
      BlazeProjectData projectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (projectData == null) {
        return Optional.empty();
      }
      return Optional.of(new SysrootFlagsProcessor(projectData.getWorkspacePathResolver()));
    }
  }

  // There's also multiple flag ["--sysroot", "value"] version, but we're only handling the
  // single flag version for now.
  private static final Pattern SYSROOT_PATTERN = Pattern.compile("^(--sysroot=)(.*)$");
  private final WorkspacePathResolver workspacePathResolver;

  private SysrootFlagsProcessor(WorkspacePathResolver workspacePathResolver) {
    this.workspacePathResolver = workspacePathResolver;
  }

  @Override
  public List<String> processFlags(List<String> flags) {
    return flags.stream().map(this::map).collect(ImmutableList.toImmutableList());
  }

  private String map(String flag) {
    // For some reason sysroot needs to be an absolute path for clangd to find the headers,
    // even if clangd's CWD is the workspace root, and the flag is relative to the workspace root.
    // clang by itself seems to work okay with relative path.
    //
    // Given a --sysroot, the compiler should then know about the directories present in
    // CToolchainIdeInfo#builtInIncludeDirectories()
    //
    // So, either
    //  * Make the sysroot an absolute path so that the built in include directories are found
    //  * Or, explicitly pass flags for each of the CToolchainIdeInfo#builtInIncludeDirectories().
    //    The "normal" flags that the driver uses are internal like "-internal-externc-isystem",
    //    so the closest thing we could pass in externally would be "-isystem".
    Matcher m = SYSROOT_PATTERN.matcher(flag);
    if (m.matches()) {
      String flagPrefix = m.group(1);
      String path = m.group(2);
      if (new File(path).isAbsolute()) {
        return flag;
      }
      File workspacePath = workspacePathResolver.resolveToFile(path);
      return flagPrefix + workspacePath.getAbsolutePath();
    } else {
      return flag;
    }
  }
}
