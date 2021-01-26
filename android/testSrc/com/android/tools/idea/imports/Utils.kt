/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.mockito.Mockito.`when`
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit

/**
 * Testing infrastructure.
 */
internal fun performWithoutSync(projectRule: AndroidGradleProjectRule, action: AndroidMavenImportIntentionAction, element: PsiElement) {
  action.perform(projectRule.project, projectRule.fixture.editor, element, false)
}

internal fun performAndWaitForSyncEnd(
  projectRule: AndroidGradleProjectRule,
  invoke: () -> Unit,
) {
  val publishedResult = SettableFuture.create<ProjectSystemSyncManager.SyncResult>()
  val project = projectRule.project
  project.messageBus
    .connect(project)
    .subscribe(PROJECT_SYSTEM_SYNC_TOPIC, object : ProjectSystemSyncManager.SyncResultListener {
      override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
        publishedResult.set(result)
      }
    })

  invoke()

  val results = publishedResult.get(10, TimeUnit.SECONDS)
  Truth.assertThat(results).named("Second sync result").isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS)
}

internal fun assertBuildGradle(projectRule: AndroidGradleProjectRule, check: (String) -> Unit) {
  val buildGradle = projectRule.project.guessProjectDir()!!.findFileByRelativePath("app/build.gradle")
  val buildGradlePsi = PsiManager.getInstance(projectRule.project).findFile(buildGradle!!)
  check(buildGradlePsi!!.text)
}

internal val fakeMavenClassRegistryManager: MavenClassRegistryManager
  get() {
    val mavenClassRegistry = if (StudioFlags.ENABLE_AUTO_IMPORT.get()) {
      val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
      `when`(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
        """
          {
            "Index": [
              {
                "groupId": "androidx.palette",
                "artifactId": "palette",
                "version": "1.0.0",
                "fqcns": [
                  "androidx.palette.graphics.Palette"
                ]
              },
              {
                "groupId": "androidx.room",
                "artifactId": "room-runtime",
                "version": "2.2.6",
                "fqcns": [
                  "androidx.room.Room",
                  "androidx.room.RoomDatabase"
                ]
              },
              {
                "groupId": "androidx.recyclerview",
                "artifactId": "recyclerview",
                "version": "1.1.0",
                "fqcns": [
                  "androidx.recyclerview.widget.RecyclerView"
                ]
              }
            ]
          }
        """.trimIndent().byteInputStream(UTF_8)
      )

      MavenClassRegistryFromRepository(gMavenIndexRepositoryMock)
    }
    else {
      MavenClassRegistryFromHardcodedMap
    }

    return mock<MavenClassRegistryManager>().apply {
      `when`(getMavenClassRegistry()).thenReturn(mavenClassRegistry)
    }
  }