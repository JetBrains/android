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
package com.android.screenshottest.producers

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.IdeTestSuiteSource
import com.android.tools.idea.gradle.model.impl.IdeJUnitEngineInfoImpl
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteImpl
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteSourceImpl
import com.android.tools.idea.gradle.model.impl.toImpl
import com.android.tools.idea.gradle.project.entities.GradleAndroidModelEntityId
import com.android.tools.idea.gradle.project.entities.GradleModuleModelEntity
import com.android.tools.idea.gradle.project.entities.gradleModuleModel
import com.android.tools.idea.gradle.project.entities.modifyGradleAndroidModelEntity
import com.android.tools.idea.gradle.project.model.GradleAndroidModelImpl
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil
import com.android.tools.idea.testartifacts.createAndroidGradleTestConfigurationFromClass
import com.android.tools.idea.testartifacts.createAndroidGradleTestConfigurationFromDirectory
import com.android.tools.idea.testartifacts.createAndroidGradleTestConfigurationFromMethod
import com.android.tools.idea.testartifacts.testsuite.GradleRunConfigurationExtension.BooleanOptions.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.actions.ConfigurationFromContextImpl
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.RunsInEdt
import java.io.File
import kotlin.test.assertEquals
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ScreenshotTestGradleRunConfigurationProducersTest {
  @get:Rule val flagRule = FlagRule(StudioFlags.ENABLE_SCREENSHOT_TESTING, true)

  @get:Rule val projectRule = AndroidGradleProjectRule().onEdt()

  private val SIMPLE_SCREENSHOT = "app/src/screenshotTest/java/com/example/application/MyScreenshotTest.kt"
  private val NO_PREVIEW_TEST = "app/src/screenshotTest/java/com/example/application/NoPreviewTest.kt"
  private val ONLY_PREVIEW_TEST = "app/src/screenshotTest/java/com/example/application/OnlyPreviewTest.kt"
  private val MULTI_PREVIEW = "app/src/screenshotTest/java/com/example/application/MyScreenshotTestMultiPreview.kt"
  private val DIFFERENT_PACKAGE = "app/src/screenshotTest/java/com/example/package/MyScreenshotTest.kt"
  private val EMPTY_CLASS = "app/src/screenshotTest/java/com/example/application/MyEmptyClass.kt"
  private val NO_PREVIEWS = "app/src/screenshotTest/java/com/example/application/NoPreviewsClass.kt"
  private val TOP_LEVEL = "app/src/screenshotTest/java/com/example/application/MyScreenshotTestTopLevel.kt"
  private val MIXED_TESTS_FILE = "app/src/screenshotTest/java/com/example/application/MyFileWithMixedTests.kt"

  @Before
  fun setUp() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APP_WITH_SCREENSHOT_TEST)
    stubComposeAnnotation()
    stubPreviewAnnotation()
    stubPreviewTestAnnotation()
    createProjectStructureForTest()
    val screenshotTestDir = findFileByIoFile(File(projectRule.project.basePath + "/app/src/screenshotTest"), true)
    PsiTestUtil.addSourceRoot(projectRule.fixture.module, screenshotTestDir!!, true)
  }

  @Test
  fun testConfigurationFromClass() {
    val project = projectRule.project
    val runConfiguration = createAndroidGradleTestConfigurationFromClass(project, "com.example.application.MyScreenshotTest")
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:testScreenshotTestDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.application.MyScreenshotTest\"", runConfiguration.settings.taskNames[2])
  }

  @Test
  fun testConfigurationFromFile_includesClassAndTopLevelTests() {
    val project = projectRule.project
    // Get the PsiFile for the test file.
    val psiFile = TestConfigurationTestingUtil.getPsiElement(project, MIXED_TESTS_FILE, false)
    val context = TestConfigurationTestingUtil.createContext(project, psiFile)
    val runConfiguration = context.configuration?.configuration as? GradleRunConfiguration

    requireNotNull(runConfiguration) { "Run configuration should not be null for file context" }

    assertEquals("Screenshot Tests in MyFileWithMixedTests.kt", runConfiguration.name)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)

    val taskNames = runConfiguration.settings.taskNames
    assertThat(taskNames).contains(":app:testScreenshotTestDefaultDebugTestSuite")

    // Check that we have the correct number of task arguments.
    // 1 for the task, plus 2 for each "--tests" filter (2 filters total) = 5.
    assertThat(taskNames).hasSize(5)

    // Drop the base task and chunk the remaining arguments into pairs like [--tests, "filter"].
    val testFilters = taskNames.drop(1).chunked(2).filter { it.size == 2 && it[0] == "--tests" }.map { it[1] }

    // Verify that we have exactly the two expected filters.
    val expectedFilters = listOf("\"com.example.application.MyClassInMixedFile\"", "\"com.example.application.MyFileWithMixedTestsKt\"")
    assertThat(testFilters).hasSize(expectedFilters.size)
    assertThat(testFilters).containsAllIn(expectedFilters)
  }

  @Test
  fun testConfigurationFromEditor_fallbackToFile() {
    val project = projectRule.project
    val psiFile = TestConfigurationTestingUtil.getPsiElement(project, MIXED_TESTS_FILE, false)
    val elementInFile = psiFile.findElementAt(0)
    requireNotNull(elementInFile) { "Element at offset 0 should not be null" }

    val context = TestConfigurationTestingUtil.createContext(project, elementInFile)
    val runConfiguration = context.configuration?.configuration as? GradleRunConfiguration

    requireNotNull(runConfiguration) { "Run configuration should not be null for element in file" }

    assertEquals("Screenshot Tests in MyFileWithMixedTests.kt", runConfiguration.name)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)

    val taskNames = runConfiguration.settings.taskNames
    assertThat(taskNames).contains(":app:testScreenshotTestDefaultDebugTestSuite")
    assertThat(taskNames).hasSize(5)

    val testFilters = taskNames.drop(1).chunked(2).filter { it.size == 2 && it[0] == "--tests" }.map { it[1] }
    val expectedFilters = listOf("\"com.example.application.MyClassInMixedFile\"", "\"com.example.application.MyFileWithMixedTestsKt\"")
    assertThat(testFilters).hasSize(expectedFilters.size)
    assertThat(testFilters).containsAllIn(expectedFilters)
  }

  @Test
  fun testConfigurationFromClassNoPreviewTest() {
    val project = projectRule.project
    val runConfiguration = createAndroidGradleTestConfigurationFromClass(project, "com.example.application.NoPreviewTest")
    Assert.assertNull(runConfiguration)
  }

  @Test
  fun testConfigurationFromClassOnlyPreviewTest() {
    val project = projectRule.project
    val runConfiguration = createAndroidGradleTestConfigurationFromClass(project, "com.example.application.OnlyPreviewTest")
    Assert.assertNotNull(runConfiguration)
    assertEquals(true, runConfiguration!!.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:testScreenshotTestDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.application.OnlyPreviewTest\"", runConfiguration.settings.taskNames[2])
  }

  @Test
  fun testConfigurationFromClassEmptyClass() {
    val project = projectRule.project
    val runConfiguration = createAndroidGradleTestConfigurationFromClass(project, "com.example.application.MyEmptyClass")
    Assert.assertNull(runConfiguration)
  }

  @Test
  fun testConfigurationFromClassNoPreviewMethods() {
    val project = projectRule.project
    val runConfiguration = createAndroidGradleTestConfigurationFromClass(project, "com.example.application.NoPreviewsClass")
    Assert.assertNull(runConfiguration)
  }

  @Test
  fun testConfigurationFromMethod() {
    val project = projectRule.project
    val runConfiguration =
      createAndroidGradleTestConfigurationFromMethod(project, "com.example.application.MyScreenshotTest", "PreviewMethod")
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:testScreenshotTestDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.application.MyScreenshotTest.PreviewMethod\"", runConfiguration.settings.taskNames[2])
  }

  @Test
  fun testConfigurationFromMethodNoPreviewTest() {
    val project = projectRule.project
    val runConfiguration = createAndroidGradleTestConfigurationFromMethod(project, "com.example.application.NoPreviewTest", "PreviewMethod")
    Assert.assertNull(runConfiguration)
  }

  @Test
  fun testConfigurationFromMethodOnlyPreviewTest() {
    val project = projectRule.project
    val runConfiguration =
      createAndroidGradleTestConfigurationFromMethod(project, "com.example.application.OnlyPreviewTest", "PreviewMethod")
    Assert.assertNotNull(runConfiguration)
    assertEquals(true, runConfiguration!!.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:testScreenshotTestDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.application.OnlyPreviewTest.PreviewMethod\"", runConfiguration.settings.taskNames[2])
  }

  @Test
  fun testConfigurationFromMethodMultiPreview() {
    val project = projectRule.project
    val runConfiguration =
      createAndroidGradleTestConfigurationFromMethod(project, "com.example.application.MyScreenshotTestMultiPreview", "PreviewMethod")
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:testScreenshotTestDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.application.MyScreenshotTestMultiPreview.PreviewMethod\"", runConfiguration.settings.taskNames[2])
  }

  @Test
  fun testConfigurationFromMethodTopLevel() {
    val project = projectRule.project
    val runConfiguration =
      createAndroidGradleTestConfigurationFromMethod(project, "com.example.application.MyScreenshotTestTopLevelKt", "PreviewMethod")
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:testScreenshotTestDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.application.MyScreenshotTestTopLevelKt.PreviewMethod\"", runConfiguration.settings.taskNames[2])
  }

  @Test
  fun testConfigurationFromPackage() {
    val project = projectRule.project
    val runConfiguration = createAndroidGradleTestConfigurationFromDirectory(project, "app/src/screenshotTest/java/com/example")
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:testScreenshotTestDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.*\"", runConfiguration.settings.taskNames[2])
  }

  @Test
  fun testConfigurationProducerFromPackage() {
    val project = projectRule.project
    val psiFile = TestConfigurationTestingUtil.getPsiElement(project, "app/src/screenshotTest/java/com/example", true)
    val context = TestConfigurationTestingUtil.createContext(project, psiFile)
    val contextConfiguration = context.configurationsFromContext?.firstOrNull() as ConfigurationFromContextImpl
    assertThat(contextConfiguration.configurationProducer).isInstanceOf(ScreenshotTestAllInPackageGradleConfigurationProducer::class.java)
  }

  @Test
  fun testConfigurationFromSubPackage() {
    val project = projectRule.project
    val runConfiguration = createAndroidGradleTestConfigurationFromDirectory(project, "app/src/screenshotTest/java/com/example/package")
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:testScreenshotTestDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.package.*\"", runConfiguration.settings.taskNames[2])
  }

  @Test
  fun testConfigurationProducerFromSubPackage() {
    val project = projectRule.project
    val psiFile = TestConfigurationTestingUtil.getPsiElement(project, "app/src/screenshotTest/java/com/example/package", true)
    val context = TestConfigurationTestingUtil.createContext(project, psiFile)
    val contextConfiguration = context.configurationsFromContext?.firstOrNull() as ConfigurationFromContextImpl
    assertThat(contextConfiguration.configurationProducer).isInstanceOf(ScreenshotTestAllInPackageGradleConfigurationProducer::class.java)
  }

  @Test
  fun testConfigurationFromAppDirectory() {
    val project = projectRule.project
    // test app directory
    val psiFile = TestConfigurationTestingUtil.getPsiElement(project, "app", true)
    val context = TestConfigurationTestingUtil.createContext(project, psiFile)
    val contextConfiguration =
      context.configurationsFromContext?.firstOrNull { it.configuration.name.contains("Screenshot") } as ConfigurationFromContextImpl?
    val runConfiguration = contextConfiguration!!.configuration as GradleRunConfiguration
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(1, runConfiguration.settings.taskNames.size)
    assertEquals(":app:testScreenshotTestDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
    assertThat(contextConfiguration.configurationProducer).isInstanceOf(ScreenshotTestAllInDirectoryGradleConfigurationProducer::class.java)
  }

  @Test
  fun testConfigurationFromSrcDirectory() {
    val project = projectRule.project
    val psiFile = TestConfigurationTestingUtil.getPsiElement(project, "app/src", true)
    val context = TestConfigurationTestingUtil.createContext(project, psiFile)
    val contextConfiguration =
      context.configurationsFromContext?.firstOrNull { it.configuration.name.contains("Screenshot") } as ConfigurationFromContextImpl?
    val runConfiguration = contextConfiguration!!.configuration as GradleRunConfiguration
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(1, runConfiguration.settings.taskNames.size)
    assertEquals(":app:testScreenshotTestDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
    assertThat(contextConfiguration.configurationProducer).isInstanceOf(ScreenshotTestAllInDirectoryGradleConfigurationProducer::class.java)

    // Compare generated run-config against unrelated context. It should return false.
    // This should not cause NPE.
    assertThat(
        contextConfiguration.configurationProducer.isConfigurationFromContext(
          runConfiguration,
          TestConfigurationTestingUtil.createContext(project, TestConfigurationTestingUtil.getPsiElement(project, "nonAndroidModule", true)),
        )
      )
      .isFalse()
  }

  @Test
  fun testConfigurationFromSSTSourceSetDirectory() {
    val project = projectRule.project
    val psiFile = TestConfigurationTestingUtil.getPsiElement(project, "app/src/screenshotTest/java", true)
    val context = TestConfigurationTestingUtil.createContext(project, psiFile)
    val contextConfiguration =
      context.configurationsFromContext?.firstOrNull { it.configuration.name.contains("Screenshot") } as ConfigurationFromContextImpl?
    val runConfiguration = contextConfiguration!!.configuration as GradleRunConfiguration
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(1, runConfiguration.settings.taskNames.size)
    assertEquals(":app:testScreenshotTestDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
    assertThat(contextConfiguration.configurationProducer).isInstanceOf(ScreenshotTestAllInDirectoryGradleConfigurationProducer::class.java)
  }

  @Test
  fun testConfigurationFromOtherSourceSetDirectory() {
    val project = projectRule.project
    val psiFile = TestConfigurationTestingUtil.getPsiElement(project, "app/src/main/java", true)
    val context = TestConfigurationTestingUtil.createContext(project, psiFile)
    val contextConfiguration =
      context.configurationsFromContext?.firstOrNull { it.configuration.name.contains("Screenshot") } as ConfigurationFromContextImpl?
    Assert.assertNull(contextConfiguration)
  }

  @Test
  fun runConfigProducerShouldNotCrashForNonAndroidModule() {
    val project = projectRule.project
    val psiFile = TestConfigurationTestingUtil.getPsiElement(project, "nonAndroidModule", true)

    // This should not cause NPE.
    TestConfigurationTestingUtil.createContext(project, psiFile).configuration
  }

  private fun createProjectStructureForTest() {
    // simple screenshotTest
    createRelativeFileWithContent(
      SIMPLE_SCREENSHOT,
      """
      package com.example.application

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview
      import com.android.tools.screenshot.PreviewTest

      class MyScreenshotTest {
        @PreviewTest
        @Preview(showBackground = true)
        @Composable
        fun PreviewMethod() {
        }
        
        @PreviewTest
        @Preview(showBackground = true)
        @Composable
        fun AnotherPreviewMethod() {
        }
      }

      """
        .trimIndent(),
    )

    // without preview test annotation
    createRelativeFileWithContent(
      NO_PREVIEW_TEST,
      """
      package com.example.application

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview
      import com.android.tools.screenshot.PreviewTest

      class NoPreviewTest {
        @Preview(showBackground = true)
        @Composable
        fun PreviewMethod() {
        }
        
        @Preview(showBackground = true)
        @Composable
        fun AnotherPreviewMethod() {
        }
      }

      """
        .trimIndent(),
    )

    // only preview test annotation - no preview annotation
    createRelativeFileWithContent(
      ONLY_PREVIEW_TEST,
      """
      package com.example.application

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview
      import com.android.tools.screenshot.PreviewTest

      class OnlyPreviewTest {
        @PreviewTest
        @Composable
        fun PreviewMethod() {
        }
        
        @PreviewTest
        @Composable
        fun AnotherPreviewMethod() {
        }
      }

      """
        .trimIndent(),
    )

    // Multi preview annotations
    createRelativeFileWithContent(
      MULTI_PREVIEW,
      """
      package com.example.application

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview
      import com.android.tools.screenshot.PreviewTest

      class MyScreenshotTestMultiPreview {
        @PreviewTest
        @Preview(showBackground = true)
        @Composable
        fun PreviewMethod() {
        }
        
        @PreviewTest
        @MultiPreview
        @Composable
        fun AnotherPreviewMethod() {
        }
      }

      @Preview(name = "with background", showBackground = true)
      @Preview(name = "without background", showBackground = false)
      annotation class MultiPreview

      """
        .trimIndent(),
    )

    // file in a different package
    createRelativeFileWithContent(
      DIFFERENT_PACKAGE,
      """
      package com.example.application

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview
      import com.android.tools.screenshot.PreviewTest

      class MyScreenshotTest {
        @PreviewTest
        @Preview(showBackground = true)
        @Composable
        fun PreviewMethod() {
        }
        
        @PreviewTest
        @Preview(showBackground = true)
        @Composable
        fun AnotherPreviewMethod() {
        }
      }

      """
        .trimIndent(),
    )

    // class with no methods
    createRelativeFileWithContent(
      EMPTY_CLASS,
      """
      package com.example.application

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      class MyEmptyClass {
      }

      """
        .trimIndent(),
    )

    // class with no preview methods
    createRelativeFileWithContent(
      NO_PREVIEWS,
      """
      package com.example.application

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      class NoPreviewsClass {
        @Composable
        fun PreviewMethod() {
        }

        @Composable
        fun AnotherPreviewMethod() {
        }
      }

      """
        .trimIndent(),
    )

    // top level function
    createRelativeFileWithContent(
      TOP_LEVEL,
      """
      package com.example.application

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview
      import com.android.tools.screenshot.PreviewTest

      @PreviewTest
      @Preview(showBackground = true)
      @Composable
      fun PreviewMethod() {
      }

      """
        .trimIndent(),
    )

    // File with both a top-level test and a test in a class
    createRelativeFileWithContent(
      MIXED_TESTS_FILE,
      """
      package com.example.application

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview
      import com.android.tools.screenshot.PreviewTest

      @PreviewTest
      @Preview
      @Composable
      fun TopLevelTestInMixedFile() {
      }

      class MyClassInMixedFile {
        @PreviewTest
        @Preview
        @Composable
        fun ClassTestInMixedFile() {
        }
      }
      """
        .trimIndent(),
    )
  }

  private fun stubPreviewTestAnnotation() {
    createRelativeFileWithContent(
      "app/src/screenshotTest/java/com/android/tools/screenshot/PreviewTest.kt",
      """
      package com.android.tools.screenshot

      @MustBeDocumented
      @Retention(AnnotationRetention.BINARY)
      @Target(
          AnnotationTarget.FUNCTION
      )
      annotation class PreviewTest {
      }
          
      """
        .trimIndent(),
    )
  }

  private fun stubComposeAnnotation() {
    createRelativeFileWithContent(
      "app/src/screenshotTest/java/androidx/compose/runtime/Composable.kt",
      """
      package androidx.compose.runtime
      @Target(
          AnnotationTarget.FUNCTION,
          AnnotationTarget.TYPE_USAGE,
          AnnotationTarget.TYPE,
          AnnotationTarget.TYPE_PARAMETER,
          AnnotationTarget.PROPERTY_GETTER
      )
      annotation class Composable
      """
        .trimIndent(),
    )
  }

  private fun stubPreviewAnnotation() {
    createRelativeFileWithContent(
      "app/src/screenshotTest/java/androidx/compose/ui/tooling/preview/Preview.kt",
      """
  package androidx.compose.ui.tooling.preview

  import kotlin.reflect.KClass

  object Devices {
      const val DEFAULT = ""

      const val NEXUS_7 = "id:Nexus 7"
      const val NEXUS_10 = "name:Nexus 10"
  }


  @Repeatable
  annotation class Preview(
    val name: String = "",
    val group: String = "",
    val apiLevel: Int = -1,
    val theme: String = "",
    val widthDp: Int = -1,
    val heightDp: Int = -1,
    val locale: String = "",
    val fontScale: Float = 1f,
    val showDecoration: Boolean = false,
    val showBackground: Boolean = false,
    val backgroundColor: Long = 0,
    val uiMode: Int = 0,
    val device: String = ""
  )

  interface PreviewParameterProvider<T> {
      val values: Sequence<T>
      val count get() = values.count()
  }

  annotation class PreviewParameter(
      val provider: KClass<out PreviewParameterProvider<*>>,
      val limit: Int = Int.MAX_VALUE
  )
  """,
    )
  }

  private fun createRelativeFileWithContent(relativePath: String, content: String): File {
    val newFile = File(projectRule.project.basePath, FileUtils.toSystemDependentPath(relativePath))
    FileUtil.createIfDoesntExist(newFile)
    newFile.writeText(content)
    return newFile
  }

  private fun setModuleTasks(tasks: List<String>, hasComposeScreenshotPlugin: Boolean = false) {
    val project = projectRule.project
    val modules = ModuleManager.getInstance(project).modules
    runWriteAction {
      project.workspaceModel.updateProjectModel("Set GradleModuleModel") { storage ->
        for (module in modules) {
          val entity = storage.resolve(ModuleId(module.name)) ?: continue
          val model =
            GradleModuleModel(
              moduleNameField = module.name,
              testTasks = emptyList(),
              allTasks = tasks,
              gradlePath = if (module.name.endsWith(".lib")) ":lib" else ":app",
              rootFolderPath = File(project.basePath!!).toImpl(),
              buildFilePath = File(project.basePath!!, "build.gradle").toImpl(),
              gradleVersion = "8.0",
              agpVersion = "8.0.0",
              safeArgsJava = false,
              safeArgsKotlin = false,
              hasFtlPlugin = false,
              hasLegacyKaptPlugin = false,
              hasComposeScreenshotPlugin = hasComposeScreenshotPlugin,
            )
          entity.gradleModuleModel?.let { storage.removeEntity(it) }
          storage.modifyModuleEntity(entity) { this.gradleModuleModel = GradleModuleModelEntity(model, entity.entitySource) }
        }
      }
    }
  }

  @Test
  fun testLegacyPluginTaskResolution() {
    setModuleTasks(listOf("validateDebugScreenshotTest"), hasComposeScreenshotPlugin = true)
    val project = projectRule.project
    val runConfiguration = createAndroidGradleTestConfigurationFromClass(project, "com.example.application.MyScreenshotTest")
    requireNotNull(runConfiguration)
    assertEquals(":app:validateDebugScreenshotTest", runConfiguration.settings.taskNames[0])
  }

  @Test
  fun testTestSuiteTaskResolution() {
    setModuleTasks(listOf("testScreenshotTestDefaultDebugTestSuite"), hasComposeScreenshotPlugin = false)
    val project = projectRule.project
    val runConfiguration = createAndroidGradleTestConfigurationFromClass(project, "com.example.application.MyScreenshotTest")
    requireNotNull(runConfiguration)
    assertEquals(":app:testScreenshotTestDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
  }

  private fun createHostJarTestSuiteSource(testSuitePath: File): IdeTestSuiteSourceImpl {
    return IdeTestSuiteSourceImpl(
      name = "hostJar",
      type = IdeTestSuiteSource.SourceType.HOST_JAR,
      sourceProvider =
        IdeSourceProvider(
          name = "hostJar",
          folder = testSuitePath,
          manifestFile = "AndroidManifest.xml",
          javaDirectories = listOf(testSuitePath.absolutePath),
          kotlinDirectories = listOf(testSuitePath.absolutePath),
          resourcesDirectories = emptyList(),
          aidlDirectories = emptyList(),
          renderscriptDirectories = emptyList(),
          resDirectories = emptyList(),
          assetsDirectories = emptyList(),
          jniLibsDirectories = emptyList(),
          shadersDirectories = emptyList(),
          mlModelsDirectories = emptyList(),
          customSourceDirectories = emptyList(),
          baselineProfileDirectories = emptyList(),
          keepRulesDirectoriesField = emptyList(),
          aarKeepRulesDirectoriesField = emptyList(),
        ),
    )
  }

  private fun withCustomScreenshotTestSuites(suites: List<IdeTestSuiteImpl>, action: () -> Unit) {
    val project = projectRule.project
    val modules = ModuleManager.getInstance(project).modules
    val originalModels = mutableMapOf<ModuleId, GradleAndroidModelImpl>()
    try {
      runWriteAction {
        project.workspaceModel.updateProjectModel("Set custom testSuites") { storage ->
          for (module in modules) {
            val moduleId = ModuleId(module.name)
            val entity = storage.resolve(GradleAndroidModelEntityId(moduleId)) ?: continue
            val originalModel = entity.gradleAndroidModel
            originalModels[moduleId] = originalModel
            val newAndroidProject = originalModel.data.androidProject.copy(testSuites = suites)
            val newData = originalModel.data.copy(androidProject = newAndroidProject)
            val customModel = GradleAndroidModelImpl(newData)
            storage.modifyGradleAndroidModelEntity(entity) { gradleAndroidModel = customModel }
          }
        }
      }
      action()
    } finally {
      runWriteAction {
        project.workspaceModel.updateProjectModel("Restore testSuites") { storage ->
          for ((moduleId, originalModel) in originalModels) {
            val entity = storage.resolve(GradleAndroidModelEntityId(moduleId)) ?: continue
            storage.modifyGradleAndroidModelEntity(entity) { gradleAndroidModel = originalModel }
          }
        }
      }
    }
  }

  @Test
  fun testConfigurationFromCustomTestSuite() {
    setModuleTasks(listOf("testCustomScreenshotSuiteDefaultDebugTestSuite"), hasComposeScreenshotPlugin = false)
    val project = projectRule.project
    val customSuite =
      IdeTestSuiteImpl(
        name = "customScreenshotSuite",
        sources = listOf(createHostJarTestSuiteSource(File(project.basePath!!, "app/src/screenshotTest"))),
        junitEngineInfo = IdeJUnitEngineInfoImpl(includedEngines = setOf("preview-screenshot-test-engine")),
        targetedVariants = listOf("debug"),
      )
    withCustomScreenshotTestSuites(listOf(customSuite)) {
      val runConfiguration = createAndroidGradleTestConfigurationFromClass(project, "com.example.application.MyScreenshotTest")
      requireNotNull(runConfiguration)
      assertEquals(":app:testCustomScreenshotSuiteDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
    }
  }

  @Test
  fun testConfigurationFromMultiSuiteModuleDirectory() {
    setModuleTasks(
      listOf("testUiScreenshotSuiteDefaultDebugTestSuite", "testFeatureScreenshotSuiteDefaultDebugTestSuite"),
      hasComposeScreenshotPlugin = false,
    )
    val project = projectRule.project
    val uiSuite =
      IdeTestSuiteImpl(
        name = "uiScreenshotSuite",
        sources = listOf(createHostJarTestSuiteSource(File(project.basePath!!, "app/src/uiScreenshotTest"))),
        junitEngineInfo = IdeJUnitEngineInfoImpl(includedEngines = setOf("preview-screenshot-test-engine")),
        targetedVariants = listOf("debug"),
      )
    val featureSuite =
      IdeTestSuiteImpl(
        name = "featureScreenshotSuite",
        sources = listOf(createHostJarTestSuiteSource(File(project.basePath!!, "app/src/featureScreenshotTest"))),
        junitEngineInfo = IdeJUnitEngineInfoImpl(includedEngines = setOf("preview-screenshot-test-engine")),
        targetedVariants = listOf("debug"),
      )
    withCustomScreenshotTestSuites(listOf(uiSuite, featureSuite)) {
      val psiFile = TestConfigurationTestingUtil.getPsiElement(project, "app", true)
      val context = TestConfigurationTestingUtil.createContext(project, psiFile)
      val contextConfiguration =
        context.configurationsFromContext?.firstOrNull { it.configuration.name.contains("Screenshot") } as ConfigurationFromContextImpl?
      val runConfiguration = contextConfiguration!!.configuration as GradleRunConfiguration
      requireNotNull(runConfiguration)
      assertEquals(2, runConfiguration.settings.taskNames.size)
      assertEquals(":app:testUiScreenshotSuiteDefaultDebugTestSuite", runConfiguration.settings.taskNames[0])
      assertEquals(":app:testFeatureScreenshotSuiteDefaultDebugTestSuite", runConfiguration.settings.taskNames[1])
    }
  }
}
