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
package com.android.tools.idea.gradle.project.sync

import com.android.sdklib.devices.Abi
import com.android.tools.idea.gradle.model.IdeSyncIssue.Companion.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.internal.dump
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.projectsystem.getAndroidTestModule
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.assertAreEqualToSnapshots
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.saveAndDump
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.PathUtil
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.impl.url.toVirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.android.AndroidTestBase
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.fail

@RunsInEdt
class SyncScenariosIntegrationTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun testPsdDependencyUpgradeLibraryModule() {
    class ModuleSnapshots(val mainMain: String, val mainAndroidTest: String, val modulePlusMain: String)

    fun Project.dumpTestedModules(): ModuleSnapshots =
      ModuleSnapshots(
        mainMain = dumpModule(":mainModule", Module::getMainModule),
        mainAndroidTest = dumpModule(":mainModule", Module::getAndroidTestModule),
        modulePlusMain = dumpModule(":modulePlus", Module::getMainModule),
      )

    prepareGradleProject(TestProjectToSnapshotPaths.PSD_DEPENDENCY, "project")
    openPreparedProject("project") { project ->
      val beforeLibUpgrade = project.dumpTestedModules()
      PsProjectImpl(project).let { projectModel ->
        projectModel
          .findModuleByGradlePath(":modulePlus")!!
          .dependencies
          .findLibraryDependencies("com.example.libs", "lib1")
          .forEach { it.version = "1.0".asParsed() }
        projectModel
          .findModuleByGradlePath(":mainModule")!!
          .dependencies
          .findLibraryDependencies("com.example.libs", "lib1")
          .forEach { it.version = "0.9.1".asParsed() }
        projectModel
          .findModuleByGradlePath(":mainModule")!!
          .dependencies
          .findLibraryDependencies("com.example.jlib", "lib3")
          .single().version = "0.9.1".asParsed()
        projectModel.applyChanges()
      }
      project.requestSyncAndWait()
      val afterLibUpgrade = project.dumpTestedModules()

      fun String.replaceVersions(from: String, to: String): String {
        return replace(Regex("(com\\.example\\.j?libs?:lib.:)$from")) { it.groupValues[1] + to }
      }

      // Note: in fact the test downgrades the version in `:mainModule`.
      expect.that(afterLibUpgrade.mainMain)
        .isEqualTo(beforeLibUpgrade.mainMain.replaceVersions(from = "1\\.0", to = "0.9.1"))
      expect.that(afterLibUpgrade.mainAndroidTest)
        .isEqualTo(beforeLibUpgrade.mainAndroidTest.replaceVersions(from = "1\\.0", to = "0.9.1"))

      expect.that(afterLibUpgrade.modulePlusMain)
        .isEqualTo(beforeLibUpgrade.modulePlusMain.replaceVersions(from = "0\\.9\\.1", to = "1.0"))
    }
  }

  /**
   * Manually added Sources on Librarys should only be removed if Gradle does not provide any.
   */
  @Test
  fun testAddedSourcesOnNoSourceLibraryArentRemoved() {
    val basePath = prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")
    val libWithNoSources = "com.google.truth:truth:0.44"
    val libWithSources = "junit:junit:4.12"

    val buildFile = basePath.resolve("app").resolve("build.gradle")
    buildFile.writeText(buildFile.readText() + """
      dependencies {
        implementation("$libWithNoSources")
        implementation("$libWithSources")
      }
    """)

    openPreparedProject("project") { project ->
      // Emulate adding source to the lib1 library.
      var table = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
      var noSourceLib = table.getLibraryByName("Gradle: $libWithNoSources")!!
      var sourceLib = table.getLibraryByName("Gradle: $libWithSources")!!
      assertThat(noSourceLib.getUrls(OrderRootType.SOURCES)).isEmpty()
      assertThat(sourceLib.getUrls(OrderRootType.SOURCES)).isNotNull()


      val fakeSourceRoot = "/some/path/to/a/source/jar.jar"
      noSourceLib.modifiableModel.apply {
        addRoot(fakeSourceRoot, OrderRootType.SOURCES)
        WriteCommandAction.runWriteCommandAction(project) { commit() }
      }

      sourceLib.modifiableModel.apply {
        addRoot(fakeSourceRoot, OrderRootType.SOURCES)
        WriteCommandAction.runWriteCommandAction(project) { commit() }
      }

      assertThat(noSourceLib.getUrls(OrderRootType.SOURCES)).hasLength(1)
      assertThat(noSourceLib.getUrls(OrderRootType.SOURCES)[0]).isEqualTo(fakeSourceRoot)

      // The library with sources from Gradle has both the original and added sources before we sync
      assertThat(sourceLib.getUrls(OrderRootType.SOURCES)).hasLength(2)
      assertThat(sourceLib.getUrls(OrderRootType.SOURCES)[0]).isNotEqualTo(fakeSourceRoot)

      project.requestSyncAndWait()

      table = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
      noSourceLib = table.getLibraryByName("Gradle: $libWithNoSources")!!
      sourceLib = table.getLibraryByName("Gradle: $libWithSources")!!

      // The added source to the library that doesn't provide one should be kept
      assertThat(noSourceLib.getUrls(OrderRootType.SOURCES)).hasLength(1)
      assertThat(noSourceLib.getUrls(OrderRootType.SOURCES)[0]).isEqualTo(fakeSourceRoot)

      // The added source to the library that provides one should be removed by the Gradle import
      assertThat(sourceLib.getUrls(OrderRootType.SOURCES)).hasLength(1)
      assertThat(sourceLib.getUrls(OrderRootType.SOURCES)[0]).isNotEqualTo(fakeSourceRoot)
    }
  }

  @Test
  fun testPsdSampleRenamingModule() {
    prepareGradleProject(TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY, "project")
    openPreparedProject("project") { project ->
      val beforeRename = project.dumpModule(":nested1:deep") { getMainModule() }
      expect.that(project.gradleModule(":container1:deep")).isNull()
      PsProjectImpl(project).let { projectModel ->
        projectModel.removeModule(":nested1")
        projectModel.removeModule(":nested1:deep")
        with(projectModel.parsedModel.projectSettingsModel!!) {
          addModulePath(":container1")
          addModulePath(":container1:deep")
        }
        projectModel.applyChanges()
      }
      WriteAction.run<Throwable> {
        project.guessProjectDir()!!.findFileByRelativePath("nested1")!!.rename("test", "container1")
      }
      ApplicationManager.getApplication().saveAll()
      project.requestSyncAndWait()
      expect.that(project.gradleModule(":nested1:deep")).isNull()
      val afterRename = project.dumpModule(":container1:deep") { getMainModule() }
      // Do not use expect since IDEA does not show the diff UI in this case.
      assertThat(afterRename).isEqualTo(beforeRename.replace("nested1", "container1"))
    }
  }

  @Test
  fun testUnsupportedAbisIgnored() {
    val basePath = prepareGradleProject(TestProjectToSnapshotPaths.BASIC_CMAKE_APP, "project")
    val buildFile = basePath.resolve("app").resolve("build.gradle")
    buildFile.writeText(buildFile.readText() + """
      android {
        defaultConfig {
          ndk {
            abiFilters 'invalidABIName'
          }
        }
      }
    """.trimIndent())

    openPreparedProject(
      "project",
      OpenPreparedProjectOptions(
        expectedSyncIssues = setOf(TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION),
        outputHandler = { },
        syncExceptionHandler = { fail() }
      )) { project ->
      val abis = GradleAndroidModel.get(project.findAppModule())!!.supportedAbis
      assertThat(abis).containsExactly(Abi.X86)
    }
  }

  @Test
  fun testGradleSourceSetModelClash() {
    val basePath = prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")
    val buildFile = basePath.resolve("app").resolve("build.gradle")
    buildFile.writeText(buildFile.readText() + """
      sourceSets {
        test.resources.srcDirs += 'src/test/resources'
      }
    """.trimIndent())

    openPreparedProject("project") { project ->
      val modules = ModuleManager.getInstance(project).modules
      assertThat(modules).hasLength(5)
      assertThat(modules.map(Module::getName).sorted()).containsExactly(
        "project", "project.app", "project.app.androidTest", "project.app.main", "project.app.unitTest")
      val unitTestModule = modules.first { it.name == "project.app.unitTest" }
      val roots = ModuleRootManager.getInstance(unitTestModule).contentRoots
      @Suppress("UnstableApiUsage")
      assertThat(roots.map { it.url }).doesNotContain(
        basePath.resolve("app/src/test/resources").toPath().toVirtualFileUrl(VirtualFileUrlManager.getInstance(project)).url)
    }
  }

  @Test
  fun testPsdDependencyAndroidToJavaModuleAndBack() {
    prepareGradleProject(TestProjectToSnapshotPaths.PSD_DEPENDENCY, "project")
    openPreparedProject("project") { project ->
      AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
      val beforeAndroidToJava = project.saveAndDump()
      val oldModuleCContent = WriteAction.compute<ByteArray, Throwable> {
        val jModuleMFile = project.guessProjectDir()?.findFileByRelativePath("jModuleM/build.gradle")!!
        val moduleCFile = project.guessProjectDir()?.findFileByRelativePath("moduleC/build.gradle")!!
        val moduleCContent = moduleCFile.contentsToByteArray()
        val jModuleMContent = jModuleMFile.contentsToByteArray()
        moduleCFile.setBinaryContent(jModuleMContent)
        moduleCContent
      }
      ApplicationManager.getApplication().saveAll()
      project.requestSyncAndWait()
      WriteAction.run<Throwable> {
        val moduleCFile = project.guessProjectDir()?.findFileByRelativePath("moduleC/build.gradle")!!
        moduleCFile.setBinaryContent(oldModuleCContent)
      }
      ApplicationManager.getApplication().saveAll()
      val textAfterSecondChange = project.syncAndDumpProject()
      assertThat(textAfterSecondChange).isEqualTo(
        beforeAndroidToJava
          .split("\n")
          .filter {
            // TODO(b/234815353): These snapshots are supposed to match without patching.
            !it.contains("WATCHED_TEST_SOURCE_FOLDER    : file://<PROJECT>/moduleC/src/test/java [-]") &&
              !it.contains("WATCHED_TEST_RESOURCE_FOLDER  : file://<PROJECT>/moduleC/src/test/resources [-]")
          }
          .joinToString("\n")
      )
    }
  }

  @Test
  fun testPsdDependencyDeleteModule() {
    prepareGradleProject(TestProjectToSnapshotPaths.PSD_DEPENDENCY, "project")
    openPreparedProject("project") { project ->
      val moduleName = project.gradleModule(":moduleB")!!.name
      assertThat(ModuleManager.getInstance(project).findModuleByName(moduleName)).isNotNull()
      PsProjectImpl(project).let { projectModel ->
        projectModel.removeModule(":moduleB")
        projectModel.applyChanges()
      }
      project.requestSyncAndWait()
      assertThat(ModuleManager.getInstance(project).findModuleByName(moduleName)).isNull()
    }
  }

  private fun Project.syncAndDumpProject(): String {
    requestSyncAndWait()
    return this.saveAndDump()
  }

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))
}

private fun Project.dumpModule(moduleName: String, sourcesetModule: Module.() -> Module?) =
      saveAndDump { project, dumper ->
        project.gradleModule(moduleName)!!.sourcesetModule()?.also {
          dumper.dump(it)
        }
      }

private fun <T : Any> T.asParsed() = ParsedValue.Set.Parsed(this, DslText.Literal)
