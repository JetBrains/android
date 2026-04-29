/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.artifacts.DigestMap
import com.google.idea.blaze.qsync.artifacts.withMetadata
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcTargetInfo
import com.google.idea.blaze.qsync.project.ProjectPath

/**
 * C/C++ compilation information. This stores information required to compile C or C++ targets. The information is extracted from the build
 * at build deps time.
 */
data class CcCompilationInfo(
  val target: Label,
  val copts: List<String>,
  val defines: List<String>,
  val includeDirectories: List<ProjectPath>,
  val quoteIncludeDirectories: List<ProjectPath>,
  val systemIncludeDirectories: List<ProjectPath>,
  val frameworkIncludeDirectories: List<ProjectPath>,
  val genHeaders: Set<BuildArtifact>,
  val toolchainId: String,
) {

  fun withMetadata(metadata: Map<BuildArtifact, List<ArtifactMetadata>>): CcCompilationInfo {
    if (metadata.isEmpty()) {
      return this
    }
    return copy(genHeaders = genHeaders.withMetadata(metadata).toSet())
  }

  companion object {
    @JvmStatic
    fun create(
      targetInfo: CcTargetInfo,
      digestMap: DigestMap,
      externalRepositoryFinder: ProjectPath.ExternalRepositoryFinder,
    ): CcCompilationInfo {
      val target = Label.of(targetInfo.label)
      return CcCompilationInfo(
        target = target,
        copts = targetInfo.coptsList,
        defines = targetInfo.definesList,
        includeDirectories = targetInfo.includeDirectoriesList.map { ArtifactDirectories.forCcInclude(it, externalRepositoryFinder) },
        quoteIncludeDirectories =
          targetInfo.quoteIncludeDirectoriesList.map { ArtifactDirectories.forCcInclude(it, externalRepositoryFinder) },
        systemIncludeDirectories =
          targetInfo.systemIncludeDirectoriesList.map { ArtifactDirectories.forCcInclude(it, externalRepositoryFinder) },
        frameworkIncludeDirectories =
          targetInfo.frameworkIncludeDirectoriesList.map { ArtifactDirectories.forCcInclude(it, externalRepositoryFinder) },
        genHeaders = BuildArtifact.fromProtos(targetInfo.genHdrsList, digestMap, target).toSet(),
        toolchainId = targetInfo.toolchainId,
      )
    }
  }
}
