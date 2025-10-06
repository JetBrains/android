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

import com.google.common.collect.ImmutableBiMap
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.TargetPattern
import com.google.idea.blaze.common.vcs.VcsState
import com.google.idea.blaze.common.vcs.WorkspaceFileChange
import com.google.idea.blaze.qsync.query.Query
import com.google.protobuf.ExtensionRegistry
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/** Deserializes a [PostQuerySyncData] instance from an input stream.  */
class SnapshotDeserializer private constructor() {
  private val snapshot: PostQuerySyncData.Builder = PostQuerySyncData.builder()

  companion object {
    @Throws(IOException::class)
    fun readFrom(input: InputStream, context: Context<*>): PostQuerySyncData? {
      val deserializer = SnapshotDeserializer()
      val proto = SnapshotProto.Snapshot.parseFrom(input, ExtensionRegistry.getEmptyRegistry())
      if (proto.version != SnapshotSerializer.PROTO_VERSION) {
        context.output(PrintOutput.output("IDE has updated since last sync; performing full sync"))
        return null
      }
      deserializer.visitProjectDefinition(proto.projectDefinition)
      if (proto.hasVcsState()) {
        deserializer.visitVcsState(proto.vcsState)
      }
      if (!proto.getBazelVersion().isEmpty()) {
        deserializer.snapshot.setBazelVersion(Optional.of(proto.getBazelVersion()))
      }
      deserializer.visitQuerySummay(proto.querySummary)
      return deserializer.snapshot.build()
    }
  }

  private fun visitProjectDefinition(proto: SnapshotProto.ProjectDefinition) {
    snapshot.setProjectDefinition(
      ProjectDefinition(
        projectIncludes = ImmutableSet.copyOf(proto.includePathsList.map { Path.of(it) }),
        projectExcludes = ImmutableSet.copyOf(proto.excludePathsList.map { Path.of(it) }),
        deriveTargetsFromDirectories = proto.deriveTargetsFromDirectories,
        targetPatterns = ImmutableList.copyOf (proto.targetPatternsList.map {TargetPattern.parse(it)}),
        isAndroidWorkspace = proto.isAndroidWorkspace,
        languageClasses = ImmutableSet.copyOf(proto.languageClassesList.mapNotNull {QuerySyncLanguage.fromProto(it).getOrNull()}),
        testSources = ImmutableSet.copyOf(proto.testSourcesList),
        systemExcludes = ImmutableSet.copyOf(proto.systemExcludesList.map { Path.of(it) })
      )
    )
  }

  private fun visitVcsState(proto: SnapshotProto.VcsState) {
    snapshot.setVcsState(Optional.of<VcsState?>(convertVcsState(proto)))
  }

  private fun visitQuerySummay(proto: Query.Summary?) {
    snapshot.setQuerySummary(proto)
  }
}

private val OP_MAP: ImmutableBiMap<SnapshotProto.WorkspaceFileChange.VcsOperation, WorkspaceFileChange.Operation> =
  SnapshotSerializer.OP_MAP.inverse()

private fun convertVcsState(proto: SnapshotProto.VcsState): VcsState {
  return VcsState(
    proto.getWorkspaceId(),
    proto.getUpstreamRevision(),
    ImmutableSet.copyOf(
      proto.workingSetList
        .map {
          WorkspaceFileChange(
            OP_MAP.get(it.getOperation()), Path.of(it.getWorkspaceRelativePath())
          )
        }
    ),
    if (proto.hasWorkspaceSnapshot())
      Optional.of(Path.of(proto.workspaceSnapshot.getPath()))
    else
      Optional.empty())
}
