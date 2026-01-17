/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.testsuite.runconfiguration

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.IdeTestSuiteSource
import com.android.tools.idea.gradle.model.impl.IdeCustomSourceDirectoryImpl
import com.android.tools.idea.gradle.model.impl.IdeJUnitEngineInfoImpl
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteImpl
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteTargetImpl
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteVariantTargetImpl
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.TEST_SUITE_ASSETS_CUSTOM_SOURCE_DIRECTORY
import com.android.tools.idea.testartifacts.testsuite.TestSuiteTestUtils.createAssetsTestSuiteSource
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.createMainSourceProviderForDefaultTestProjectStructure
import com.android.tools.idea.util.toIoFile
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.resolveFromRootOrRelative
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TestSuiteUtilsTest {

  @get:Rule
  val rule =
    AndroidProjectRule.withAndroidModels(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(
        gradlePath = ":app",
        selectedBuildVariant = "debug",
        projectBuilder =
          AndroidProjectBuilder(
            projectType = { IdeAndroidProjectType.PROJECT_TYPE_APP },
            namespace = { "com.example.app" },
            mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() },
            testSuites = {
              listOf(
                IdeTestSuiteImpl(
                  name = "myTestSuite",
                  sources = listOf(
                    createAssetsTestSuiteSource(
                      testSuitePath = moduleBasePath.resolve("src/myTestSuite")
                    )
                  ),
                  junitEngineInfo = IdeJUnitEngineInfoImpl(includedEngines = setOf("engine1")),
                  targetedVariants = listOf("debug")
                ),
                IdeTestSuiteImpl(
                  name = "myTestSuiteWithoutTargets",
                  sources = listOf(
                    createAssetsTestSuiteSource(
                      testSuitePath = moduleBasePath.resolve("src/myTestSuiteWithoutTargets")
                    )
                  ),
                  junitEngineInfo = IdeJUnitEngineInfoImpl(includedEngines = setOf("engine1")),
                  targetedVariants = listOf("debug")
                ),
                IdeTestSuiteImpl(
                  name = "myTestSuiteWithNonTargetedVariant",
                  sources = listOf(
                    createAssetsTestSuiteSource(
                      testSuitePath = moduleBasePath.resolve("src/myTestSuiteWithNonTargetedVariant")
                    )
                  ),
                  junitEngineInfo = IdeJUnitEngineInfoImpl(includedEngines = setOf("engine1")),
                  targetedVariants = listOf("release")
                ),
                IdeTestSuiteImpl(
                  name = "myTestSuiteWithMultipleTargets",
                  sources = listOf(
                    createAssetsTestSuiteSource(
                      testSuitePath = moduleBasePath.resolve("src/myTestSuiteWithMultipleTargets")
                    )
                  ),
                  junitEngineInfo = IdeJUnitEngineInfoImpl(includedEngines = setOf("engine1")),
                  targetedVariants = listOf("debug")
                ),
              )
            },
            testSuiteArtifactsStub = { variant ->
              when (variant) {
                "debug" -> listOf(
                  IdeTestSuiteVariantTargetImpl(
                    suiteName = "myTestSuite",
                    targetedVariantName = "debug",
                    targets = listOf(
                      IdeTestSuiteTargetImpl(
                        targetName = "connectedTest", testTaskName = "myTestSuiteTaskName",
                        targetedDevices = emptyList()
                      )
                    )
                  ),
                  IdeTestSuiteVariantTargetImpl(
                    suiteName = "myTestSuiteWithoutTargets",
                    targetedVariantName = "debug",
                    targets = emptyList()
                  ),
                  IdeTestSuiteVariantTargetImpl(
                    suiteName = "myTestSuiteWithMultipleTargets",
                    targetedVariantName = "debug",
                    targets = listOf(
                      IdeTestSuiteTargetImpl(
                        targetName = "target1", testTaskName = "myTarget1TaskName",
                        targetedDevices = emptyList()
                      ),
                      IdeTestSuiteTargetImpl(
                        targetName = "target2", testTaskName = "myTarget2TaskName",
                        targetedDevices = emptyList()
                      ),
                      IdeTestSuiteTargetImpl(
                        targetName = "target3", testTaskName = "myTarget3TaskName",
                        targetedDevices = listOf("deviceId")
                      ),
                    )
                  )
                )

                "release" -> listOf(
                  IdeTestSuiteVariantTargetImpl(
                    suiteName = "myTestSuiteWithNonTargetedVariant",
                    targetedVariantName = "release",
                    targets = listOf(
                      IdeTestSuiteTargetImpl(
                        targetName = "connectedTest", testTaskName = "myTestSuiteWithNonTargetedVariantTaskName",
                        targetedDevices = emptyList()
                      )
                    )
                  )
                )

                else -> emptyList()
              }
            }
          ),
      ),
    )

  lateinit var testFile: PsiFile
  lateinit var testSuiteModule: Module
  lateinit var gradleAndroidModel: GradleAndroidModel

  @Before
  fun setUp() {
    testFile = rule.fixture.addFileToProject("app/src/myTestSuite/assets/test.xml", "")
    testSuiteModule = ModuleUtilCore.findModuleForFile(testFile.virtualFile, rule.project)!!
    gradleAndroidModel = GradleAndroidModel.get(testSuiteModule)!!
  }

  @Test
  fun getTestSuiteContainingFile_returnsTestSuite() {
    val testSuite = TestSuiteUtils.getTestSuiteContainingFile(gradleAndroidModel.testSuites, testFile.virtualFile)

    assertNotNull(testSuite)
    assertEquals("myTestSuite", testSuite.name)
    assertEquals(setOf("engine1"), testSuite.junitEngineInfo.includedEngines)
    assertEquals(listOf("debug"), testSuite.targetedVariants)
    assertEquals(IdeTestSuiteSource.SourceType.ASSETS, testSuite.sources.first().type)
    assertEquals("assets", testSuite.sources.first().name)

    val folder = rule.fixture.project.guessProjectDir()!!.resolveFromRootOrRelative("app/src/myTestSuite")!!.toIoFile()
    assertEquals(IdeSourceProvider(
      name = "assets",
      folder = folder,
      manifestFile = "AndroidManifest.xml",
      javaDirectories = emptyList(),
      kotlinDirectories = emptyList(),
      resourcesDirectories = emptyList(),
      aidlDirectories = emptyList(),
      renderscriptDirectories = emptyList(),
      resDirectories = emptyList(),
      assetsDirectories = emptyList(),
      jniLibsDirectories = emptyList(),
      shadersDirectories = emptyList(),
      mlModelsDirectories = emptyList(),
      customSourceDirectories = listOf(
        IdeCustomSourceDirectoryImpl(
          sourceTypeName = TEST_SUITE_ASSETS_CUSTOM_SOURCE_DIRECTORY,
          myFolder = folder,
          path = "."
        )
      ),
      baselineProfileDirectories = emptyList(),
      keepRulesDirectoriesField = emptyList(),
    ), testSuite.sources.first().sourceProvider)
  }

  @Test
  fun getTestSuiteContainingFile_returnsNull_whenFileNotInTestSuite() {
    val nonTestSuiteFile = rule.fixture
      .addFileToProject("app/src/main/java/com/example/app/MainActivity.kt", "")

    val testSuite = TestSuiteUtils.getTestSuiteContainingFile(gradleAndroidModel.testSuites, nonTestSuiteFile.virtualFile)

    assertNull(testSuite)
  }

  @Test
  fun getTestSuiteContainingFile_returnsNull_whenVirtualFileNotInLocalFileSystem() {
    val virtualFile = LightVirtualFile("test.xml")

    val testSuite = TestSuiteUtils.getTestSuiteContainingFile(gradleAndroidModel.testSuites, virtualFile)

    assertNull(testSuite)
  }

  @Test
  fun getTestSuiteContainingFile_returnsNull_forRootTestSuiteDirectory() {
    val testSuiteRootDirectory = rule.fixture.findFileInTempDir("app/src/myTestSuite")

    val testSuite = TestSuiteUtils.getTestSuiteContainingFile(gradleAndroidModel.testSuites, testSuiteRootDirectory)

    assertNull(testSuite)
  }

  @Test
  fun getTestSuiteAtRoot_returnsTestSuite_whenFileIsTestSuiteRootDir() {
    val testSuiteRootDirectory = rule.fixture.findFileInTempDir("app/src/myTestSuite")

    val testSuite = TestSuiteUtils.getTestSuiteAtRoot(gradleAndroidModel.testSuites, testSuiteRootDirectory)

    assertNotNull(testSuite)
    assertEquals("myTestSuite", testSuite.name)
  }

  @Test
  fun getTestSuiteAtRoot_returnsNull_whenFileIsNotTestSuiteRootDir() {
    val nonRootTestSuiteDirectory = rule.fixture.findFileInTempDir("app/src")

    val testSuite = TestSuiteUtils.getTestSuiteAtRoot(gradleAndroidModel.testSuites, nonRootTestSuiteDirectory)

    assertNull(testSuite)
  }

  @Test
  fun getTestSuiteAtRoot_returnsNull_whenFileIsSubDirectoryOfTestSuiteRoot() {
    val testSuiteSubdirectory = rule.fixture.findFileInTempDir("app/src/myTestSuite/assets")

    val testSuite = TestSuiteUtils.getTestSuiteAtRoot(gradleAndroidModel.testSuites, testSuiteSubdirectory)

    assertNull(testSuite)
  }

  @Test
  fun getTestSuiteRoot_returnsTestSuiteRoot() {
    val testSuiteRoot = TestSuiteUtils.getTestSuiteRoot(testSuiteModule)

    assertNotNull(testSuiteRoot)
    assertEquals(rule.fixture.project.guessProjectDir()!!.resolveFromRootOrRelative("app/src/myTestSuite")!!.toIoFile(), testSuiteRoot)
  }

  @Test
  fun getTestSuiteRoot_returnsNull_whenModuleIsNotTestSuiteModule() {
    val appModule = ModuleUtilCore.findModuleForFile(testFile.virtualFile.parent.parent.parent, rule.project)!!
    val testSuiteRoot = TestSuiteUtils.getTestSuiteRoot(appModule)

    assertNull(testSuiteRoot)
  }

  @Test
  fun getTestSuiteTargets_returnsSingleTarget() {
    val targets = TestSuiteUtils.getTestSuiteTargets(gradleAndroidModel.selectedVariant, "myTestSuite")

    assertEquals(1, targets.size)
    assertEquals("connectedTest", targets.first().targetName)
    assertEquals("myTestSuiteTaskName", targets.first().testTaskName)
  }

  @Test
  fun getTestSuiteTargets_ignoresTargetsWithDevices() {
    val targets = TestSuiteUtils.getTestSuiteTargets(gradleAndroidModel.selectedVariant, "myTestSuiteWithMultipleTargets")

    assertEquals(2, targets.size)
    assertEquals("target1", targets.first().targetName)
    assertEquals("myTarget1TaskName", targets.first().testTaskName)
    assertEquals("target2", targets[1].targetName)
    assertEquals("myTarget2TaskName", targets[1].testTaskName)
  }

  @Test
  fun getTestSuiteTaskName_returnsNull_whenTestSuiteNotFound() {
    val targets = TestSuiteUtils.getTestSuiteTargets(gradleAndroidModel.selectedVariant, "unknownTestSuite")

    assertTrue(targets.isEmpty())
  }

  @Test
  fun getTestSuiteTaskName_returnsNull_whenNoTargetsConfiguredOnTestSuite() {
    val targets = TestSuiteUtils.getTestSuiteTargets(gradleAndroidModel.selectedVariant, "myTestSuiteWithoutTargets")

    assertTrue(targets.isEmpty())
  }

  @Test
  fun getTestSuiteTaskName_returnsNull_whenNonTargetedVariantSelected() {
    val targets = TestSuiteUtils.getTestSuiteTargets(gradleAndroidModel.selectedVariant, "myTestSuiteWithNonTargetedVariant")

    assertTrue(targets.isEmpty())
  }

  @Test
  fun getTestSuiteModule_returnsModule() {
    val runConfiguration = TestSuiteRunConfiguration(
      rule.project,
      TestSuiteRunConfigurationType().configurationFactories[0],
      "Test Suite Config"
    )
    runConfiguration.addTaskName("myTestSuiteTaskName")
    runConfiguration.settings.externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(testSuiteModule)!!

    val module = TestSuiteUtils.getTestSuiteModule(runConfiguration)

    assertNotNull(module)
    assertEquals(testSuiteModule, module)
  }

  @Test
  fun getTestSuiteModule_returnsNull_whenTaskNameNotFound() {
    val runConfiguration = TestSuiteRunConfiguration(
      rule.project,
      TestSuiteRunConfigurationType().configurationFactories[0],
      "Test Suite Config"
    )
    runConfiguration.addTaskName("unknownTaskName")
    runConfiguration.settings.externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(testSuiteModule)!!

    val module = TestSuiteUtils.getTestSuiteModule(runConfiguration)

    assertNull(module)
  }

  @Test
  fun getTestSuiteModule_returnsNull_whenExternalProjectPathNotFound() {
    val runConfiguration = TestSuiteRunConfiguration(
      rule.project,
      TestSuiteRunConfigurationType().configurationFactories[0],
      "Test Suite Config"
    )
    runConfiguration.addTaskName("myTestSuiteTaskName")
    runConfiguration.settings.externalProjectPath = "/unknown/path"

    val module = TestSuiteUtils.getTestSuiteModule(runConfiguration)

    assertNull(module)
  }
}