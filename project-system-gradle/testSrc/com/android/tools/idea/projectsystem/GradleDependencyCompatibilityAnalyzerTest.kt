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
package com.android.tools.idea.projectsystem

import com.android.AndroidProjectTypes
import com.android.SdkConstants
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.stubs.l2AndroidLibrary
import com.android.ide.common.gradle.model.stubs.level2.IdeDependenciesStubBuilder
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GradleCoordinate
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.gradle.structure.configurables.RepositorySearchFactory
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepositorySearch
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepositorySearchService
import com.android.tools.idea.gradle.structure.model.repositories.search.LocalMavenRepository
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.gradle.CHECK_DIRECT_GRADLE_DEPENDENCIES
import com.android.tools.idea.projectsystem.gradle.GradleDependencyCompatibilityAnalyzer
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.projectsystem.gradle.ProjectBuildModelHandler
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
import java.nio.file.Paths
import java.util.Collections
import java.util.concurrent.TimeUnit

class GradleDependencyCompatibilityAnalyzerTest: AndroidTestCase() {

  /**
   * This test is using a fake Maven Repository where we control the available artifacts and versions.
   */
  private val mavenRepository = object : GoogleMavenRepository(
    cacheDir = Paths.get(AndroidTestBase.getTestDataPath()).resolve("../../project-system-gradle/testData/repoIndex").normalize(),
    cacheExpiryHours = Int.MAX_VALUE,
    useNetwork = false
  ) {
    override fun readUrlData(url: String, timeout: Int): ByteArray? = throw AssertionFailedError("shouldn't try to read!")
    override fun error(throwable: Throwable, message: String?) {}
  }

  private val repoUrlManager = RepositoryUrlManager(
    googleMavenRepository = mavenRepository,
    cachedGoogleMavenRepository = mavenRepository,
    forceRepositoryChecksInTests = false,
    useEmbeddedStudioRepo = false
  )

  private val searchService = ArtifactRepositorySearch(listOf(LocalMavenRepository(mavenRepository.cacheDir!!.toFile(), "local")))

  private lateinit var analyzer: GradleDependencyCompatibilityAnalyzer
  private var androidProject: IdeAndroidProject? = null

  private val library1ModuleName = "library1"
  private val library1Path = getAdditionalModulePath(library1ModuleName)

  override fun configureAdditionalModules(projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
                                          modules: List<MyAdditionalModuleData>) {
    addModuleWithAndroidFacet(projectBuilder, modules, library1ModuleName, AndroidProjectTypes.PROJECT_TYPE_LIBRARY)
  }

  override fun setUp() {
    super.setUp()
    val repositorySearchFactory = object: RepositorySearchFactory {
      override fun create(repositories: Collection<ArtifactRepositorySearchService>): ArtifactRepositorySearchService {
        return searchService
      }
    }

    val moduleSystem = GradleModuleSystem(
      module = myModule,
      projectBuildModelHandler = ProjectBuildModelHandler(project),
      moduleHierarchyProvider = mock()
    )

    analyzer = GradleDependencyCompatibilityAnalyzer(
      moduleSystem = moduleSystem,
      projectBuildModelHandler = ProjectBuildModelHandler(project),
      repositorySearchFactory = repositorySearchFactory,
      repoUrlManager = repoUrlManager
    )
  }

  fun testGetAvailableDependency_fallbackToPreview() {
    // In the test repo NAVIGATION only has a preview version 0.0.1-alpha1
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.NAVIGATION.getCoordinate("+"))).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GoogleMavenArtifactId.NAVIGATION.getCoordinate("0.0.1-alpha1"))
  }

  fun testGetAvailableDependency_returnsLatestStable() {
    // In the test repo CONSTRAINT_LAYOUT has a stable version of 1.0.2 and a beta version of 1.1.0-beta3
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+"))).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("1.0.2"))
  }

  fun testGetAvailableDependency_returnsNullWhenNoneMatches() {
    // The test repo does not have any version of PLAY_SERVICES_ADS.
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.PLAY_SERVICES_ADS.getCoordinate("+"))).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("The dependency was not found: com.google.android.gms:play-services-ads:+")
    assertThat(missing).containsExactly(GoogleMavenArtifactId.PLAY_SERVICES_ADS.getCoordinate("+"))
    assertThat(found).isEmpty()
  }

  fun testAddSupportDependencyWithMatchInSubModule() {
    val libraryModule = getAdditionalModuleByName(library1ModuleName)!!
    createFakeModel(libraryModule, Collections.singletonList("com.android.support:appcompat-v7:23.1.1"))
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          api project(':$library1ModuleName')
      }""".trimIndent())

    myFixture.addFileToProject("$library1Path/build.gradle", """
      dependencies {
          implementation 'com.android.support:appcompat-v7:+'
      }""".trimIndent())

    // Check that the version is picked up from one of the sub modules
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.RECYCLERVIEW_V7.getCoordinate("+"))).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GoogleMavenArtifactId.RECYCLERVIEW_V7.getCoordinate("23.1.1"))
  }

  fun testAddSupportDependencyWithMatchInAppModule() {
    createFakeModel(myModule, listOf("com.android.support:recyclerview-v7:22.2.1"))
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          api project(':$library1ModuleName')
          implementation 'com.android.support:appcompat-v7:22.2.1'
      }""".trimIndent())

    // Check that the version is picked up from the parent module:
    val module1 = getAdditionalModuleByName(library1ModuleName)!!
    val gradleModuleSystem1 = GradleModuleSystem(
      module = module1,
      projectBuildModelHandler = ProjectBuildModelHandler(project),
      moduleHierarchyProvider = mock()
    )
    val compatibility1 = GradleDependencyCompatibilityAnalyzer(
      moduleSystem = gradleModuleSystem1,
      projectBuildModelHandler = ProjectBuildModelHandler(project),
      repoUrlManager = repoUrlManager
    )

    val (found, missing, warning) = compatibility1.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.RECYCLERVIEW_V7.getCoordinate("+"))).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GoogleMavenArtifactId.RECYCLERVIEW_V7.getCoordinate("22.2.1"))
  }

  fun testProjectWithIncompatibleDependencies() {
    createFakeModel(myModule, listOf("androidx.appcompat:appcompat:2.0.0", "androidx.appcompat:appcompat:1.2.0"))
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          implementation 'androidx.appcompat:appcompat:2.0.0'
          implementation 'androidx.appcompat:appcompat:1.2.0'
      }""".trimIndent())

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("androidx.fragment", "fragment", "+"))).get(1, TimeUnit.SECONDS)

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
    createFakeModel(myModule, listOf("androidx.appcompat:appcompat:2.0.0", "androidx.core:core:1.0.0"))
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          implementation 'androidx.appcompat:appcompat:2.0.0'
          implementation 'androidx.core:core:1.0.0'
      }""".trimIndent())

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("androidx.fragment", "fragment", "+"))).get(1, TimeUnit.SECONDS)

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
    createFakeModel(myModule, Collections.singletonList("com.google.android.material:material:1.3.0"))
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          implementation 'com.google.android.material:material:1.3.0'
      }""".trimIndent())

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("com.acme.pie", "pie", "+"))).get(1, TimeUnit.SECONDS)

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

    createFakeModel(getAdditionalModuleByName(library1ModuleName)!!, listOf("com.google.android.material:material:1.3.0"))
    myFixture.addFileToProject("$library1Path/build.gradle", """
      dependencies {
          implementation 'com.google.android.material:material:1.3.0'
      }""".trimIndent())

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("com.acme.pie", "pie", "+"))).get(1, TimeUnit.SECONDS)

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

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("com.acme.pie", "pie", "+"))).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate.parseCoordinateString("com.acme.pie:pie:1.0.0-alpha1"))
    assertThat(missing).isEmpty()
  }

  fun testNewestSameMajorIsChosenFromExistingIndirectDependency() {
    createFakeModel(myModule, listOf("androidx.appcompat:appcompat:1.0.0"))
    myFixture.addFileToProject("build.gradle", """
      dependencies {
          implementation 'androidx.appcompat:appcompat:1.0.0'
      }""".trimIndent())

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("androidx.fragment", "fragment", "+"))).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate.parseCoordinateString("androidx.fragment:fragment:1.2.0"))
    assertThat(missing).isEmpty()
  }

  fun testAddingImcompatibleTestDependenciesFromMultipleSources() {
    // We are adding 2 dependencies that have conflicting test dependencies.
    // We should ignore test dependencies with analyzing compatibility issues.
    // In this case recyclerview and material have conflicting dependencies on mockito,
    // recyclerview on mockito-core:2.19.0 and material on mockito-core:1.9.5
    createFakeModel(myModule, listOf("androidx.recyclerview:recyclerview:1.2.0"))
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("com.google.android.material", "material", "+"))).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(found).containsExactly(
      GradleCoordinate.parseCoordinateString("com.google.android.material:material:1.3.0"))
    assertThat(missing).isEmpty()
  }

  fun testAddingKotlinStdlibDependenciesFromMultipleSources() {
    // We are adding 2 kotlin dependencies which depend on different kotlin stdlib
    // versions: 1.2.50 and 1.3.0. Make sure the dependencies can be added without errors.
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("androidx.core", "core-ktx", "1.0.0"),
             GradleCoordinate("androidx.navigation", "navigation-runtime-ktx", "2.0.0"))).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(found).containsExactly(
      GradleCoordinate.parseCoordinateString("androidx.core:core-ktx:1.0.0"),
      GradleCoordinate.parseCoordinateString("androidx.navigation:navigation-runtime-ktx:2.0.0"))
    assertThat(missing).isEmpty()
  }

  fun testGetAvailableDependencyWithRequiredVersionMatching() {
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate(SdkConstants.SUPPORT_LIB_GROUP_ID, SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID, "+"))).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).hasSize(1)

    val foundDependency = found.first()
    assertThat(foundDependency.artifactId).isEqualTo(SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID)
    assertThat(foundDependency.groupId).isEqualTo(SdkConstants.SUPPORT_LIB_GROUP_ID)
    assertThat(foundDependency.version!!.major).isEqualTo(23)

    // TODO: b/129297171
    @Suppress("ConstantConditionIf")
    if (CHECK_DIRECT_GRADLE_DEPENDENCIES) {
      // When we were checking the parsed gradle file we were able to detect a specified "+" in the version.
      assertThat(foundDependency.version!!.minorSegment!!.text).isEqualTo("+")
    }
    else {
      // Now that we are using the resolved gradle version we are no longer able to detect a "+" in the version.
      assertThat(foundDependency.version!!.minor).isEqualTo(1)
      assertThat(foundDependency.version!!.micro).isEqualTo(1)
    }
  }

  fun testGetAvailableDependencyWhenUnavailable() {
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("nonexistent", "dependency123", "+"))).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("The dependency was not found: nonexistent:dependency123:+")
    assertThat(missing).containsExactly(GradleCoordinate("nonexistent", "dependency123", "+"))
    assertThat(found).isEmpty()
  }

  fun testWithExplicitVersion() {
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate(SdkConstants.SUPPORT_LIB_GROUP_ID, SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID, "23.1.0"))
    ).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate("com.android.support", "appcompat-v7", "23.1.0"))
  }

  fun testWithExplicitPreviewVersion() {
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate(SdkConstants.CONSTRAINT_LAYOUT_LIB_GROUP_ID, SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID, "1.1.0-beta3"))
    ).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate("com.android.support.constraint", "constraint-layout", "1.1.0-beta3"))
  }

  fun testWithExplicitNonExistingVersion() {
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate(SdkConstants.SUPPORT_LIB_GROUP_ID, SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID, "22.17.3"))
    ).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("The dependency was not found: com.android.support:appcompat-v7:22.17.3")
    assertThat(missing).containsExactly(GradleCoordinate("com.android.support", "appcompat-v7", "22.17.3"))
    assertThat(found).isEmpty()
  }

  fun testWithMajorVersion() {
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate(SdkConstants.SUPPORT_LIB_GROUP_ID, SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID, "23.+"))
    ).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate("com.android.support", "appcompat-v7", "23.1.1"))
  }

  fun testWithMajorMinorVersion() {
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate(SdkConstants.SUPPORT_LIB_GROUP_ID, SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID, "22.2.+"))
    ).get(1, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate("com.android.support", "appcompat-v7", "22.2.1"))
  }

  /**
   * Create a fake {@link AndroidModuleModel} and sets up the module with
   * [artifacts] dependencies.
   *
   * Disclaimer: this method has side effects. An IdeAndroidProject will be
   * created if it hasn't already been and it will override the
   * AndroidModuleModel and IdeAndroidProject to make it look like the specified
   * [artifacts] are in fact dependencies of the specified [module]
   */
  private fun createFakeModel(module: Module, artifacts: List<String>): AndroidModuleModel {
    val project = androidProject ?: createAndroidProject()
    val ideDependencies = IdeDependenciesStubBuilder().apply {
      androidLibraries = artifacts.map { l2AndroidLibrary(it) }
    }.build()
    val model = Mockito.mock(AndroidModuleModel::class.java)
    `when`(model.androidProject).thenReturn(project)
    `when`(model.selectedMainCompileLevel2Dependencies).thenReturn(ideDependencies)
    val facet = AndroidFacet.getInstance(module)!!
    AndroidModel.set(facet, model)
    return model
  }

  private fun createAndroidProject(): IdeAndroidProject {
    androidProject = Mockito.mock(IdeAndroidProject::class.java)
    return androidProject!!
  }
}
