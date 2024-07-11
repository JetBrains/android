/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.rendering

import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState.GRADLE_BUILD_TOPIC
import com.android.tools.idea.gradle.project.sync.GRADLE_SYNC_TOPIC
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.Context
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.replaceContent
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.buildAndWait
import com.google.common.truth.Truth.assertThat
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.ProgressBuildEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.writeText
import com.intellij.psi.PsiManager
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class BuildListenerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  private val collector = StringBuilder()
  private val listener = TestListener(collector)

  @Test
  fun `open subscribe no-build`() {
    val preparedTestProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedTestProject.runTest() {
      setupTestListener(buildTargetReference)

      assertThat(collectedEvents()).isEqualTo(
        """
        * syncStarted
        * firstSourceRootsAdded
        * syncSucceeded
        * setupBuildListener
        ->startedListening
      """.trimIndent()
      )
    }
  }

  @Test
  fun `re-open subscribe-at-opening no-build`() {
    val preparedTestProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedTestProject.runTest {
      // Do nothing.
    }

    collector.clear()
    preparedTestProject.runTest(onFirstContent = { project ->
      setupTestListener(
        buildTargetReferenceFromFile(project, preparedTestProject, "app/src/main/java/google/simpleapplication/MyActivity.java")
      )
    }) {
      assertThat(collectedEvents()).isEqualTo(
        """
        * firstSourceRootsAdded
        * setupBuildListener
        * syncSkipped
        ->startedListening
      """.trimIndent() // Note that `->startedListening` comes only after `* syncSkipped`.
      )
    }
  }

  @Test
  fun `open subscribe build`() {
    val preparedTestProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedTestProject.runTest {
      setupTestListener(buildTargetReference)
      project.buildAndWait { it.assemble() }

      assertThat(collectedEvents()).isEqualTo(
        """
        * syncStarted
        * firstSourceRootsAdded
        * syncSucceeded
        * setupBuildListener
        ->startedListening
        * buildStarted
        ->buildStarted
        * buildFinished
        ->buildSucceeded
      """.trimIndent()
      )
    }
  }

  @Test
  fun `open subscribe-while-building`() {
    val preparedTestProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedTestProject.runTest {
      project.buildAndWait(
        buildStarted = { setupTestListener(
          buildTargetReferenceFromFile(project, preparedTestProject, "app/src/main/java/google/simpleapplication/MyActivity.java")
        )}
      ) { it.assemble() }

      assertThat(collectedEvents()).isEqualTo(
        """
        * syncStarted
        * firstSourceRootsAdded
        * syncSucceeded
        * buildStarted
        * setupBuildListener
        ->startedListening
        * buildFinished
        ->buildSucceeded
      """.trimIndent() // Note the absence of `->buildStarted`.
      )
    }
  }

  @Test
  fun `open build subscribe-while-building`() {
    val preparedTestProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedTestProject.runTest {
      project.buildAndWait { it.assemble() }
      project.buildAndWait(
        buildStarted = { setupTestListener(
          buildTargetReferenceFromFile(project, preparedTestProject, "app/src/main/java/google/simpleapplication/MyActivity.java")
        )}
      ) { it.assemble() }

      assertThat(collectedEvents()).isEqualTo(
        """
        * syncStarted
        * firstSourceRootsAdded
        * syncSucceeded
        * buildStarted
        * buildFinished
        * buildStarted
        * setupBuildListener
        ->buildStarted
        ->buildSucceeded
        ->startedListening
        * buildFinished
        ->buildSucceeded
      """.trimIndent() // Note the artificially nested builds and `->startedListening` after the nested one.
      )
    }
  }

  @Test
  fun `open build-failed subscribe-while-building`() {
    val preparedTestProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedTestProject.runTest {
      withBrokenBuild {
        project.buildAndWait { it.assemble() }
      }
      project.buildAndWait(
        buildStarted = { setupTestListener(
          buildTargetReferenceFromFile(project, preparedTestProject, "app/src/main/java/google/simpleapplication/MyActivity.java")
        )}
      ) { it.assemble() }

      assertThat(collectedEvents()).isEqualTo(
        """
        * syncStarted
        * firstSourceRootsAdded
        * syncSucceeded
        * buildStarted
        * buildFinished
        * buildStarted
        * setupBuildListener
        ->startedListening
        * buildFinished
        ->buildSucceeded
      """.trimIndent() // Note an incomplete build after `->startedListening`.
      )
    }
  }

  @Test
  fun `open build subscribe`() {
    val preparedTestProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedTestProject.runTest {
      project.buildAndWait { it.assemble() }
      setupTestListener(buildTargetReference)

      assertThat(collectedEvents()).isEqualTo(
        """
        * syncStarted
        * firstSourceRootsAdded
        * syncSucceeded
        * buildStarted
        * buildFinished
        * setupBuildListener
        ->buildStarted
        ->buildSucceeded
        ->startedListening
      """.trimIndent()
      )
    }
  }

  @Test
  fun `open build subscribe build`() {
    val preparedTestProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedTestProject.runTest {

      project.buildAndWait { it.assemble() }
      setupTestListener(buildTargetReference)
      project.buildAndWait { it.assemble() }

      assertThat(collectedEvents()).isEqualTo(
        """
        * syncStarted
        * firstSourceRootsAdded
        * syncSucceeded
        * buildStarted
        * buildFinished
        * setupBuildListener
        ->buildStarted
        ->buildSucceeded
        ->startedListening
        * buildStarted
        ->buildStarted
        * buildFinished
        ->buildSucceeded
      """.trimIndent()
      )
    }
  }

  @Test
  fun `open clean subscribe build`() {
    val preparedTestProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedTestProject.runTest {

      project.buildAndWait { it.cleanProject() }
      setupTestListener(buildTargetReference)
      project.buildAndWait { it.assemble() }

      assertThat(collectedEvents()).isEqualTo(
        """
        * syncStarted
        * firstSourceRootsAdded
        * syncSucceeded
        * buildStarted
        * buildFinished
        * setupBuildListener
        ->buildStarted
        ->buildSucceeded
        ->startedListening
        * buildStarted
        ->buildStarted
        * buildFinished
        ->buildSucceeded
      """.trimIndent() // TODO: Note the incorrectly delivered `->buildSucceeded` before `->startedListening`.
      )
    }
  }

  @Test
  fun `open build subscribe build-failed`() {
    val preparedTestProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedTestProject.runTest {

      project.buildAndWait { it.assemble() }
      setupTestListener(buildTargetReference)
      withBrokenBuild {
        project.buildAndWait { it.assemble() }
      }

      assertThat(collectedEvents()).isEqualTo(
        """
        * syncStarted
        * firstSourceRootsAdded
        * syncSucceeded
        * buildStarted
        * buildFinished
        * setupBuildListener
        ->buildStarted
        ->buildSucceeded
        ->startedListening
        * buildStarted
        ->buildStarted
        * buildFinished
        ->buildFailed
      """.trimIndent()
      )
    }
  }

  private fun collectedEvents() = collector.toString().trim()

  class TestListener(private val collector: StringBuilder) : BuildListener {
    override fun startedListening() {
      collector.appendLine("->startedListening")
    }

    override fun buildSucceeded() {
      collector.appendLine("->buildSucceeded")
    }

    override fun buildFailed() {
      collector.appendLine("->buildFailed")
    }

    override fun buildStarted() {
      collector.appendLine("->buildStarted")
    }

    override fun buildCleaned() {
      collector.appendLine("->buildCleaned")
    }

    override fun toString(): String {
      return collector.toString().trim()
    }
  }

  private val Context.buildTargetReference get() = BuildTargetReference.gradleOnly(this.fixture.module)

  private fun buildTargetReferenceFromFile(
    project: Project,
    preparedTestProject: PreparedTestProject,
    file: String
  ): BuildTargetReference = runReadAction {
    BuildTargetReference.from(
      PsiManager.getInstance(project)
        .findFile(
          VfsUtil.findFileByIoFile(
            preparedTestProject.root.resolve(file),
            true
          )!!
        )!!

    )!!
  }

  fun log(event: String) {
    collector.appendLine("* $event")
  }

  private fun setupTestListener(buildTargetReference: BuildTargetReference) {
    log("setupBuildListener")
    setupBuildListener(buildTargetReference, listener, buildTargetReference.project)
  }

  private fun <T> Context.withBrokenBuild(body: () -> T) {
    val buildFile = runWriteAction {VfsUtil.findFileByIoFile(this.projectRoot.resolve("app/build.gradle"), true)!!}
    val oldContent = buildFile.contentsToByteArray()
    runWriteAction {
      buildFile.writeText("***")
    }
    try {
      body()
    } finally {
      runWriteAction {
        buildFile.setBinaryContent(oldContent)
      }
    }
  }

  private fun <T> PreparedTestProject.runTest(
    onFirstContent: (Project) -> Unit = {},
    body: Context.(Project) -> T
  ) {
    var firstSyncStarted = false
    val syncListener = object : GradleSyncListenerWithRoot {
      override fun syncStarted(project: Project, rootProjectPath: String) {
        log("syncStarted")
        if (firstSyncStarted) return
        firstSyncStarted = true
      }

      override fun syncSucceeded(project: Project, rootProjectPath: String) = log("syncSucceeded")
      override fun syncSkipped(project: Project) = log("syncSkipped")
      override fun syncCancelled(project: Project, rootProjectPath: String) = log("syncCancelled")
      override fun syncFailed(project: Project, errorMessage: String, rootProjectPath: String) = log("syncFailed")
    }
    val buildListener = object : GradleBuildListener {
      override fun buildStarted(context: BuildContext) {
        log("buildStarted")
      }

      override fun buildFinished(status: BuildStatus, context: BuildContext) = log("buildFinished")
    }
    val rootListener = object : ModuleRootListener {
      private var seenSources = false
      override fun rootsChanged(event: ModuleRootEvent) {
        if (!seenSources) {
          if (ProjectRootManagerEx.getInstance(event.project).orderEntries().allSourceRoots.isNotEmpty()) {
            seenSources = true
            log("firstSourceRootsAdded")
            onFirstContent(event.project)
          }
        }
      }
    }
    this.open(updateOptions = {
      it.copy(
        subscribe = { bus ->
          bus.subscribe(GRADLE_SYNC_TOPIC, syncListener)
          bus.subscribe(GRADLE_BUILD_TOPIC, buildListener)
          bus.subscribe(ModuleRootListener.TOPIC, rootListener)
        },
      )
    }) { project ->
      body(project)
    }
  }
}
