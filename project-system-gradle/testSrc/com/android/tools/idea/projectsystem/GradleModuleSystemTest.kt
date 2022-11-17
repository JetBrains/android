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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.SourceProviderManager.Companion.getInstance
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.projectsystem.gradle.ProjectBuildModelHandler
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.times

/**
 * Replaces the [from] string in the [VirtualFile] with the [to] string.
 */
private fun VirtualFile.replace(from: String, to: String) =
  setBinaryContent(String(contentsToByteArray(false)).replace(from, to).toByteArray())

/**
 * These unit tests use a local test maven repo "project-system-gradle/testData/repoIndex". To see
 * what dependencies are available to test with, go to that folder and look at the group-indices.
 */
class GradleModuleSystemTest : AndroidTestCase() {
  private var _gradleDependencyManager: GradleDependencyManager? = null
  private var _gradleModuleSystem: GradleModuleSystem? = null
  private var androidProject: IdeAndroidProject? = null
  private val gradleDependencyManager get() = _gradleDependencyManager!!
  private val gradleModuleSystem get() = _gradleModuleSystem!!
  private val moduleHierarchyProviderStub = object : ModuleHierarchyProvider {}

  override fun setUp() {
    super.setUp()
    _gradleDependencyManager = IdeComponents(project).mockProjectService(GradleDependencyManager::class.java)
    _gradleModuleSystem = GradleModuleSystem(myModule, ProjectBuildModelHandler(project), moduleHierarchyProviderStub)
    assertThat(gradleModuleSystem.getAndroidLibraryDependencies(DependencyScopeType.MAIN)).isEmpty()
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

  fun testGetPackageName_noOverrides() {
    val packageName = (myModule.getModuleSystem() as DefaultModuleSystem).getPackageName()
    assertThat(packageName).isEqualTo("p1.p2")
  }

  @RunsInEdt
  class Gradle {

    @get:Rule
    val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

    @Test
    fun testFindSourceProvider() {
      val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APPAND_LIB)
      preparedProject.open { project ->
        val module = project.gradleModule(":app")!!
        val facet = module.androidFacet!!
        TestCase.assertNotNull(AndroidModel.get(facet))
        val moduleFile = VfsUtil.findFileByIoFile(preparedProject.root.resolve("app"), true)
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
    }

    @Test
    fun testSourceProviderContainsFile() {
      val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APPAND_LIB)
      preparedProject.open { project ->
        val module = project.gradleModule(":app")!!
        val facet = module.androidFacet!!
        TestCase.assertNotNull(AndroidModel.get(facet))
        val paidFlavorSourceProvider: IdeaSourceProvider =
          getInstance(facet).currentAndSomeFrequentlyUsedInactiveSourceProviders
            .single { it: NamedIdeaSourceProvider -> it.name.equals("paid", ignoreCase = true) }
        val moduleFile = VfsUtil.findFileByIoFile(preparedProject.root.resolve("app"), true)
        TestCase.assertNotNull(moduleFile)
        val javaSrcFile = moduleFile!!.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid")
        TestCase.assertNotNull(javaSrcFile)
        assertTrue(paidFlavorSourceProvider.containsFile(javaSrcFile!!))
        val javaMainSrcFile = moduleFile.findFileByRelativePath("src/main/java/com/example/projectwithappandlib/")
        TestCase.assertNotNull(javaMainSrcFile)
        assertFalse(paidFlavorSourceProvider.containsFile(javaMainSrcFile!!))
      }
    }

    @Test
    fun testSourceProviderIsContainedByFolder() {
      val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APPAND_LIB)
      preparedProject.open { project ->
        val module = project.gradleModule(":app")!!
        val facet = module.androidFacet!!

        val paidFlavorSourceProvider = SourceProviderManager.getInstance(facet)
          .currentAndSomeFrequentlyUsedInactiveSourceProviders
          .filter { it -> it.name.equals("paid", ignoreCase = true) }
          .single()

        val moduleFile = VfsUtil.findFileByIoFile(preparedProject.root.resolve("app"), true)
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
    }

    @Test
    fun testSourceProviderIsContainedByFolder_noSources() {
      val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APPAND_LIB)
      preparedProject.open { project ->
        val module = project.gradleModule(":app")!!
        val facet = module.androidFacet!!

        val paidFlavorSourceProvider = getInstance(facet)
          .currentAndSomeFrequentlyUsedInactiveSourceProviders.single { it.name.equals("basicDebug", ignoreCase = true) }

        val moduleFile = VfsUtil.findFileByIoFile(preparedProject.root.resolve("app"), true)
        assertNotNull(moduleFile)

        val srcFile = moduleFile!!.findChild("src")!!
        assertNotNull(srcFile)

        assertTrue(paidFlavorSourceProvider.isContainedBy(srcFile))
      }
    }

    @Test
    fun testUsesComposeFlag() {
      val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
      preparedProject.open { project ->
        val module = project.gradleModule(":app")!!

        val projectSystem = ProjectSystemService.getInstance(project).projectSystem
        val moduleSystem = projectSystem.getModuleSystem(module)
        // Verify defaults
        assertFalse(
          "usesCompose override is only meant to be set via properties by the AndroidX project",
          StudioFlags.COMPOSE_PROJECT_USES_COMPOSE_OVERRIDE.get()
        )
        assertFalse(moduleSystem.usesCompose)

        StudioFlags.COMPOSE_PROJECT_USES_COMPOSE_OVERRIDE.override(true)
        try {
          assertTrue(moduleSystem.usesCompose)
        } finally {
          StudioFlags.COMPOSE_PROJECT_USES_COMPOSE_OVERRIDE.clearOverride()
        }
      }
    }
  }
}
