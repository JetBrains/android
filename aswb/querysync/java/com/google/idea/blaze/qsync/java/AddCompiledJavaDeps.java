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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableCollection;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.deps.ArtifactDirectoryBuilder;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.project.ProjectProto.JarDirectory;

/** Adds compiled jars from dependencies to the project. */
public class AddCompiledJavaDeps implements ProjectProtoUpdateOperation {
  private final Supplier<ImmutableCollection<TargetBuildInfo>> builtTargetsSupplier;

  public AddCompiledJavaDeps(Supplier<ImmutableCollection<TargetBuildInfo>> builtTargetsSupplier) {
    this.builtTargetsSupplier = builtTargetsSupplier;
  }

  @Override
  public void update(ProjectProtoUpdate update) {
    ArtifactDirectoryBuilder javaDepsDir = update.artifactDirectory(ArtifactDirectories.JAVADEPS);
    for (TargetBuildInfo target : builtTargetsSupplier.get()) {
      if (target.javaInfo().isPresent()) {
        JavaArtifactInfo javaInfo = target.javaInfo().get();
        for (BuildArtifact jar : javaInfo.jars()) {
          javaDepsDir.addIfNewer(jar.artifactPath(), jar, target.buildContext());
        }
      }
    }
    if (!javaDepsDir.isEmpty()) {
      update
          .library(JAVA_DEPS_LIB_NAME)
          .addClassesJar(
              JarDirectory.newBuilder().setPath(javaDepsDir.path().toString()).setRecursive(true));
      if (!update.workspaceModule().getLibraryNameList().contains(JAVA_DEPS_LIB_NAME)) {
        update.workspaceModule().addLibraryName(JAVA_DEPS_LIB_NAME);
      }
    }
  }
}
