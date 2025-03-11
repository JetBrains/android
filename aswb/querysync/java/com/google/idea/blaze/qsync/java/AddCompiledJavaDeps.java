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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.deps.ArtifactDirectoryBuilder;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.project.ProjectProto.JarDirectory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Adds compiled jars from dependencies to the project. */
public class AddCompiledJavaDeps implements ProjectProtoUpdateOperation {
  private final boolean enableBazelAdditionalLibraryRootsProvider;

  public AddCompiledJavaDeps(boolean enableBazelAdditionalLibraryRootsProvider) {
    this.enableBazelAdditionalLibraryRootsProvider = enableBazelAdditionalLibraryRootsProvider;
  }

  @Override
  public void update(ProjectProtoUpdate update, ArtifactTracker.State artifactState, Context<?> context) {
    ArtifactDirectoryBuilder javaDepsDir = update.artifactDirectory(ArtifactDirectories.JAVADEPS);
    Map<String, Set<String>> libNameToJars = new HashMap<>();
    for (TargetBuildInfo target : artifactState.targets()) {
      if (target.javaInfo().isPresent()) {
        JavaArtifactInfo javaInfo = target.javaInfo().get();
        for (BuildArtifact jar : javaInfo.jars()) {
          javaDepsDir.addIfNewer(jar.artifactPath(), jar, target.buildContext());
          libNameToJars
            .computeIfAbsent(target.label().toString(), t -> new HashSet())
            .add(javaDepsDir.path().resolve(jar.artifactPath()).toString());
        }
      }
    }
    if (!enableBazelAdditionalLibraryRootsProvider) {
      updateProjectProtoUpdateAllJarsInOneLibrary(javaDepsDir, update);
    } else {
      updateProjectProtoUpdateOneTargetToOneLibrary(libNameToJars, update);
    }
  }

  private void updateProjectProtoUpdateOneTargetToOneLibrary(
    Map<String, Set<String>> libNameToJars, ProjectProtoUpdate update) {
    libNameToJars.forEach(
      (name, jars) ->
        update
          .library(name)
          .addAllClassesJar(
            jars.stream()
              .map(
                jar ->
                  JarDirectory.newBuilder().setPath(jar).setRecursive(false).build())
              .collect(toImmutableSet())));
  }

  private void updateProjectProtoUpdateAllJarsInOneLibrary(
    ArtifactDirectoryBuilder javaDepsDir, ProjectProtoUpdate update) {
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
