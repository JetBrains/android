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
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.project.Project
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
  assertThat(results).named("Second sync result").isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS)
}

internal fun checkBuildGradle(project: Project, check: (String) -> Boolean): Boolean {
  val buildGradle = project.guessProjectDir()!!.findFileByRelativePath("app/build.gradle")
  val buildGradlePsi = PsiManager.getInstance(project).findFile(buildGradle!!)

  return check(buildGradlePsi!!.text)
}

internal fun assertBuildGradle(project: Project, check: (String) -> Boolean) {
  assertThat(checkBuildGradle(project, check)).isEqualTo(true)
}

internal val fakeMavenClassRegistryManager: MavenClassRegistryManager
  get() {
    val mavenClassRegistry = if (StudioFlags.ENABLE_SUGGESTED_IMPORT.get()) {
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
                  "androidx.palette.graphics.Palette",
                  "androidx.palette.graphics.FakeClass"
                ]
              },
              {
                "groupId": "androidx.room",
                "artifactId": "room-runtime",
                "version": "2.2.6",
                "fqcns": [
                  "androidx.room.Room",
                  "androidx.room.RoomDatabase",
                  "androidx.room.FakeClass"
                ]
              },
              {
                "groupId": "androidx.recyclerview",
                "artifactId": "recyclerview",
                "version": "1.1.0",
                "fqcns": [
                  "androidx.recyclerview.widget.RecyclerView"
                ]
              },
              {
                "groupId": "com.google.android.gms",
                "artifactId": "play-services-maps",
                "version": "17.0.0",
                "fqcns": [
                  "com.google.android.gms.maps.SupportMapFragment"
                ]
              },
              {
                "groupId": "androidx.camera",
                "artifactId": "camera-core",
                "version": "1.1.0-alpha03",
                "fqcns": [
                  "androidx.camera.core.ExtendableBuilder",
                  "androidx.camera.core.ImageCapture"
                ]
              },
              {
                "groupId": "androidx.camera",
                "artifactId": "camera-view",
                "version": "1.0.0-alpha22",
                "fqcns": [
                  "androidx.camera.view.PreviewView"
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