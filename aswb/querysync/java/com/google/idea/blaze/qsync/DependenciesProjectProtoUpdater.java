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
package com.google.idea.blaze.qsync;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.cc.ConfigureCcCompilation;
import com.google.idea.blaze.qsync.deps.NewArtifactTracker;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation;
import com.google.idea.blaze.qsync.java.AddCompiledJavaDeps;
import com.google.idea.blaze.qsync.java.AddDependencyGenSrcsJars;
import com.google.idea.blaze.qsync.java.AddDependencySrcJars;
import com.google.idea.blaze.qsync.java.AddProjectGenSrcJars;
import com.google.idea.blaze.qsync.java.AddProjectGenSrcs;
import com.google.idea.blaze.qsync.java.PackageStatementParser;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto.Project;
import com.google.idea.blaze.qsync.project.ProjectProtoTransform;

/**
 * A {@link ProjectProtoTransform} that adds built artifact information to the project proto, based
 * on all artifacts that have been built.
 */
public class DependenciesProjectProtoUpdater implements ProjectProtoTransform {
  private final ImmutableList<ProjectProtoUpdateOperation> updateOperations;

  public DependenciesProjectProtoUpdater(
      NewArtifactTracker<?> dependencyTracker,
      ProjectDefinition projectDefinition,
      BuildArtifactCache artifactCache,
      ProjectPath.Resolver pathResolver,
      Supplier<Boolean> attachDepsSrcjarsExperiment) {
    // Require empty package prefixes for srcjar inner paths, since the ultimate consumer of these
    // paths does not support setting a package prefix (see `Library.ModifiableModel.addRoot`).
    PackageStatementParser packageReader = new PackageStatementParser();
    SrcJarInnerPathFinder srcJarInnerPathFinder = new SrcJarInnerPathFinder(packageReader);

    ImmutableList.Builder<ProjectProtoUpdateOperation> updateOperations =
        ImmutableList.<ProjectProtoUpdateOperation>builder()
            .add(new AddCompiledJavaDeps(dependencyTracker::getBuiltDeps))
            .add(
                new AddProjectGenSrcJars(
                    dependencyTracker::getBuiltDeps,
                    projectDefinition,
                    artifactCache,
                    srcJarInnerPathFinder))
            .add(
                new AddProjectGenSrcs(
                    dependencyTracker::getBuiltDeps,
                    projectDefinition,
                    artifactCache,
                    packageReader))
            .add(new ConfigureCcCompilation.UpdateOperation(dependencyTracker::getStateSnapshot));
    if (attachDepsSrcjarsExperiment.get()) {
      updateOperations.add(
          new AddDependencySrcJars(
              dependencyTracker::getBuiltDeps,
              projectDefinition,
              pathResolver,
              srcJarInnerPathFinder));
      updateOperations.add(
          new AddDependencyGenSrcsJars(
              dependencyTracker::getBuiltDeps,
              projectDefinition,
              artifactCache,
              srcJarInnerPathFinder));
    }
    this.updateOperations = updateOperations.build();
  }

  @Override
  public Project apply(Project proto, BuildGraphData graph, Context<?> context)
      throws BuildException {

    ProjectProtoUpdate protoUpdate = new ProjectProtoUpdate(proto, graph, context);
    for (ProjectProtoUpdateOperation op : updateOperations) {
      op.update(protoUpdate);
    }
    return protoUpdate.build();
  }
}
