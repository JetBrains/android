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

import com.android.tools.idea.protobuf.ExtensionRegistry
import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.TargetPattern
import com.google.idea.blaze.common.vcs.VcsState
import com.google.idea.blaze.common.vcs.WorkspaceFileChange
import com.google.idea.blaze.common.vcs.WorkspaceFileChange.Operation
import com.google.idea.blaze.qsync.project.SnapshotProto.WorkspaceFileChange.VcsOperation
import com.google.idea.blaze.qsync.query.Query
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.Optional

/**
 * Deserializes project snapshot components from an input stream, including [PostQuerySyncData], [ProjectStructureData], VcsState,
 * BazelVersion, and ProjectDefinition.
 */
class SnapshotDeserializer private constructor() {
  private val syncDataBuilder = PostQuerySyncData.builder()
  private var projectStructureData: ProjectStructureData? = null
  private var projectDefinition: ProjectDefinition? = null
  private var vcsState: VcsState? = null
  private var bazelVersion: String? = null

  companion object {
    @Throws(IOException::class)
    fun readFrom(input: InputStream, context: Context<*>): SerializedProjectStructureAndQueryData? {
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
        deserializer.bazelVersion = proto.getBazelVersion()
      }
      if (proto.hasProjectStructureData()) {
        deserializer.projectStructureData = deserializer.visitProjectStructureData(proto.projectStructureData)
      }
      deserializer.visitQuerySummary(proto.querySummary)
      return SerializedProjectStructureAndQueryData(
        deserializer.syncDataBuilder.build(),
        deserializer.projectStructureData
          ?: let {
            context.output(PrintOutput.output("Incomplete sync data; performing full sync"))
            return@readFrom null
          },
        deserializer.projectDefinition ?: ProjectDefinition.EMPTY,
        deserializer.vcsState,
        deserializer.bazelVersion,
      )
    }
  }

  private fun visitProjectDefinition(proto: SnapshotProto.ProjectDefinition) {
    projectDefinition =
      ProjectDefinition(
        projectIncludes = proto.includePathsList.map { Path.of(it) }.toSet(),
        projectExcludes = proto.excludePathsList.map { Path.of(it) }.toSet(),
        deriveTargetsFromDirectories = proto.deriveTargetsFromDirectories,
        targetPatterns = proto.targetPatternsList.map { TargetPattern.parse(it) },
        isAndroidWorkspace = proto.isAndroidWorkspace,
        languageClasses = proto.languageClassesList.mapNotNull { it.toQuerySyncLanguage() }.toSet(),
        testSources = proto.testSourcesList.toSet(),
        systemExcludes = proto.systemExcludesList.map { Path.of(it) }.toSet(),
      )
  }

  private fun visitVcsState(proto: SnapshotProto.VcsState) {
    vcsState = convertVcsState(proto)
  }

  private fun visitQuerySummary(proto: Query.Summary?) {
    syncDataBuilder.setQuerySummary(proto)
  }

  private fun visitProjectStructureData(proto: SnapshotProto.ProjectStructureData): ProjectStructureData {
    val roots =
      proto.rootsList.map { rootProto ->
        ProjectStructureRoot(
          projectStructureRootPath = Path.of(rootProto.projectStructureRootPath),
          buildPackages =
            rootProto.buildPackagesList.associate { buildPkgProto ->
              val pkgPath = Path.of(buildPkgProto.buildPackagePath)
              pkgPath to
                BuildPackage(
                  path = pkgPath,
                  sourceSets =
                    buildPkgProto.sourceSetsList.map { sourceSet ->
                      SourceSet(
                        rootPath = Path.of(sourceSet.rootPath),
                        javaSourceFiles = sourceSet.javaSourceFilesList.map { Path.of(it) },
                        nonJavaSourceFiles = sourceSet.nonJavaSourceFilesList.map { Path.of(it) },
                        javaPackage = sourceSet.javaPackage,
                      )
                    },
                  stamp = 0L,
                )
            },
        )
      }

    val activeLanguages = proto.activeLanguagesList.mapNotNull { it.toQuerySyncLanguage() }.toSet()

    return ProjectStructureData.create(roots, activeLanguages)
  }
}

private fun convertVcsState(proto: SnapshotProto.VcsState): VcsState {
  return VcsState(
    proto.getWorkspaceId(),
    proto.getUpstreamRevision(),
    ImmutableSet.copyOf(
      proto.workingSetList.map { WorkspaceFileChange(it.getOperation().toOperation(), Path.of(it.getWorkspaceRelativePath())) }
    ),
    if (proto.hasWorkspaceSnapshot()) Optional.of(Path.of(proto.workspaceSnapshot.getPath())) else Optional.empty(),
  )
}

private fun VcsOperation.toOperation(): Operation =
  when (this) {
    VcsOperation.ADD -> Operation.ADD
    VcsOperation.DELETE -> Operation.DELETE
    VcsOperation.MODIFY -> Operation.MODIFY
    VcsOperation.UNSPECIFIED,
    VcsOperation.UNRECOGNIZED -> error("Unknown VcsOperation: $this")
  }
