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
package com.android.tools.idea.projectsystem.runsGradle

import com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID
import com.android.SdkConstants.SUPPORT_LIB_GROUP_ID
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.ide.common.repository.GradleCoordinate
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.projectsystem.containsFile
import com.android.tools.idea.projectsystem.getForFile
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.gradle.CHECK_DIRECT_GRADLE_DEPENDENCIES
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.projectsystem.isContainedBy
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.RunsInEdt
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Integration tests for [GradleModuleSystem]; contains tests that require a working gradle project.
 */
@RunsInEdt
class GradleModuleSystemIntegrationTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testRegisterDependency() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      val moduleSystem = project.findAppModule().getModuleSystem()
      val dependencyManager = GradleDependencyManager.getInstance(project)
      val dummyCoordinate = GradleCoordinate("a", "b", "+")
      val dummyDependency = Dependency.parse(dummyCoordinate.toString())
      val anotherDummyCoordinate = GradleCoordinate("hello", "world", "1.2.3")
      val anotherDummyDependency = Dependency.parse(anotherDummyCoordinate.toString())

      moduleSystem.registerDependency(dummyCoordinate)
      moduleSystem.registerDependency(anotherDummyCoordinate)

      assertThat(
        dependencyManager.findMissingDependencies(project.findAppModule(), listOf(dummyDependency, anotherDummyDependency))
      ).isEmpty()
    }
  }

  @Test
  fun testGetRegisteredExistingDependency() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      verifyProjectDependsOnWildcardAppCompat(project)
      val moduleSystem = project.findAppModule().getModuleSystem()

      // Verify that getRegisteredDependency gets a existing dependency correctly.
      val appCompat = GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
      val foundDependency = moduleSystem.getRegisteredDependency(appCompat)!!

      assertThat(foundDependency.artifactId).isEqualTo(APPCOMPAT_LIB_ARTIFACT_ID)
      assertThat(foundDependency.groupId).isEqualTo(SUPPORT_LIB_GROUP_ID)
      assertThat(foundDependency.lowerBoundVersion!!.major).isEqualTo(28)

      // TODO: b/129297171
      @Suppress("ConstantConditionIf")
      if (CHECK_DIRECT_GRADLE_DEPENDENCIES) {
        // When we were checking the parsed gradle file we were able to detect a specified "+" in the version.
        assertThat(foundDependency.acceptsGreaterRevisions()).isTrue()
      } else {
        // Now that we are using the resolved gradle version we are no longer able to detect a "+" in the version.
        assertThat(foundDependency.lowerBoundVersion!!.minor).isLessThan(Integer.MAX_VALUE)
        assertThat(foundDependency.lowerBoundVersion!!.micro).isLessThan(Integer.MAX_VALUE)
      }
    }
  }

  @Test
  fun testGetRegisteredDependencies() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APP_WITH_OLDER_SUPPORT_LIB)
    preparedProject.open { project ->
      val moduleSystem = project.findAppModule().getModuleSystem()
      val appCompat = GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "25.4.0")

      // Matching Dependencies:
      assertThat(
        isSameArtifact(
          moduleSystem.getRegisteredDependency(
            GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "25.4.0")
          ), appCompat
        )
      ).isTrue()
      assertThat(
        isSameArtifact(
          moduleSystem.getRegisteredDependency(
            GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "25.4.+")
          ), appCompat
        )
      ).isTrue()
      assertThat(
        isSameArtifact(
          moduleSystem.getRegisteredDependency(
            GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "25.+")
          ), appCompat
        )
      ).isTrue()
      assertThat(
        isSameArtifact(
          moduleSystem.getRegisteredDependency(
            GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "+")
          ), appCompat
        )
      ).isTrue()

      // Non Matching Dependencies:
      assertThat(
        moduleSystem.getRegisteredDependency(
          GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "25.0.99")
        )
      ).isNull()
      assertThat(
        moduleSystem.getRegisteredDependency(
          GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "4.99.+")
        )
      ).isNull()
      assertThat(
        moduleSystem.getRegisteredDependency(
          GradleCoordinate(SUPPORT_LIB_GROUP_ID, "BAD", "25.4.0")
        )
      ).isNull()
    }
  }

  @Test
  fun `test use androidx in all modules of a non-androidx project`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      val modules = project.modules.toList()
      assertThat(modules).isNotEmpty()
      for (module in modules) {
        assertThat(module.getModuleSystem().useAndroidX)
          .named("module[%s].moduleSystem.useAndroidx", module.name)
          .isFalse()
      }
    }
  }

  @Test
  fun `test use androidx in all modules of a androidx project`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.ANDROIDX_SIMPLE)
    preparedProject.open { project ->
      val modules = project.modules.toList()
      assertThat(modules).isNotEmpty()
      for (module in modules) {
        assertThat(module.getModuleSystem().useAndroidX)
          .named("module[\"{}\"].moduleSystem.useAndroidx", module.name)
          .isTrue()
      }
    }
  }

  @Test
  fun testGetResolvedMatchingDependencies() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      verifyProjectDependsOnWildcardAppCompat(project)
      val moduleSystem = project.findAppModule().getModuleSystem()

      // Verify that app-compat is on version 28.0.0 so the checks below make sense.
      assertThat(moduleSystem.getResolvedDependency(GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+"))!!.revision).isEqualTo("28.0.0")

      val appCompatDependency = GradleCoordinate("com.android.support", "appcompat-v7", "+")
      val wildcardVersionResolution = moduleSystem.getResolvedDependency(appCompatDependency)
      assertThat(wildcardVersionResolution).isNotNull()
      assertThat(wildcardVersionResolution!!.matches(appCompatDependency)).isTrue()
    }
  }

  @Test
  @Ignore("b/136028658")
  fun testGetResolvedNonMatchingDependencies() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      verifyProjectDependsOnWildcardAppCompat(project)
      val moduleSystem = project.findAppModule().getModuleSystem()

      // Verify that app-compat is on version 28.0.0 so the checks below make sense.
      assertThat(moduleSystem.getResolvedDependency(GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+"))!!.revision).isEqualTo("28.0.0")

      assertThat(moduleSystem.getResolvedDependency(GradleCoordinate("com.android.support", "appcompat-v7", "26.+"))).isNull()
      assertThat(moduleSystem.getResolvedDependency(GradleCoordinate("com.android.support", "appcompat-v7", "99.9.0"))).isNull()
      assertThat(moduleSystem.getResolvedDependency(GradleCoordinate("com.android.support", "appcompat-v7", "99.+"))).isNull()
    }
  }

  @Test
  fun testGetResolvedAarDependencies() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      verifyProjectDependsOnWildcardAppCompat(project)

      // appcompat-v7 is a dependency with an AAR.
      assertThat(
        project.findAppModule().getModuleSystem().getResolvedDependency(
          GradleCoordinate("com.android.support", "appcompat-v7", "+")
        )
      ).isNotNull()
    }
  }

  @Test
  fun testGetResolvedJarDependencies() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      verifyProjectDependsOnGuava(project)

      // guava is a dependency with a JAR.
      assertThat(
        project.findAppModule().getModuleSystem().getResolvedDependency(
          GradleCoordinate("com.google.guava", "guava", "+")
        )
      ).isNotNull()
    }
  }

  @Test
  fun testGetDynamicFeatureModules() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.INSTANT_APP_WITH_DYNAMIC_FEATURES)
    preparedProject.open { project ->
      val moduleSystem = project.findAppModule().getModuleSystem()
      val dynamicFeatureModuleNames = moduleSystem.getDynamicFeatureModules().map { it.name }
      assertThat(dynamicFeatureModuleNames).containsExactly(
        project.findModule("dynamicfeature").getName(),
        project.findModule("instantdynamicfeature").getName()
      ).inOrder()
    }
  }

  @Test
  fun testGetDependencyPath() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->

      val moduleSystem = project.findAppModule().getModuleSystem() as GradleModuleSystem

      // Verify that the module system returns a path.
      assertThat(moduleSystem.getDependencyPath(GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+"))).isNotNull()
    }
  }

  @Test
  fun testDesugarLibraryConfigFiles() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    val buildGradle = preparedProject.root.resolve("app/build.gradle")
    buildGradle.appendText("""
      
      android.compileOptions.coreLibraryDesugaringEnabled = true
      android.defaultConfig.multiDexEnabled = true
      dependencies {
          coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:1.1.5'
      }
    """.trimIndent())
    preparedProject.open { project ->

      val moduleSystem = project.findAppModule().getModuleSystem()

      assertThat(moduleSystem.desugarLibraryConfigFilesKnown).named("desugarLibraryConfigFilesKnown").isTrue()
      assertThat(moduleSystem.desugarLibraryConfigFilesNotKnownUserMessage).named("desugarLibraryConfigFilesNotKnownUserMessage").isNull()
      assertThat(moduleSystem.desugarLibraryConfigFiles).named("desugarLibraryConfigFiles").hasSize(1)
      assertThat(moduleSystem.desugarLibraryConfigFiles.single()).contains("""
        {
          "configuration_format_version": 3,
          "group_id" : "com.tools.android",
          "artifact_id" : "desugar_jdk_libs",
          "version": "1.1.5"
      """.trimIndent()) // Asserting the beginning of the file has the expected header
    }
  }

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
      val mainFlavorSourceProvider = SourceProviderManager.getInstance(facet).mainIdeaSourceProvider
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
        SourceProviderManager.getInstance(facet).currentAndSomeFrequentlyUsedInactiveSourceProviders
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
        SourceProviderManager.getInstance(facet).currentAndSomeFrequentlyUsedInactiveSourceProviders
          .single { it: NamedIdeaSourceProvider -> it.name.equals("paid", ignoreCase = true) }
      val moduleFile = VfsUtil.findFileByIoFile(preparedProject.root.resolve("app"), true)
      TestCase.assertNotNull(moduleFile)
      val javaSrcFile = moduleFile!!.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid")
      TestCase.assertNotNull(javaSrcFile)
      AndroidTestCase.assertTrue(paidFlavorSourceProvider.containsFile(javaSrcFile!!))
      val javaMainSrcFile = moduleFile.findFileByRelativePath("src/main/java/com/example/projectwithappandlib/")
      TestCase.assertNotNull(javaMainSrcFile)
      AndroidTestCase.assertFalse(paidFlavorSourceProvider.containsFile(javaMainSrcFile!!))
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
      AndroidTestCase.assertNotNull(moduleFile)
      val javaSrcFile = moduleFile!!.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid")!!
      AndroidTestCase.assertNotNull(javaSrcFile)

      AndroidTestCase.assertFalse(paidFlavorSourceProvider.isContainedBy(javaSrcFile))

      val flavorRoot = moduleFile.findFileByRelativePath("src/paid")!!
      AndroidTestCase.assertNotNull(flavorRoot)

      AndroidTestCase.assertTrue(paidFlavorSourceProvider.isContainedBy(flavorRoot))

      val srcFile = moduleFile.findChild("src")!!
      AndroidTestCase.assertNotNull(srcFile)

      AndroidTestCase.assertTrue(paidFlavorSourceProvider.isContainedBy(srcFile))
    }
  }

  @Test
  fun testSourceProviderIsContainedByFolder_noSources() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APPAND_LIB)
    preparedProject.open { project ->
      val module = project.gradleModule(":app")!!
      val facet = module.androidFacet!!

      val paidFlavorSourceProvider = SourceProviderManager.getInstance(facet)
        .currentAndSomeFrequentlyUsedInactiveSourceProviders.single { it.name.equals("basicDebug", ignoreCase = true) }

      val moduleFile = VfsUtil.findFileByIoFile(preparedProject.root.resolve("app"), true)
      AndroidTestCase.assertNotNull(moduleFile)

      val srcFile = moduleFile!!.findChild("src")!!
      AndroidTestCase.assertNotNull(srcFile)

      AndroidTestCase.assertTrue(paidFlavorSourceProvider.isContainedBy(srcFile))
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
      AndroidTestCase.assertFalse(
        "usesCompose override is only meant to be set via properties by the AndroidX project",
        StudioFlags.COMPOSE_PROJECT_USES_COMPOSE_OVERRIDE.get()
      )
      AndroidTestCase.assertFalse(moduleSystem.usesCompose)

      StudioFlags.COMPOSE_PROJECT_USES_COMPOSE_OVERRIDE.override(true)
      try {
        AndroidTestCase.assertTrue(moduleSystem.usesCompose)
      } finally {
        StudioFlags.COMPOSE_PROJECT_USES_COMPOSE_OVERRIDE.clearOverride()
      }
    }
  }

  private fun isSameArtifact(first: GradleCoordinate?, second: GradleCoordinate?) =
    GradleCoordinate.COMPARE_PLUS_LOWER.compare(first, second) == 0

  private fun verifyProjectDependsOnWildcardAppCompat(project: Project) {
    // SimpleApplication should have a dependency on "com.android.support:appcompat-v7:+"
    val appCompatArtifact = ProjectBuildModel
      .get(project)
      .getModuleBuildModel(project.findAppModule())
      ?.dependencies()
      ?.artifacts()
      ?.find { "${it.group()}:${it.name().forceString()}" == GoogleMavenArtifactId.APP_COMPAT_V7.toString() }

    assertThat(appCompatArtifact).isNotNull()
    assertThat(appCompatArtifact!!.version().toString()).isEqualTo("+")
  }

  private fun verifyProjectDependsOnGuava(project: Project) {
    // SimpleApplication should have a dependency on guava.
    assertThat(
      ProjectBuildModel
        .get(project)
        .getModuleBuildModel(project.findAppModule())
        ?.dependencies()
        ?.artifacts()
        ?.find { "${it.group()}:${it.name().forceString()}" == "com.google.guava:guava" }
    ).isNotNull()
  }
}