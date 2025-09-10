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
package com.google.idea.blaze.qsync.java

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.AllowPackagePrefixes
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull

/**
 * Adds checked-in `.srcjar` files from external dependencies to the project proto. This
 * allows those sources to be shown in the IDE instead of decompiled class files.
 */
class AddDependencySrcJars(
  private val projectDefinition: ProjectDefinition,
  private val pathResolver: ProjectPath.Resolver,
  private val srcJarInnerPathFinder: SrcJarInnerPathFinder
) : ProjectProtoUpdateOperation {
  @Throws(BuildException::class)
  override fun update(
    update: ProjectProtoUpdate,
    artifactState: ArtifactTracker.State,
    context: Context<*>
  ) {
    for (target in artifactState.targets()) {
      val javaInfo = target.javaInfo().getOrNull() ?: continue
      if (projectDefinition.isIncluded(javaInfo.label())) {
        continue
      }
      for (srcJar in javaInfo.srcJars()) {
        // these are workspace relative srcjar paths.
        val jarPath = ProjectPath.workspaceRelative(srcJar)
        srcJarInnerPathFinder
          .findInnerJarPaths(
            pathResolver.resolve(jarPath).toFile(),
            AllowPackagePrefixes.EMPTY_PACKAGE_PREFIXES_ONLY,
            srcJar.toString()
          )
          .map { jarPath.withInnerJarPath(it.path()).toProto() }
          .map { ProjectProto.LibrarySource.newBuilder().setSrcjar(it) }
          .forEach {
            update.library(target.label().toString()).addSources(it)
          }
      }
    }
  }
}
