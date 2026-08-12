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
package com.google.idea.blaze.qsync.project

import com.android.tools.idea.protobuf.AbstractMessageLite
import com.google.common.annotations.VisibleForTesting
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.idea.blaze.common.vcs.VcsState
import com.google.idea.blaze.common.vcs.WorkspaceFileChange as CommonWorkspaceFileChange
import com.google.idea.blaze.common.vcs.WorkspaceFileChange.Operation
import com.google.idea.blaze.qsync.project.SnapshotProto.WorkspaceFileChange.VcsOperation
import com.google.idea.blaze.qsync.project.SnapshotProto.WorkspaceSnapshot
import com.google.idea.blaze.qsync.query.QuerySummary

/**
 * Serializes a [QuerySyncProjectSnapshot] instance to a proto message, including VcsState, BazelVersion, ProjectDefinition, and
 * ProjectStructureData.
 */
class SnapshotSerializer() {

  private val proto: SnapshotProto.Snapshot.Builder = SnapshotProto.Snapshot.newBuilder().setVersion(PROTO_VERSION)

  @VisibleForTesting
  constructor(protoVersion: Int) : this() {
    proto.setVersion(protoVersion)
  }

  @CanIgnoreReturnValue
  fun visit(snapshot: PostQuerySyncData): SnapshotSerializer {
    visitQuerySummary(snapshot.querySummary())
    return this
  }

  @CanIgnoreReturnValue
  fun visitVcsState(vcsState: VcsState?): SnapshotSerializer {
    if (vcsState != null) {
      visitVcsStateInternal(vcsState)
    }
    return this
  }

  @CanIgnoreReturnValue
  fun visitBazelVersion(bazelVersion: String?): SnapshotSerializer {
    if (bazelVersion != null) {
      proto.setBazelVersion(bazelVersion)
    }
    return this
  }

  @CanIgnoreReturnValue
  fun visit(projectDefinition: ProjectDefinition): SnapshotSerializer {
    visitProjectDefinition(projectDefinition)
    return this
  }

  @CanIgnoreReturnValue
  fun visit(projectStructureData: ProjectStructureData?): SnapshotSerializer {
    projectStructureData?.let(::visitProjectStructureData)
    return this
  }

  fun toProto(): AbstractMessageLite<*, *> = proto.build()

  private fun visitProjectDefinition(projectDefinition: ProjectDefinition) {
    proto.projectDefinitionBuilder.apply {
      addAllIncludePaths(projectDefinition.projectIncludes.map { it.toString() })
      addAllExcludePaths(projectDefinition.projectExcludes.map { it.toString() })
      setDeriveTargetsFromDirectories(projectDefinition.deriveTargetsFromDirectories)
      addAllTargetPatterns(projectDefinition.targetPatterns.map { it.toString() })
      addAllSystemExcludes(projectDefinition.systemExcludes.map { it.toString() })
      setIsAndroidWorkspace(projectDefinition.isAndroidWorkspace)
      addAllLanguageClasses(projectDefinition.languageClasses.map { it.protoValue })
      addAllTestSources(projectDefinition.testSources)
    }
  }

  private fun visitVcsStateInternal(vcsState: VcsState) {
    proto.vcsStateBuilder.setWorkspaceId(vcsState.workspaceId).setUpstreamRevision(vcsState.upstreamRevision)
    vcsState.workingSet.forEach { change -> proto.vcsStateBuilder.addWorkingSet(toProto(change)) }
    vcsState.workspaceSnapshotPath
      .map { WorkspaceSnapshot.newBuilder().setPath(it.toString()).build() }
      .ifPresent { proto.vcsStateBuilder.setWorkspaceSnapshot(it) }
  }

  private fun toProto(change: CommonWorkspaceFileChange): SnapshotProto.WorkspaceFileChange =
    SnapshotProto.WorkspaceFileChange.newBuilder()
      .setOperation(change.operation.toProto())
      .setWorkspaceRelativePath(change.workspaceRelativePath.toString())
      .build()

  private fun visitQuerySummary(summary: QuerySummary) {
    proto.setQuerySummary(summary.protoForSerializationOnly())
  }

  private fun visitProjectStructureData(projectStructureData: ProjectStructureData) {
    val structureProto = SnapshotProto.ProjectStructureData.newBuilder()
    projectStructureData.roots.forEach { root ->
      val rootProto = SnapshotProto.ProjectStructureRoot.newBuilder().setProjectStructureRootPath(root.projectStructureRootPath.toString())

      for ((pkgPath, buildPkg) in root.buildPackages) {
        val buildPkgProto = SnapshotProto.BuildPackage.newBuilder().setBuildPackagePath(pkgPath.toString()).setStamp(buildPkg.stamp)
        for (sourceSet in buildPkg.sourceSets) {
          buildPkgProto.addSourceSets(
            SnapshotProto.SourceSet.newBuilder()
              .apply {
                addAllJavaSourceFiles(sourceSet.javaSourceFiles.map { it.toString() })
                addAllNonJavaSourceFiles(sourceSet.nonJavaSourceFiles.map { it.toString() })
                setRootPath(sourceSet.rootPath.toString())
                setJavaPackage(sourceSet.javaPackage)
              }
              .build()
          )
        }
        rootProto.addBuildPackages(buildPkgProto.build())
      }
      structureProto.addRoots(rootProto.build())
    }
    structureProto.addAllActiveLanguages(projectStructureData.activeLanguages.map { it.protoValue })
    proto.setProjectStructureData(structureProto.build())
  }

  companion object {
    const val PROTO_VERSION: Int = 10

    private fun Operation.toProto(): VcsOperation =
      when (this) {
        Operation.ADD -> VcsOperation.ADD
        Operation.DELETE -> VcsOperation.DELETE
        Operation.MODIFY -> VcsOperation.MODIFY
      }
  }
}
