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

import com.google.common.collect.ImmutableSetMultimap
import com.google.idea.blaze.common.Interners
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.artifacts.DigestMap
import com.google.idea.blaze.qsync.java.JavaTargetInfo
import com.google.idea.blaze.qsync.project.ProjectPath

/** Information about a project dependency that is calculated when the dependency is built. */
data class JavaArtifactInfo(
  val label: Label,
  val isExternalDependency: Boolean,
  val isKotlinToolchain: Boolean,
  val jars: Set<BuildArtifact>,
  val outputJars: Set<BuildArtifact>,
  val ideAar: BuildArtifact?,
  val genSrcs: Set<BuildArtifact>,
  val genAndroidRes: Set<BuildArtifact>,
  val protoSrcjars: Set<BuildArtifact>,
  val sources: Set<ProjectPath>,
  val srcJars: Set<ProjectPath>,
  val androidResourcesPackage: String,
  val kotlinCompilerFlags: List<String>,
) {

  fun withMetadata(metadata: ImmutableSetMultimap<BuildArtifact, ArtifactMetadata>): JavaArtifactInfo {
    if (metadata.isEmpty) {
      return this
    }
    return copy(
      genSrcs = BuildArtifact.addMetadata(genSrcs, metadata).toSet(),
      genAndroidRes = BuildArtifact.addMetadata(genAndroidRes, metadata).toSet(),
      protoSrcjars = BuildArtifact.addMetadata(protoSrcjars, metadata).toSet(),
      ideAar = ideAar?.withMetadata(metadata.get(ideAar)),
      jars = BuildArtifact.addMetadata(jars, metadata).toSet(),
      outputJars = BuildArtifact.addMetadata(outputJars, metadata).toSet(),
    )
  }

  companion object {
    @JvmStatic
    fun create(
      proto: JavaTargetInfo.JavaArtifacts,
      digestMap: DigestMap,
      externalRepositoryFinder: ProjectPath.ExternalRepositoryFinder,
    ): JavaArtifactInfo {
      val target = Label.of(proto.target)
      val ideAar =
        if (proto.hasIdeAar()) {
          digestMap.createBuildArtifact(Interners.pathOf(proto.ideAar.file), target).orElse(null)
        } else {
          null
        }
      return JavaArtifactInfo(
        label = target,
        isExternalDependency = proto.isExternalDependency,
        jars = BuildArtifact.fromProtos(proto.jarsList, digestMap, target).toSet(),
        outputJars = BuildArtifact.fromProtos(proto.outputJarsList, digestMap, target).toSet(),
        ideAar = ideAar,
        genSrcs = BuildArtifact.fromProtos(proto.genSrcsList, digestMap, target).toSet(),
        genAndroidRes = BuildArtifact.fromProtos(proto.genAndroidResList, digestMap, target).toSet(),
        protoSrcjars = BuildArtifact.fromProtos(proto.protoSrcjarsList, digestMap, target).toSet(),
        sources = proto.srcsList.map { ProjectPath.workspaceRelative(Interners.pathOf(it), externalRepositoryFinder) }.toSet(),
        srcJars = proto.srcjarsList.map { ProjectPath.workspaceRelative(Interners.pathOf(it), externalRepositoryFinder) }.toSet(),
        androidResourcesPackage = proto.androidResourcesPackage,
        kotlinCompilerFlags = proto.kotlinCompilerFlagsList,
        isKotlinToolchain = proto.isKotlinToolchain,
      )
    }
  }
}
