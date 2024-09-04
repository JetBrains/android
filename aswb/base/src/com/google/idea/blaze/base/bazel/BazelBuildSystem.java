/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.CommandLineBlazeCommandRunner;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperBep;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.BazelBinarySection;
import com.google.idea.blaze.base.qsync.BazelQueryRunner;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Optional;
import javax.annotation.Nullable;

class BazelBuildSystem implements BuildSystem {

  class BazelInvoker extends AbstractBuildInvoker {

    public BazelInvoker(Project project, BlazeContext blazeContext, String path) {
      super(
          project,
          blazeContext,
          BuildBinaryType.BAZEL,
          path,
          false,
          BazelBuildSystem.this,
          new CommandLineBlazeCommandRunner());
    }

    @Override
    @MustBeClosed
    public BuildResultHelper createBuildResultHelper() {
      return new BuildResultHelperBep();
    }
  }

  @Override
  public BuildSystemName getName() {
    return BuildSystemName.Bazel;
  }

  @Override
  public BuildInvoker getBuildInvoker(
      Project project, BlazeContext context, ExecutorType executorType, Kind targetKind) {
    return getBuildInvoker(project, context);
  }

  @Override
  public BuildInvoker getBuildInvoker(
      Project project, BlazeContext context, BlazeCommandName command) {
    return getBuildInvoker(project, context);
  }

  @Override
  public BuildInvoker getBuildInvoker(Project project, BlazeContext context) {
    String binaryPath;
    File projectSpecificBinary = getProjectSpecificBazelBinary(project);
    if (projectSpecificBinary != null) {
      binaryPath = projectSpecificBinary.getPath();
    } else {
      BlazeUserSettings settings = BlazeUserSettings.getInstance();
      binaryPath = settings.getBazelBinaryPath();
    }
    return new BazelInvoker(project, context, binaryPath);
  }

  @Override
  public Optional<BuildInvoker> getParallelBuildInvoker(Project project, BlazeContext context) {
    return Optional.empty();
  }

  @Override
  public Optional<BuildInvoker> getLocalBuildInvoker(Project project, BlazeContext context) {
    return Optional.of(getBuildInvoker(project, context));
  }

  @Override
  public SyncStrategy getSyncStrategy(Project project) {
    return SyncStrategy.SERIAL;
  }

  @Nullable
  static File getProjectSpecificBazelBinary(Project project) {
    ProjectViewSet projectView = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectView == null) {
      return null;
    }
    return projectView.getScalarValue(BazelBinarySection.KEY).orElse(null);
  }

  @Override
  public void populateBlazeVersionData(
      WorkspaceRoot workspaceRoot, BlazeInfo blazeInfo, BlazeVersionData.Builder builder) {
    builder.setBazelVersion(BazelVersion.parseVersion(blazeInfo));
  }

  @Override
  public Optional<String> getBazelVersionString(BlazeInfo blazeInfo) {
    return Optional.ofNullable(BazelVersion.parseVersion(blazeInfo).toString());
  }

  @Override
  public BazelQueryRunner createQueryRunner(Project project) {
    return new BazelQueryRunner(project, this);
  }
}
