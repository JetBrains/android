/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.stubs.level2.AndroidLibraryStubBuilder
import com.android.ide.common.gradle.model.stubs.level2.IdeDependenciesStub
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModelHandler
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.templates.RepositoryUrlManager
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.Module
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import junit.framework.AssertionFailedError
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import java.io.File
import java.util.Collections

/**
 * These unit tests use a local test maven repo "project-system-gradle/testData/repoIndex". To see
 * what dependencies are available to test with, go to that folder and look at the group-indices.
 *
 * TODO:
 * Some cases of analyzeDependencyCompatibility cannot be tested without AndroidGradleTestCase because it relies on real gradle models.
 * Because of this tests for getAvailableDependency with matching platform support libs reside in [GradleModuleSystemIntegrationTest].
 * Once we truly move dependency versioning logic into GradleDependencyManager the tests can be implemented there.
 */
class GradleModuleSystemTest : AndroidTestCase() {
  private var _gradleDependencyManager: GradleDependencyManager? = null
  private var _gradleModuleSystem: GradleModuleSystem? = null
  private var androidProject: IdeAndroidProject? = null
  private val gradleDependencyManager get() = _gradleDependencyManager!!
  private val gradleModuleSystem get() = _gradleModuleSystem!!

  private val mavenRepository = object : GoogleMavenRepository(File(AndroidTestBase.getTestDataPath(),
      "../../project-system-gradle/testData/repoIndex"), cacheExpiryHours = Int.MAX_VALUE, useNetwork = false) {
    override fun readUrlData(url: String, timeout: Int): ByteArray? = throw AssertionFailedError("shouldn't try to read!")

    override fun error(throwable: Throwable, message: String?) {}
  }

  private val repoUrlManager = RepositoryUrlManager(mavenRepository, mavenRepository, false)

  private val library1ModuleName = "library1"
  private val library1Path = AndroidTestCase.getAdditionalModulePath(library1ModuleName)

  override fun configureAdditionalModules(projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
                                          modules: List<AndroidTestCase.MyAdditionalModuleData>) {
    addModuleWithAndroidFacet(projectBuilder, modules, library1ModuleName, PROJECT_TYPE_LIBRARY)
  }

  override fun setUp() {
    super.setUp()
    _gradleDependencyManager = IdeComponents(project).mockProjectService(GradleDependencyManager::class.java)
    _gradleModuleSystem = GradleModuleSystem(myModule, ProjectBuildModelHandler(project), repoUrlManager)
    assertThat(gradleModuleSystem.getResolvedDependentLibraries()).isEmpty()
  }

  override fun tearDown() {
    try {
      _gradleDependencyManager = null
      _gradleModuleSystem = null
      androidProject = null
    }
    finally {
      super.tearDown()
    }
  }

  fun testRegisterDependency() {
    val coordinate = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    assertThat(gradleModuleSystem.canRegisterDependency(DependencyType.IMPLEMENTATION).isSupported()).isTrue()
    gradleModuleSystem.registerDependency(coordinate)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependenciesWithoutSync(myModule, listOf(coordinate))
  }

  fun testNoAndroidModuleModel() {
    // The AndroidModuleModel shouldn't be created when running from an IdeaTestCase.
    assertThat(AndroidModuleModel.get(myModule)).isNull()
    assertThat(gradleModuleSystem.getResolvedDependency(GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+"))).isNull()
  }

  fun testGetAvailableDependency_fallbackToPreview() {
    // In the test repo NAVIGATION only has a preview version 0.0.1-alpha1
    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(toGradleCoordinate(GoogleMavenArtifactId.NAVIGATION)))

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(toGradleCoordinate(GoogleMavenArtifactId.NAVIGATION, "0.0.1-alpha1"))
  }

  fun testGetAvailableDependency_returnsLatestStable() {
    // In the test repo CONSTRAINT_LAYOUT has a stable version of 1.0.2 and a beta version of 1.1.0-beta3
    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(toGradleCoordinate(GoogleMavenArtifactId.CONSTRAINT_LAYOUT)))

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(toGradleCoordinate(GoogleMavenArtifactId.CONSTRAINT_LAYOUT, "1.0.2"))
  }

  fun testGetAvailableDependency_returnsNullWhenNoneMatches() {
    // The test repo does not have any version of PLAY_SERVICES_ADS.
    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(toGradleCoordinate(GoogleMavenArtifactId.PLAY_SERVICES_ADS)))

    assertThat(warning).isEmpty()
    assertThat(missing).containsExactly(toGradleCoordinate(GoogleMavenArtifactId.PLAY_SERVICES_ADS))
    assertThat(found).isEmpty()
  }

  fun testAddSupportDependencyWithMatchInSubModule() {
    val libraryModule = getAdditionalModuleByName(library1ModuleName)!!
    installDependencies(libraryModule, Collections.singletonList("com.android.support:appcompat-v7:23.1.1"))
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          api project(':$library1ModuleName')
      }""".trimIndent())

    myFixture.addFileToProject("$library1Path/build.gradle", """
      dependencies {
          implementation 'com.android.support:appcompat-v7:+'
      }""".trimIndent())

    // Check that the version is picked up from one of the sub modules
    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(toGradleCoordinate(GoogleMavenArtifactId.RECYCLERVIEW_V7)))

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(toGradleCoordinate(GoogleMavenArtifactId.RECYCLERVIEW_V7, "23.1.1"))
  }

  fun testAddSupportDependencyWithMatchInAppModule() {
    installDependencies(myModule, listOf("com.android.support:recyclerview-v7:22.2.1"))
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          api project(':$library1ModuleName')
          implementation 'com.android.support:appcompat-v7:22.2.1'
      }""".trimIndent())

    // Check that the version is picked up from the parent module:
    val module1 = getAdditionalModuleByName(library1ModuleName)!!
    val gradleModuleSystem = GradleModuleSystem(module1, ProjectBuildModelHandler(project), repoUrlManager)

    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(toGradleCoordinate(GoogleMavenArtifactId.RECYCLERVIEW_V7)))

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(toGradleCoordinate(GoogleMavenArtifactId.RECYCLERVIEW_V7, "22.2.1"))
  }

  fun testProjectWithIncompatibleDependencies() {
    installDependencies(myModule, listOf("androidx.appcompat:appcompat:2.0.0", "androidx.appcompat:appcompat:1.2.0"))
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          implementation 'androidx.appcompat:appcompat:2.0.0'
          implementation 'androidx.appcompat:appcompat:1.2.0'
      }""".trimIndent())

    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("androidx.fragment", "fragment", "+")))

    assertThat(warning).isEqualTo("""
      Inconsistencies in the existing project dependencies found.
      Version incompatibility between:
      -   androidx.appcompat:appcompat:1.2.0
      and:
      -   androidx.appcompat:appcompat:2.0.0
    """.trimIndent())
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate("androidx.fragment", "fragment", "2.0.0"))
  }

  fun testProjectWithIncompatibleIndirectDependencies() {
    installDependencies(myModule, listOf("androidx.appcompat:appcompat:2.0.0", "androidx.core:core:1.0.0"))
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          implementation 'androidx.appcompat:appcompat:2.0.0'
          implementation 'androidx.core:core:1.0.0'
      }""".trimIndent())

    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("androidx.fragment", "fragment", "+")))

    assertThat(warning).isEqualTo("""
      Inconsistencies in the existing project dependencies found.
      Version incompatibility between:
      -   androidx.core:core:1.0.0
      and:
      -   androidx.appcompat:appcompat:2.0.0

      With the dependency:
      -   androidx.core:core:1.0.0
      versus:
      -   androidx.core:core:2.0.0
    """.trimIndent())
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate("androidx.fragment", "fragment", "2.0.0"))
  }

  fun testTwoArtifactsWithConflictingDependencies() {
    installDependencies(myModule, Collections.singletonList("com.google.android.material:material:1.3.0"))
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          implementation 'com.google.android.material:material:1.3.0'
      }""".trimIndent())

    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("com.acme.pie", "pie", "+")))

    assertThat(warning).isEqualTo("""
      Version incompatibility between:
      -   com.acme.pie:pie:1.0.0-alpha1
      and:
      -   com.google.android.material:material:1.3.0

      With the dependency:
      -   androidx.core:core:2.0.0
      versus:
      -   androidx.core:core:1.0.0
    """.trimIndent())
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate("com.acme.pie", "pie", "1.0.0-alpha1"))
  }

  fun testTwoArtifactsWithConflictingDependenciesInDifferentModules() {
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          api project(':$library1ModuleName')
      }""".trimIndent())

    installDependencies(getAdditionalModuleByName(library1ModuleName)!!, listOf("com.google.android.material:material:1.3.0"))
    myFixture.addFileToProject("$library1Path/build.gradle", """
      dependencies {
          implementation 'com.google.android.material:material:1.3.0'
      }""".trimIndent())

    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("com.acme.pie", "pie", "+")))

    assertThat(warning).isEqualTo("""
      Version incompatibility between:
      -   com.acme.pie:pie:1.0.0-alpha1 in module app
      and:
      -   com.google.android.material:material:1.3.0 in module library1

      With the dependency:
      -   androidx.core:core:2.0.0
      versus:
      -   androidx.core:core:1.0.0
    """.trimIndent())
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate("com.acme.pie", "pie", "1.0.0-alpha1"))
  }

  fun testPreviewsAreAcceptedIfNoStableExists() {
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          implementation 'androidx.appcompat:appcompat:2.0.0'
      }""".trimIndent())

    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("com.acme.pie", "pie", "+")))

    assertThat(warning).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate.parseCoordinateString("com.acme.pie:pie:1.0.0-alpha1"))
    assertThat(missing).isEmpty()
  }

  fun testNewestSameMajorIsChosenFromExistingIndirectDependency() {
    installDependencies(myModule, listOf("androidx.appcompat:appcompat:1.0.0"))
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          implementation 'androidx.appcompat:appcompat:1.0.0'
      }""".trimIndent())

    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("androidx.fragment", "fragment", "+")))

    assertThat(warning).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate.parseCoordinateString("androidx.fragment:fragment:1.2.0"))
    assertThat(missing).isEmpty()
  }

  fun testAddingImcompatibleTestDependenciesFromMultipleSources() {
    // We are adding 2 dependencies that have conflicting test dependencies.
    // We should ignore test dependencies with analyzing compatibility issues.
    // In this case recyclerview and material have conflicting dependencies on mockito,
    // recyclerview on mockito-core:2.19.0 and material on mockito-core:1.9.5
    installDependencies(myModule, listOf("androidx.recyclerview:recyclerview:1.2.0"))
    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("com.google.android.material", "material", "+")))

    assertThat(warning).isEmpty()
    assertThat(found).containsExactly(
      GradleCoordinate.parseCoordinateString("com.google.android.material:material:1.3.0"))
    assertThat(missing).isEmpty()
  }

  fun testAddingKotlinStdlibDependenciesFromMultipleSources() {
    // We are adding 2 kotlin dependencies which depend on different kotlin stdlib
    // versions: 1.2.50 and 1.3.0. Make sure the dependencies can be added without errors.
    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("androidx.core", "core-ktx", "1.0.0"),
             GradleCoordinate("androidx.navigation", "navigation-runtime-ktx", "2.0.0")))

    assertThat(warning).isEmpty()
    assertThat(found).containsExactly(
      GradleCoordinate.parseCoordinateString("androidx.core:core-ktx:1.0.0"),
      GradleCoordinate.parseCoordinateString("androidx.navigation:navigation-runtime-ktx:2.0.0"))
    assertThat(missing).isEmpty()
  }

  private fun toGradleCoordinate(id: GoogleMavenArtifactId, version: String = "+"): GradleCoordinate {
    return GradleCoordinate(id.mavenGroupId, id.mavenArtifactId, version)
  }

  /**
   * Setup a module with some existing dependencies.
   *
   * Disclaimer: This method has side effects. It will override the
   * AndroidModuleModel and IdeAndroidProject to make it look like the specified
   * [artifacts] are in fact dependencies of the specified [module].
   * Use with care.
   */
  private fun installDependencies(module: Module, artifacts: List<String>) {
    val model = AndroidModuleModel.get(module) ?: createFakeModel(module)
    val dependencies = model.selectedMainCompileLevel2Dependencies as IdeDependenciesStub
    dependencies.androidLibraries.clear()
    artifacts.forEach {
      artifact -> dependencies.addAndroidLibrary(AndroidLibraryStubBuilder().apply { artifactAddress = artifact }.build())
    }
  }

  /**
   * Create a fake {@link AndroidModuleModel}.
   *
   * Disclaimer: this method has side effects. An IdeAndroidProject will be
   * created if it hasn't already been.
   */
  private fun createFakeModel(module: Module): AndroidModuleModel {
    val project = androidProject ?: createAndroidProject()
    val ideDependencies = IdeDependenciesStub()
    val model = mock(AndroidModuleModel::class.java)
    `when`(model.androidProject).thenReturn(project)
    `when`(model.selectedMainCompileLevel2Dependencies).thenReturn(ideDependencies)
    val facet = AndroidFacet.getInstance(module)!!
    facet.configuration.model = model
    return model
  }

  private fun createAndroidProject(): IdeAndroidProject {
    androidProject = mock(IdeAndroidProject::class.java)
    return androidProject!!
  }
}
