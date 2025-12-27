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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectPath.AbsoluteProjectPath
import com.google.idea.blaze.qsync.project.ProjectPath.ExternalRepositoryRelativeProjectPath
import com.google.idea.blaze.qsync.project.ProjectPath.ProjectRelativeProjectPath
import com.google.idea.blaze.qsync.project.ProjectPath.WorkspaceRelativeProjectPath
import java.nio.file.Path
import java.time.Instant

/** Deserializes [NewArtifactTracker] state from a proto.  */
class ArtifactTrackerStateDeserializer(private val metadataFactory: ArtifactMetadata.Factory) {
  val builtDepsMap: Map<Label, TargetBuildInfo> get() = _depsMap
  val ccToolchainMap: Map<String, CcToolchain> get() = _ccToolchainMap

  private val _depsMap = mutableMapOf<Label, TargetBuildInfo>()
  private val _ccToolchainMap = mutableMapOf<String, CcToolchain>()
  private val buildContexts = mutableMapOf<String, DependencyBuildContext>()

  fun visit(proto: ArtifactTrackerProto.ArtifactTrackerState) {
    if (proto.version != ArtifactTrackerStateSerializer.VERSION) {
      // Skip loading older versions.
      return
    }
    proto.buildContextsList.forEach { visitBuildContext(it) }
    proto.builtDepsMap.entries.forEach { visitTargetBuildInfo(it) }
    proto.ccToolchainsMap.forEach { visitCcToolchain(it.key, it.value) }
  }

  private fun visitBuildContext(buildContext: ArtifactTrackerProto.BuildContext) {
    buildContexts[buildContext.getBuildIdForLogging()] =
      DependencyBuildContext.create(
        buildContext.getBuildIdForLogging(),
        Instant.ofEpochMilli(buildContext.startTimeMillis)
      )
  }

  private fun visitTargetBuildInfo(entry: Map.Entry<String, ArtifactTrackerProto.TargetBuildInfo>) {
    val proto = entry.value
    val builder = TargetBuildInfo.builder().buildContext(buildContexts[proto.getBuildId()])
    val owner = Label.of(entry.key)
    if (proto.hasJavaArtifacts()) {
      builder.javaInfo(convertJavaArtifactInfo(owner, proto.javaArtifacts))
    }
    if (proto.hasCcInfo()) {
      builder.ccInfo(convertCcCompilationInfo(owner, proto.ccInfo))
    }
    _depsMap[owner] = builder.build()
  }

  private fun visitCcToolchain(id: String, proto: ArtifactTrackerProto.CcToolchain) {
    _ccToolchainMap[id] = CcToolchain.builder()
      .id(id)
      .compiler(proto.getCompiler())
      .compilerExecutable(projectPathFrom(proto.compilerExecutable))
      .cpu(proto.getCpu())
      .targetGnuSystemName(proto.getTargetGnuSystemName())
      .builtInIncludeDirectories(ImmutableList.copyOf(proto.builtInIncludeDirectoriesList.map { projectPathFrom(it) }))
      .cOptions(ImmutableList.copyOf(proto.cOptionsList))
      .cppOptions(ImmutableList.copyOf(proto.cppOptionsList))
      .build()
  }

  private fun projectPathFrom(p: ArtifactTrackerProto.ProjectPath): ProjectPath {
    val emptyPath = Path.of("")
    return when (p.getBase()) {
      ArtifactTrackerProto.ProjectPath.Base.UNSPECIFIED, ArtifactTrackerProto.ProjectPath.Base.UNRECOGNIZED -> error("Unexpected value: $p")
      ArtifactTrackerProto.ProjectPath.Base.WORKSPACE ->
        WorkspaceRelativeProjectPath(Path.of(p.getPath()), innerPath = emptyPath)
      ArtifactTrackerProto.ProjectPath.Base.EXTERNAL_REPOSITORY ->
        ExternalRepositoryRelativeProjectPath(p.getExternalRepository(), relativePath = Path.of(p.getPath()), innerPath = emptyPath)
      ArtifactTrackerProto.ProjectPath.Base.PROJECT ->
        ProjectRelativeProjectPath(relativePath = Path.of(p.getPath()), innerPath = emptyPath)
      ArtifactTrackerProto.ProjectPath.Base.ABSOLUTE -> AbsoluteProjectPath(absolutePath = Path.of(p.getPath()), innerPath = emptyPath)
    }
  }

  private fun convertJavaArtifactInfo(owner: Label, proto: ArtifactTrackerProto.JavaArtifacts): JavaArtifactInfo {
    return JavaArtifactInfo.builder()
      .setLabel(owner)
      .setIsExternalDependency(proto.isExternalDependency)
      .setJars(toArtifactList(proto.jarsList, owner))
      .setOutputJars(toArtifactList(proto.outputJarsList, owner))
      .setIdeAar(if (proto.hasIdeAar()) toArtifact(proto.ideAar, owner) else null)
      .setGenSrcs(toArtifactList(proto.genSrcsList, owner))
      .setGenAndroidRes(toArtifactList(proto.genAndroidResList, owner))
      .setProtoSrcjars(toArtifactList(proto.protoSrcjarsList, owner))
      .setSources(proto.sourcesList.map { projectPathFrom(it) }.toSet())
      .setSrcJars(proto.srcJarsList.map { projectPathFrom(it) }.toSet())
      .setAndroidResourcesPackage(proto.getAndroidResourcesPackage())
      .setKotlinCompilerFlags(ImmutableList.copyOf(proto.kotlinCompilerFlagsList))
      .setIsKotlinToolchain(proto.isKotlinToolchain)
      .build()
  }

  private fun convertCcCompilationInfo(owner: Label, proto: ArtifactTrackerProto.CcCompilationInfo): CcCompilationInfo {
    return CcCompilationInfo.builder()
      .target(owner)
      .copts(proto.coptsList)
      .defines(proto.definesList)
      .includeDirectories(proto.includeDirectoriesList.map { projectPathFrom(it) })
      .quoteIncludeDirectories(proto.quoteIncludeDirectoriesList.map { projectPathFrom(it) })
      .systemIncludeDirectories(
        proto.sysytemIncludeDirectoriesList.map { projectPathFrom(it) })
      .frameworkIncludeDirectories(proto.frameworkIncludeDirectoriesList.map { projectPathFrom(it) })
      .genHeaders(toArtifactList(proto.genHeadersList, owner))
      .toolchainId(proto.getToolchainId())
      .build()
  }

  private fun toArtifact(a: ArtifactTrackerProto.Artifact, owner: Label): BuildArtifact {
    return BuildArtifact.create(
      a.getDigest(),
      Path.of(a.getArtifactPath()),
      owner,
      ImmutableMap.copyOf(toArtifactMap(a.metadataList))
    )
  }

  private fun toArtifactList(protos: List<ArtifactTrackerProto.Artifact>, owner: Label): List<BuildArtifact> {
    return protos.map { toArtifact(it, owner) }
  }

  private fun toArtifactMap(protoList: List<ArtifactTrackerProto.Metadata>): Map<Class<out ArtifactMetadata>, ArtifactMetadata> {
    return protoList
      .mapNotNull { metadataFactory.create(it) }
      .associateBy { it.javaClass }
  }
}
