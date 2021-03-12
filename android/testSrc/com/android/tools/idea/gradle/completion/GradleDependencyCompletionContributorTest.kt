/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.completion

import com.android.testutils.MockitoKt
import com.android.tools.idea.imports.GMavenIndexRepository
import com.android.tools.idea.imports.MavenClassRegistry
import com.android.tools.idea.imports.MavenClassRegistryManager
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.jetbrains.android.AndroidTestCase
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import java.nio.charset.StandardCharsets

@RunsInEdt
class GradleDependencyCompletionContributorTest : AndroidTestCase() {
  @Before
  override fun setUp() {
    super.setUp()

    ApplicationManager.getApplication().replaceService(
      MavenClassRegistryManager::class.java,
      createFakeMavenClassRegistryManager(),
      myFixture.testRootDisposable
    )
  }

  @Test
  fun testBasicCompletionInGradleBuildFile_qualifiedClosure() {
    val buildFile = myFixture.addFileToProject(
      "build.gradle",
      """
        dependencies {
          implementation 'com.google.a$caret'
        }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly("com.google.android.gms:play-services-maps:17.0.0", "implementation")
  }

  @Test
  fun testBasicCompletionInGradleBuildFile_notQualifiedClosure() {
    val buildFile = myFixture.addFileToProject(
      "build.gradle",
      """
        defaultConfig {
          applicationId = "$caret"
        }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).isEmpty()
  }

  @Test
  fun testBasicCompletionInGradleKtsFile_qualifiedClosure() {
    val buildFile = myFixture.addFileToProject(
      "build.gradle.kts",
      """
        dependencies {
            api("androidx.$caret")
        }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly(
      "androidx.camera:camera-core:1.1.0-alpha03",
      "androidx.camera:camera-view:1.0.0-alpha22",
      "androidx.room:room-runtime:2.2.6"
    )
  }

  @Test
  fun testBasicCompletionInGradleKtsFile_notQualifiedClosure() {
    val buildFile = myFixture.addFileToProject(
      "build.gradle.kts",
      """
        defaultConfig {
          applicationId = "com.$caret"
        }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).isEmpty()
  }

  @Test
  fun testBasicCompletionInBuildSrc_kotlin() {
    val ktFile = myFixture.addFileToProject(
      "buildSrc/src/main/java/Dependencies.kt",
      """
        object Dependencies {
            val cameraView by lazy { "androidx.camera$caret" }
        }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(ktFile.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly(
      "androidx.camera:camera-core:1.1.0-alpha03",
      "androidx.camera:camera-view:1.0.0-alpha22"
    )
  }

  @Test
  fun testBasicCompletionInBuildSrc_java() {
    val ktFile = myFixture.addFileToProject(
      "buildSrc/src/main/java/Dependencies.java",
      """
        class Dependencies {
            public static String cameraView = "androidx.camera$caret"
        }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(ktFile.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly(
      "androidx.camera:camera-core:1.1.0-alpha03",
      "androidx.camera:camera-view:1.0.0-alpha22",
      "cameraView"
    )
  }

  private fun createFakeMavenClassRegistryManager(): MavenClassRegistryManager {
    val mockGMavenIndexRepository: GMavenIndexRepository = MockitoKt.mock()
    `when`(mockGMavenIndexRepository.loadIndexFromDisk()).thenReturn(
      """
        {
          "Index": [
            {
              "groupId": "androidx.room",
              "artifactId": "room-runtime",
              "version": "2.2.6",
              "ktxTargets": [],
              "fqcns": [
                "androidx.room.Room",
                "androidx.room.RoomDatabase",
                "androidx.room.FakeClass"
              ]
            },
            {
              "groupId": "com.google.android.gms",
              "artifactId": "play-services-maps",
              "version": "17.0.0",
              "ktxTargets": [],
              "fqcns": [
                "com.google.android.gms.maps.SupportMapFragment"
              ]
            },
            {
              "groupId": "androidx.camera",
              "artifactId": "camera-core",
              "version": "1.1.0-alpha03",
              "ktxTargets": [],
              "fqcns": [
                "androidx.camera.core.ExtendableBuilder",
                "androidx.camera.core.ImageCapture"
              ]
            },
            {
              "groupId": "androidx.camera",
              "artifactId": "camera-view",
              "version": "1.0.0-alpha22",
              "ktxTargets": [],
              "fqcns": [
                "androidx.camera.view.PreviewView"
              ]
            }
          ]
        }
      """.trimIndent().byteInputStream(StandardCharsets.UTF_8)
    )

    val mavenClassRegistry = MavenClassRegistry(mockGMavenIndexRepository)

    return MockitoKt.mock<MavenClassRegistryManager>().apply {
      `when`(getMavenClassRegistry()).thenReturn(mavenClassRegistry)
    }
  }
}