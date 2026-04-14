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
import com.google.idea.blaze.common.vcs.WorkspaceFileChange.Operation
import com.google.idea.blaze.qsync.project.SnapshotProto.WorkspaceFileChange.VcsOperation
import com.google.idea.blaze.qsync.project.SnapshotProto.WorkspaceSnapshot
import com.google.idea.blaze.qsync.query.QuerySummary

/** Serializes a [PostQuerySyncData] instance to a proto message. */
class SnapshotSerializer() {

  private val proto: SnapshotProto.Snapshot.Builder = SnapshotProto.Snapshot.newBuilder().setVersion(PROTO_VERSION)

  @VisibleForTesting
  constructor(protoVersion: Int) : this() {
    proto.setVersion(protoVersion)
  }

  @CanIgnoreReturnValue
  fun visit(snapshot: PostQuerySyncData): SnapshotSerializer {
    visitProjectDefinition(snapshot.projectDefinition())
    snapshot.vcsState().ifPresent(::visitVcsState)
    visitQuerySummary(snapshot.querySummary())
    visitBazelVersion(snapshot.bazelVersion().orElse(null))
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

  private fun visitVcsState(vcsState: VcsState) {
    proto.vcsStateBuilder.setWorkspaceId(vcsState.workspaceId).setUpstreamRevision(vcsState.upstreamRevision)
    vcsState.workingSet.forEach { change -> proto.vcsStateBuilder.addWorkingSet(toProto(change)) }
    vcsState.workspaceSnapshotPath
      .map { WorkspaceSnapshot.newBuilder().setPath(it.toString()).build() }
      .ifPresent { proto.vcsStateBuilder.setWorkspaceSnapshot(it) }
  }

  private fun toProto(change: com.google.idea.blaze.common.vcs.WorkspaceFileChange): SnapshotProto.WorkspaceFileChange =
    SnapshotProto.WorkspaceFileChange.newBuilder()
      .setOperation(change.operation.toProto())
      .setWorkspaceRelativePath(change.workspaceRelativePath.toString())
      .build()

  private fun visitQuerySummary(summary: QuerySummary) {
    proto.setQuerySummary(summary.protoForSerializationOnly())
  }

  private fun visitBazelVersion(value: String?) {
    if (value != null) {
      proto.setBazelVersion(value)
    }
  }

  private fun visitProjectStructureData(projectStructureData: ProjectStructureData) {
    val structureProto = SnapshotProto.ProjectStructureData.newBuilder()
    projectStructureData.packageSourceSets.forEach { (path, sourceSet) ->
      structureProto.addPackageSourceSets(
        SnapshotProto.SourceSet.newBuilder()
          .apply {
            setWorkspaceRelativePath(path.toString())
            addAllJavaSourceFiles(sourceSet.javaSourceFiles.map { it.toString() })
            addAllNonJavaSourceFiles(sourceSet.nonJavaSourceFiles.map { it.toString() })
          }
          .build()
      )
    }
    structureProto.addAllActiveLanguages(projectStructureData.activeLanguages.map { it.protoValue })
    proto.setProjectStructureData(structureProto.build())
  }

  companion object {
    const val PROTO_VERSION: Int = 4

    private fun Operation.toProto(): VcsOperation =
      when (this) {
        Operation.ADD -> VcsOperation.ADD
        Operation.DELETE -> VcsOperation.DELETE
        Operation.MODIFY -> VcsOperation.MODIFY
      }
  }
}
