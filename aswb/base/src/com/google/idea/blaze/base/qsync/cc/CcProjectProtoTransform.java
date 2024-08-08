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
package com.google.idea.blaze.base.qsync.cc;

import com.google.common.base.Supplier;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.cc.CcDependenciesInfo;
import com.google.idea.blaze.qsync.cc.CcWorkspaceBuilder;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.ProjectProto.Project;
import com.google.idea.blaze.qsync.project.ProjectProtoTransform;

/**
 * Adds cc workspace information to the project proto, based on the output from cc dependency
 * builds.
 */
public class CcProjectProtoTransform implements ProjectProtoTransform {

  private final Supplier<CcDependenciesInfo> ccDependenciesInfoSupplier;

  public CcProjectProtoTransform(Supplier<CcDependenciesInfo> ccDependenciesInfoSupplier) {
    this.ccDependenciesInfoSupplier = ccDependenciesInfoSupplier;
  }

  @Override
  public Project apply(Project proto, BuildGraphData graph, Context<?> context)
      throws BuildException {
    return new CcWorkspaceBuilder(ccDependenciesInfoSupplier.get(), graph, context)
        .updateProjectProtoForCcDeps(proto);
  }
}
