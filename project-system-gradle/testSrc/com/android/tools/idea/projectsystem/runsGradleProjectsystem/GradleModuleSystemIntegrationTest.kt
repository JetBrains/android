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
package com.android.tools.idea.projectsystem.runsGradleProjectsystem

import com.android.SdkConstants.SUPPORT_LIB_GROUP_ID
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.gradle.Module
import com.android.ide.common.gradle.RichVersion
import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7
import com.android.ide.common.repository.WellKnownMavenArtifactId.Companion.GUAVA_GUAVA
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.DependencyScopeType.MAIN
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.projectsystem.containsFile
import com.android.tools.idea.projectsystem.getForFile
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.projectsystem.isContainedBy
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.RunsInEdt
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
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
      val moduleSystem = project.findAppModule().getModuleSystem() as GradleModuleSystem
      val dependencyManager = GradleDependencyManager.getInstance(project)
      val dummyDependency = Dependency.parse("a:b:+")
      val anotherDummyDependency = Dependency.parse("hello:world:1.2.3")

      moduleSystem.registerDependency(dummyDependency, DependencyType.IMPLEMENTATION)
      moduleSystem.registerDependency(anotherDummyDependency, DependencyType.IMPLEMENTATION)

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
      val moduleSystem = project.findAppModule().getModuleSystem() as GradleModuleSystem

      // Verify that getRegisteredDependency gets a existing dependency correctly.
      val appCompat = SUPPORT_APPCOMPAT_V7
      assertThat(moduleSystem.hasRegisteredDependency(appCompat)).isTrue()
      assertThat(moduleSystem.hasRegisteredDependency(appCompat.getModule())).isTrue()
      val foundDependency = moduleSystem.getRegisteredDependency(appCompat)
      assertThat(foundDependency).isNotNull()
      assertThat(foundDependency?.dependency?.module).isEqualTo(appCompat.getModule())
      assertThat(foundDependency?.dependency?.version).isEqualTo(RichVersion.parse("+"))
    }
  }

  @Test
  fun testGetRegisteredDependencies() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APP_WITH_OLDER_SUPPORT_LIB)
    preparedProject.open { project ->
      val moduleSystem = project.findAppModule().getModuleSystem() as GradleModuleSystem
      val appCompat = SUPPORT_APPCOMPAT_V7

      assertThat(moduleSystem.getRegisteredDependency(appCompat.getModule())).isEqualTo(appCompat.getDependency("25.4.0"))
      assertThat(moduleSystem.hasRegisteredDependency(appCompat.getModule())).isTrue()
      assertThat(moduleSystem.getRegisteredDependency(Module(SUPPORT_LIB_GROUP_ID, "BAD"))).isNull()
      assertThat(moduleSystem.hasRegisteredDependency(Module(SUPPORT_LIB_GROUP_ID, "BAD"))).isFalse()
    }
  }

  @Test
  fun `test use androidx in all modules of a non-androidx project`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.root.resolve("gradle.properties").appendText("""
      android.useAndroidX=false
    """.trimIndent())

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
  fun testGetResolvedAarDependencies() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      verifyProjectDependsOnWildcardAppCompat(project)

      val moduleSystem = project.findAppModule().getModuleSystem() as GradleModuleSystem
      // appcompat-v7 is a dependency with an AAR.
      assertThat(moduleSystem.hasResolvedDependency(SUPPORT_APPCOMPAT_V7)).isTrue()
      assertThat(moduleSystem.getResolvedDependency(SUPPORT_APPCOMPAT_V7.getModule(), MAIN)?.version).isEqualTo(Version.parse("28.0.0"))
    }
  }

  @Test
  fun testGetResolvedJarDependencies() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      verifyProjectDependsOnGuava(project)

      val moduleSystem = project.findAppModule().getModuleSystem() as GradleModuleSystem
      // guava is a dependency with a JAR.
      assertThat(moduleSystem.hasResolvedDependency(GUAVA_GUAVA)).isTrue()
      assertThat(moduleSystem.getResolvedDependency(GUAVA_GUAVA.getModule(), MAIN)?.version).isEqualTo(Version.parse("19.0"))
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
      assertThat(moduleSystem.getDependencyPath(SUPPORT_APPCOMPAT_V7.getDependency("+"))).isNotNull()
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

  @Test
  fun testDisableAgpUpgradePromptProperty() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)

    preparedProject.open { project ->
      val module = project.gradleModule(":app")!!

      val projectSystem = ProjectSystemService.getInstance(project).projectSystem
      val moduleSystem = projectSystem.getModuleSystem(module)

      val gradleProperties = project.guessProjectDir()?.findChild("gradle.properties")!!

      assertThat(gradleProperties.exists()).isTrue()

      ApplicationManager.getApplication().runWriteAction {
        VfsUtil.saveText(gradleProperties, """
        android.disableAgpUpgradePrompt=true
      """.trimIndent())
      }
      GradleSyncInvoker.getInstance().requestProjectSync(project,
                                                         GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED))

      AndroidTestCase.assertTrue(moduleSystem.disableAgpUpgradePrompt)

      ApplicationManager.getApplication().runWriteAction {
        VfsUtil.saveText(gradleProperties, """
        android.disableAgpUpgradePrompt=false
      """.trimIndent())
      }
      GradleSyncInvoker.getInstance().requestProjectSync(project,
                                                         GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED))

      AndroidTestCase.assertFalse(moduleSystem.disableAgpUpgradePrompt)
    }
  }

  private fun verifyProjectDependsOnWildcardAppCompat(project: Project) {
    val projectSystem = project.getProjectSystem() as GradleProjectSystem
    val moduleSystem = projectSystem.getModuleSystem(project.findAppModule())
    val dependency = moduleSystem.getRegisteredDependency(SUPPORT_APPCOMPAT_V7)
    assertThat(dependency).isNotNull()
    assertThat(dependency?.dependency?.version).isEqualTo(RichVersion.parse("+"))
  }

  private fun verifyProjectDependsOnGuava(project: Project) {
    val projectSystem = project.getProjectSystem() as GradleProjectSystem
    val moduleSystem = projectSystem.getModuleSystem(project.findAppModule())
    val dependency = moduleSystem.getRegisteredDependency(GUAVA_GUAVA)
    assertThat(dependency).isNotNull()
  }
}