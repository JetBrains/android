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

import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
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
import com.intellij.openapi.vfs.VfsUtil
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.SourceProviderManager
import org.jetbrains.android.facet.SourceProviderManager.Companion.getInstance
import org.mockito.Mockito
import org.mockito.Mockito.times

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
    assertThat(gradleModuleSystem.getAndroidLibraryDependencies()).isEmpty()
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

      val paidFlavorSourceProvider = getInstance(myAndroidFacet)
        .currentAndSomeFrequentlyUsedInactiveSourceProviders.single { it.name.equals("basicDebug", ignoreCase = true) }

      val moduleFile = VfsUtil.findFileByIoFile(projectFolderPath, true)!!.findFileByRelativePath("app")
      assertNotNull(moduleFile)


      val srcFile = moduleFile!!.findChild("src")!!
      assertNotNull(srcFile)

      assertTrue(paidFlavorSourceProvider.isContainedBy(srcFile))
    }
  }
}
