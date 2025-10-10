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

import com.google.common.collect.Sets
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectPath.AbsoluteProjectPath
import com.google.idea.blaze.qsync.project.ProjectPath.ProjectRelativeProjectPath
import com.google.idea.blaze.qsync.project.ProjectPath.WorkspaceRelativeProjectPath

/** Serializes [NewArtifactTracker] state to a proto.  */
class ArtifactTrackerStateSerializer {
  companion object {
    const val VERSION: Int = 4
  }
  private val proto = ArtifactTrackerProto.ArtifactTrackerState.newBuilder().setVersion(VERSION)
  private val buildIdsSeen: MutableSet<String> = Sets.newHashSet()

  @CanIgnoreReturnValue
  fun visitDepsMap(builtDeps: Map<Label, TargetBuildInfo>): ArtifactTrackerStateSerializer {
    builtDeps.entries.forEach { visitTargetBuildInfo(it.key, it.value) }
    return this
  }

  @CanIgnoreReturnValue
  fun visitToolchainMap(toolchainMap: Map<String, CcToolchain>): ArtifactTrackerStateSerializer {
    toolchainMap.values.forEach { visitCcToolchain(it) }
    return this
  }

  fun toProto(): ArtifactTrackerProto.ArtifactTrackerState {
    return proto.build()
  }

  private fun visitTargetBuildInfo(target: Label, targetBuildInfo: TargetBuildInfo) {
    visitBuildContext(targetBuildInfo.buildContext())

    val builder = ArtifactTrackerProto.TargetBuildInfo.newBuilder()
    builder.setBuildId(targetBuildInfo.buildContext().buildIdForLogging())
    targetBuildInfo.javaInfo().ifPresent { visitJavaInfo(it, builder) }
    targetBuildInfo.ccInfo().ifPresent { visitCcInfo(it, builder) }
    proto.putBuiltDeps(target.toString(), builder.build())
  }

  private fun visitBuildContext(buildContext: DependencyBuildContext) {
    if (buildIdsSeen.add(buildContext.buildIdForLogging())) {
      proto
        .addBuildContextsBuilder()
        .setStartTimeMillis(buildContext.startTime().toEpochMilli())
        .setBuildIdForLogging(buildContext.buildIdForLogging())
    }
  }

  private fun visitJavaInfo(javaInfo: JavaArtifactInfo, builder: ArtifactTrackerProto.TargetBuildInfo.Builder) {
    val artifactTrackerProtoBuilder = builder.getJavaArtifactsBuilder()
    javaInfo.ideAar()?.let {artifactTrackerProtoBuilder.setIdeAar(toProto(it))}
    artifactTrackerProtoBuilder
      .addAllGenSrcs(toProtos(javaInfo.genSrcs()))
      .addAllJars(toProtos(javaInfo.jars()))
      .addAllSources(javaInfo.sources().map { it.toString() })
      .addAllSrcJars(javaInfo.srcJars().map { it.toString() })
      .setAndroidResourcesPackage(javaInfo.androidResourcesPackage())
  }

  private fun toProtos(artifacts: Collection<BuildArtifact>): List<ArtifactTrackerProto.Artifact> {
    return artifacts.map { toProto(it) }
  }

  private fun toProto(artifact: BuildArtifact): ArtifactTrackerProto.Artifact {
    return ArtifactTrackerProto.Artifact.newBuilder()
      .setDigest(artifact.digest())
      .setArtifactPath(artifact.artifactPath().toString())
      .addAllMetadata(artifact.metadata().values.map { it.toProto() })
      .build()
  }

  private fun visitCcInfo(ccInfo: CcCompilationInfo, builder: ArtifactTrackerProto.TargetBuildInfo.Builder) {
    builder
      .getCcInfoBuilder()
      .addAllDefines(ccInfo.defines())
      .addAllIncludeDirectories(ccInfo.includeDirectories().map { projectPathToProto(it) })
      .addAllQuoteIncludeDirectories(ccInfo.quoteIncludeDirectories().map { projectPathToProto(it) })
      .addAllSysytemIncludeDirectories(ccInfo.systemIncludeDirectories().map { projectPathToProto(it) })
      .addAllFrameworkIncludeDirectories(ccInfo.frameworkIncludeDirectories().map { projectPathToProto(it) })
      .addAllGenHeaders(toProtos(ccInfo.genHeaders()))
      .setToolchainId(ccInfo.toolchainId())
  }

  private fun projectPathToProto(projectPath: ProjectPath): ArtifactTrackerProto.ProjectPath {
    when (projectPath) {
      is WorkspaceRelativeProjectPath -> {
        return ArtifactTrackerProto.ProjectPath.newBuilder()
          .setBase(ArtifactTrackerProto.ProjectPath.Base.WORKSPACE)
          .setPath(projectPath.relativePath.toString())
          .build()
      }

      is ProjectPath.ExternalRepositoryRelativeProjectPath -> {
        return ArtifactTrackerProto.ProjectPath.newBuilder()
          .setBase(ArtifactTrackerProto.ProjectPath.Base.EXTERNAL_REPOSITORY)
          .setExternalRepository(projectPath.externalRepositoryName)
          .setPath(projectPath.relativePath.toString())
          .build()
      }

      is ProjectRelativeProjectPath -> {
        return ArtifactTrackerProto.ProjectPath.newBuilder()
          .setBase(ArtifactTrackerProto.ProjectPath.Base.PROJECT)
          .setPath(projectPath.relativePath.toString())
          .build()
      }

      is AbsoluteProjectPath -> {
        return ArtifactTrackerProto.ProjectPath.newBuilder()
          .setBase(ArtifactTrackerProto.ProjectPath.Base.ABSOLUTE)
          .setPath(projectPath.absolutePath.toString())
          .build()
      }
    }
  }

  private fun visitCcToolchain(toolchain: CcToolchain) {
    proto.putCcToolchains(
      toolchain.id(),
      ArtifactTrackerProto.CcToolchain.newBuilder()
        .setCompiler(toolchain.compiler())
        .setCompilerExecutable(projectPathToProto(toolchain.compilerExecutable()))
        .setCpu(toolchain.cpu())
        .setTargetGnuSystemName(toolchain.targetGnuSystemName())
        .addAllBuiltInIncludeDirectories(toolchain.builtInIncludeDirectories().map { projectPathToProto(it) })
        .addAllCOptions(toolchain.cOptions())
        .addAllCppOptions(toolchain.cppOptions())
        .build())
  }
}
