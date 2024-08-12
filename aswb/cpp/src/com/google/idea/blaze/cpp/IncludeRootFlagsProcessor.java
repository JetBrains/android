/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolve execution root relative -I${rel_path}, -isystem, etc. compiler flags to be absolute. */
public class IncludeRootFlagsProcessor implements BlazeCompilerFlagsProcessor {

  static class Provider implements BlazeCompilerFlagsProcessor.Provider {

    @Override
    public Optional<BlazeCompilerFlagsProcessor> getProcessor(Project project) {
      BlazeProjectData projectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (projectData == null) {
        return Optional.empty();
      }
      WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
      ExecutionRootPathResolver executionRootPathResolver =
          new ExecutionRootPathResolver(
              Blaze.getBuildSystemProvider(project),
              workspaceRoot,
              projectData.getBlazeInfo().getExecutionRoot(),
              projectData.getWorkspacePathResolver());
      return Optional.of(new IncludeRootFlagsProcessor(executionRootPathResolver));
    }
  }

  private static final Pattern IFLAG = Pattern.compile("^-isystem|-I|-iquote$");
  private static final Pattern IFLAG_COMBINED = Pattern.compile("^(-isystem|-I|-iquote)(.+)$");
  private final ExecutionRootPathResolver executionRootPathResolver;

  private IncludeRootFlagsProcessor(ExecutionRootPathResolver executionRootPathResolver) {
    this.executionRootPathResolver = executionRootPathResolver;
  }

  @Override
  public List<String> processFlags(List<String> flags) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    String previousIFlag = null;
    for (String flag : flags) {
      if (previousIFlag != null) {
        collectPathFlags(builder, previousIFlag, flag);
        previousIFlag = null;
      } else {
        Matcher iflagMatcher = IFLAG.matcher(flag);
        if (iflagMatcher.matches()) {
          previousIFlag = flag;
          continue;
        }
        Matcher matcher = IFLAG_COMBINED.matcher(flag);
        if (matcher.matches()) {
          collectPathFlags(builder, matcher.group(1), matcher.group(2));
          continue;
        }
        builder.add(flag);
        previousIFlag = null;
      }
    }
    return builder.build();
  }

  private void collectPathFlags(ImmutableList.Builder<String> builder, String iflag, String path) {
    ImmutableList<File> includeDirs =
        executionRootPathResolver.resolveToIncludeDirectories(new ExecutionRootPath(path));
    for (File f : includeDirs) {
      builder.add(iflag);
      builder.add(f.getAbsolutePath());
    }
  }
}
