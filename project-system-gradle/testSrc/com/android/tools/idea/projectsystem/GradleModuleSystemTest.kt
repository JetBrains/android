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

import com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.stubs.l2AndroidLibrary
import com.android.ide.common.gradle.model.stubs.level2.IdeDependenciesStubBuilder
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.projectsystem.gradle.ProjectBuildModelHandler
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import junit.framework.AssertionFailedError
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.SourceProviderManager
import org.jetbrains.android.facet.SourceProviderManager.Companion.getInstance
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import java.io.File
import java.util.*

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
                                                                    "../../project-system-gradle/testData/repoIndex"),
                                                               cacheExpiryHours = Int.MAX_VALUE, useNetwork = false) {
    override fun readUrlData(url: String, timeout: Int): ByteArray? = throw AssertionFailedError("shouldn't try to read!")

    override fun error(throwable: Throwable, message: String?) {}
  }

  private val repoUrlManager = RepositoryUrlManager(mavenRepository, mavenRepository, forceRepositoryChecksInTests = false,
                                                    useEmbeddedStudioRepo = false)

  private val moduleHierarchyProviderStub = object : ModuleHierarchyProvider {}

  private val library1ModuleName = "library1"
  private val library1Path = AndroidTestCase.getAdditionalModulePath(library1ModuleName)

  override fun configureAdditionalModules(projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
                                          modules: List<AndroidTestCase.MyAdditionalModuleData>) {
    addModuleWithAndroidFacet(projectBuilder, modules, library1ModuleName, PROJECT_TYPE_LIBRARY)
  }

  override fun setUp() {
    super.setUp()
    _gradleDependencyManager = IdeComponents(project).mockProjectService(GradleDependencyManager::class.java)
    _gradleModuleSystem = GradleModuleSystem(myModule, ProjectBuildModelHandler(project), moduleHierarchyProviderStub, repoUrlManager)
    assertThat(gradleModuleSystem.getResolvedLibraryDependencies()).isEmpty()
  }

  override fun tearDown() {
    try {
      StudioFlags.ANDROID_MANIFEST_INDEX_ENABLED.clearOverride()
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
    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(toGradleCoordinate(GoogleMavenArtifactId.RECYCLERVIEW_V7)))

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(toGradleCoordinate(GoogleMavenArtifactId.RECYCLERVIEW_V7, "23.1.1"))
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
    val gradleModuleSystem = GradleModuleSystem(module1, ProjectBuildModelHandler(project), moduleHierarchyProviderStub, repoUrlManager)

    val (found, missing, warning) = gradleModuleSystem.analyzeDependencyCompatibility(
      listOf(toGradleCoordinate(GoogleMavenArtifactId.RECYCLERVIEW_V7)))

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(toGradleCoordinate(GoogleMavenArtifactId.RECYCLERVIEW_V7, "22.2.1"))
  }

  fun testProjectWithIncompatibleDependencies() {
    createFakeModel(myModule, listOf("androidx.appcompat:appcompat:2.0.0", "androidx.appcompat:appcompat:1.2.0"))
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
    createFakeModel(myModule, listOf("androidx.appcompat:appcompat:2.0.0", "androidx.core:core:1.0.0"))
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
    createFakeModel(myModule, Collections.singletonList("com.google.android.material:material:1.3.0"))
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

    createFakeModel(getAdditionalModuleByName(library1ModuleName)!!, listOf("com.google.android.material:material:1.3.0"))
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
    createFakeModel(myModule, listOf("androidx.appcompat:appcompat:1.0.0"))
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
    createFakeModel(myModule, listOf("androidx.recyclerview:recyclerview:1.2.0"))
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

  fun testGetPackageName_noOverrides() {
    val packageName = (myModule.getModuleSystem() as DefaultModuleSystem).getPackageName()
    assertThat(packageName).isEqualTo("p1.p2")
  }

  fun testGetPackageName_noOverrides_noIndex() {
    StudioFlags.ANDROID_MANIFEST_INDEX_ENABLED.override(false)
    val packageName = (myModule.getModuleSystem() as DefaultModuleSystem).getPackageName()
    assertThat(packageName).isEqualTo("p1.p2")
  }

  class Gradle : AndroidGradleTestCase() {
    fun testFindSourceProvider() {
      loadProject(TestProjectPaths.PROJECT_WITH_APPAND_LIB)
      val module = project.gradleModule(":app")!!
      val facet = module.androidFacet!!
      TestCase.assertNotNull(AndroidModel.get(facet))
      val moduleFile = VfsUtil.findFileByIoFile(getProjectFolderPath(), true)!!.findFileByRelativePath("app")
      TestCase.assertNotNull(moduleFile)
      // Try finding main flavor
      val mainFlavorSourceProvider = getInstance(facet).mainIdeaSourceProvider
      TestCase.assertNotNull(mainFlavorSourceProvider)
      val javaMainSrcFile = moduleFile!!.findFileByRelativePath("src/main/java/com/example/projectwithappandlib/")
      TestCase.assertNotNull(javaMainSrcFile)
      var providers: Collection<NamedIdeaSourceProvider?>? = facet.sourceProviders.getForFile(javaMainSrcFile)
      TestCase.assertNotNull(providers)
      TestCase.assertEquals(1, providers!!.size)
      var actualProvider = providers.iterator().next()
      TestCase.assertEquals(mainFlavorSourceProvider, actualProvider)
      // Try finding paid flavor
      val paidFlavorSourceProvider =
        getInstance(facet).currentAndSomeFrequentlyUsedInactiveSourceProviders
          .single { it: NamedIdeaSourceProvider -> it.name.equals("paid", ignoreCase = true) }
      val javaSrcFile = moduleFile.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid")
      TestCase.assertNotNull(javaSrcFile)
      providers = facet.sourceProviders.getForFile(javaSrcFile)
      TestCase.assertNotNull(providers)
      TestCase.assertEquals(1, providers!!.size)
      actualProvider = providers.iterator().next()
      TestCase.assertEquals(paidFlavorSourceProvider, actualProvider)
    }

    fun testSourceProviderContainsFile() {
      loadProject(TestProjectPaths.PROJECT_WITH_APPAND_LIB)
      val module = project.gradleModule(":app")!!
      val facet = module.androidFacet!!
      TestCase.assertNotNull(AndroidModel.get(facet))
      val paidFlavorSourceProvider: IdeaSourceProvider =
        getInstance(facet).currentAndSomeFrequentlyUsedInactiveSourceProviders
          .single { it: NamedIdeaSourceProvider -> it.name.equals("paid", ignoreCase = true) }
      val moduleFile = VfsUtil.findFileByIoFile(projectFolderPath, true)!!.findFileByRelativePath("app")
      TestCase.assertNotNull(moduleFile)
      val javaSrcFile = moduleFile!!.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid")
      TestCase.assertNotNull(javaSrcFile)
      assertTrue(paidFlavorSourceProvider.containsFile(javaSrcFile!!))
      val javaMainSrcFile = moduleFile.findFileByRelativePath("src/main/java/com/example/projectwithappandlib/")
      TestCase.assertNotNull(javaMainSrcFile)
      assertFalse(paidFlavorSourceProvider.containsFile(javaMainSrcFile!!))
    }

    fun testSourceProviderIsContainedByFolder() {
      loadProject(TestProjectPaths.PROJECT_WITH_APPAND_LIB, "app")

      val paidFlavorSourceProvider = SourceProviderManager.getInstance(myAndroidFacet)
        .currentAndSomeFrequentlyUsedInactiveSourceProviders
        .filter { it -> it.name.equals("paid", ignoreCase = true) }
        .single()

      val moduleFile = VfsUtil.findFileByIoFile(projectFolderPath, true)!!.findFileByRelativePath("app")
      assertNotNull(moduleFile)
      val javaSrcFile = moduleFile!!.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid")!!
      assertNotNull(javaSrcFile)

      assertFalse(paidFlavorSourceProvider.isContainedBy(javaSrcFile))

      val flavorRoot = moduleFile.findFileByRelativePath("src/paid")!!
      assertNotNull(flavorRoot)

      assertTrue(paidFlavorSourceProvider.isContainedBy(flavorRoot))

      val srcFile = moduleFile.findChild("src")!!
      assertNotNull(srcFile)

      assertTrue(paidFlavorSourceProvider.isContainedBy(srcFile))
    }

    fun testSourceProviderIsContainedByFolder_noSources() {
      loadProject(TestProjectPaths.PROJECT_WITH_APPAND_LIB, "app")

      val paidFlavorSourceProvider = SourceProviderManager.getInstance(myAndroidFacet)
        .currentAndSomeFrequentlyUsedInactiveSourceProviders.single { it.name.equals("basicDebug", ignoreCase = true) }

      val moduleFile = VfsUtil.findFileByIoFile(projectFolderPath, true)!!.findFileByRelativePath("app")
      assertNotNull(moduleFile)


      val srcFile = moduleFile!!.findChild("src")!!
      assertNotNull(srcFile)

      assertTrue(paidFlavorSourceProvider.isContainedBy(srcFile))
    }
  }

  private fun toGradleCoordinate(id: GoogleMavenArtifactId, version: String = "+"): GradleCoordinate {
    return GradleCoordinate(id.mavenGroupId, id.mavenArtifactId, version)
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
    val model = mock(AndroidModuleModel::class.java)
    `when`(model.androidProject).thenReturn(project)
    `when`(model.selectedMainCompileLevel2Dependencies).thenReturn(ideDependencies)
    val facet = AndroidFacet.getInstance(module)!!
    AndroidModel.set(facet, model)
    return model
  }

  private fun createAndroidProject(): IdeAndroidProject {
    androidProject = mock(IdeAndroidProject::class.java)
    return androidProject!!
  }
}
