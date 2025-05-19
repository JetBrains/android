/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync

import com.google.common.io.ByteSource
import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.base.bazel.BuildSystem
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.project.LanguageClassProto
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import java.nio.file.Path
import java.util.Optional
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SnapshotHolderTest {
  @Test
  fun `notifies project structure changed`() {
    val holder = SnapshotHolder()
    holder.setCurrent(
      BlazeContext.create(),
      readonlyQuerySyncProjectStub,
      QuerySyncProjectSnapshot.EMPTY
    )

    var notified = false
    holder.addListener { _, _, _ -> notified = true }
    holder.setCurrent(
      BlazeContext.create(),
      readonlyQuerySyncProjectStub,
      QuerySyncProjectSnapshot.EMPTY.toBuilder()
        // Change something in the project.
        .project(ProjectProto.Project.newBuilder().addActiveLanguages(LanguageClassProto.LanguageClass.LANGUAGE_CLASS_JVM).build()).build()
    )
    assertThat(notified).isTrue()
  }

  @Test
  fun `does not notify if project structure NOT changed`() {
    fun project() = ProjectProto.Project.newBuilder().addActiveLanguages(LanguageClassProto.LanguageClass.LANGUAGE_CLASS_JVM).build()

    val holder = SnapshotHolder()
    holder.setCurrent(
      BlazeContext.create(),
      readonlyQuerySyncProjectStub,
      QuerySyncProjectSnapshot.EMPTY.toBuilder().project(project()).build()
    )

    var notified = false
    holder.addListener { _, _, _ -> notified = true }
    holder.setCurrent(
      BlazeContext.create(),
      readonlyQuerySyncProjectStub,
      QuerySyncProjectSnapshot.EMPTY.toBuilder()
        .queryData(PostQuerySyncData.EMPTY.toBuilder().setBazelVersion(Optional.of("1.2.3")).build())
        .artifactState(ArtifactTracker.State.forJavaLabels(Label.of("//a/b/c:d")))
        .project(project())
        .build()
    )
    assertThat(notified).isFalse()
  }
}

private val readonlyQuerySyncProjectStub = object : ReadonlyQuerySyncProject {
  private fun notExpected(): Nothing = throw AssertionError("not expected")
  override val buildSystem: BuildSystem get() = notExpected()
  override val projectDefinition: ProjectDefinition get() = notExpected()
  override val projectViewSet: ProjectViewSet get() = notExpected()
  override val workspaceRoot: WorkspaceRoot get() = notExpected()
  override val projectPathResolver: ProjectPath.Resolver get() = notExpected()
  override val projectData: QuerySyncProjectData get() = notExpected()
  override fun getWorkingSet(create: BlazeContext): Set<Path> = notExpected()
  override fun dependsOnAnyOf_DO_NOT_USE_BROKEN(target: Label, deps: Set<Label>): Boolean = notExpected()
  override fun containsPath(absolutePath: Path): Boolean = notExpected()
  override fun explicitlyExcludesPath(absolutePath: Path): Boolean = notExpected()
  override fun getBugreportFiles(): Map<String, ByteSource> = notExpected()
}
