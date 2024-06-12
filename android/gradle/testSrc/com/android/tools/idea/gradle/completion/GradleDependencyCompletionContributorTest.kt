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
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.imports.GMavenIndexRepository
import com.android.tools.idea.imports.MavenClassRegistry
import com.android.tools.idea.imports.MavenClassRegistryManager
import com.android.tools.idea.lang.typedef.TypeDefCompletionContributor
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.kotlin.psi.KotlinReferenceProvidersService
import org.junit.Before
import org.junit.Test
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

    // The KotlinReferenceProviderService implementation in the Kotlin 1.9 compiler constructs a MockProject with an unrooted parent
    // Disposable, causing our test leak checker to complain.  Replace that service with an empty reference provider.
    // TODO(b/299068973): remove this replacement (and the extension mask below).
    myFixture.project.replaceService(
      KotlinReferenceProvidersService::class.java,
      object : KotlinReferenceProvidersService() {
        override fun getReferences(psiElement: PsiElement) = PsiReference.EMPTY_ARRAY
      },
      myFixture.testRootDisposable
    )

    // Although a reference provider returning an empty array of references is ostensibly legal (the service itself provides a default
    // implementation of that form), there is code out there that assumes that various Kotlin Psi elements (e.g. KtSimpleNameExpression)
    // have non-empty references through the use of `mainReference`.  For now the only piece of code which intersects this test is the
    // KotlinTypeDefCompletionContributor, which we don't need: so mask it out.
    val completionContributorEPs = CompletionContributor.EP.extensions
    val clazz = com.android.tools.idea.lang.typedef.KotlinTypeDefCompletionContributor::class.java
    ExtensionTestUtil.maskExtensions(CompletionContributor.EP, completionContributorEPs.filter { it.implementationClass != clazz.name },
                                     myFixture.testRootDisposable)
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

  @Test
  fun testBasicCompletionInLibsVersionsToml() {
    val tomlFile = myFixture.addFileToProject(
      "gradle/libs.versions.toml",
      """
        [libraries]
        camera = "androidx.$caret"
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly(
      "androidx.camera:camera-core:1.1.0-alpha03",
      "androidx.camera:camera-view:1.0.0-alpha22",
      "androidx.room:room-runtime:2.2.6"
    )
  }

  @Test
  fun testBasicCompletionInLibsVersionsTomlModuleKey() {
    val tomlFile = myFixture.addFileToProject(
      "gradle/libs.versions.toml",
      """
        [libraries]
        camera = { module = "androidx.$caret" }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly(
      "androidx.camera:camera-core",
      "androidx.camera:camera-view",
      "androidx.room:room-runtime"
    )
  }

  @Test
  fun testBasicCompletionInLibsVersionsTomlGroupKey() {
    val tomlFile = myFixture.addFileToProject(
      "gradle/libs.versions.toml",
      """
        [libraries]
        camera = { group = "androidx.$caret" }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly(
      "androidx.camera",
      "androidx.room"
    )
  }

  @Test
  fun testBasicCompletionInLibsVersionsTomlNameKey() {
    val tomlFile = myFixture.addFileToProject(
      "gradle/libs.versions.toml",
      """
        [libraries]
        camera = { name = "r$caret" }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly(
      "camera-core",
      "camera-view",
      "play-services-maps",
      "room-runtime"
    )
  }

  @Test
  fun testBasicCompletionInLibsVersionsTomlVersionKey() {
    val tomlFile = myFixture.addFileToProject(
      "gradle/libs.versions.toml",
      """
        [libraries]
        camera = { version = "1$caret" }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly(
      "1.0.0-alpha22", "1.1.0-alpha03", "17.0.0",
    )
  }

  @Test
  fun testBasicCompletionInLibsVersionsTomlOutsideLibraries() {
    val tomlFile = myFixture.addFileToProject(
      "gradle/libs.versions.toml",
      """
        [versions]
        camera = "$caret"
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).isEmpty()
  }

  private fun createFakeMavenClassRegistryManager(): MavenClassRegistryManager {
    val mockGMavenIndexRepository: GMavenIndexRepository = MockitoKt.mock()
    whenever(mockGMavenIndexRepository.loadIndexFromDisk()).thenReturn(
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
      whenever(getMavenClassRegistry()).thenReturn(mavenClassRegistry)
    }
  }
}