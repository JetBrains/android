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
package com.google.idea.blaze.qsync.deps

import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSetMultimap
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata
import com.google.idea.blaze.qsync.artifacts.BuildArtifact

/**
 * An update to the project proto that operates on a [ProjectProtoUpdate]. Also defines some
 * constants that are useful to implementations.
 *
 *
 * Implementations of this interface must not depend on any project state other than [ ].
 */
interface ProjectProtoUpdateOperation {
  fun getRequiredArtifacts(forTarget: TargetBuildInfo): Map<BuildArtifact, Collection<ArtifactMetadata.Extractor<*>>> = mapOf()

  @Throws(BuildException::class)
  fun update(
    update: ProjectProtoUpdate,
    artifactState: ArtifactTracker.State,
    context: Context<*>
  )

  companion object {
    @JvmField
    val JAVA_ARCHIVE_EXTENSIONS: ImmutableSet<String> = ImmutableSet.of("jar", "srcjar")
  }
}
