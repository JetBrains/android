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
package com.android.tools.idea.testartifacts.testsuite.temp

import com.android.tools.idea.testartifacts.TestConfigurationTesting
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.build.BuildView
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PsiTestUtil
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GradleAndroidTestsExecutionConsoleManagerTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APP_WITH_SCREENSHOT_TEST)
    stubComposeAnnotation()
    stubPreviewAnnotation()
    createProjectStructureForTest()
    val screenshotTestDir = requireNotNull(
      VfsUtil.findFileByIoFile(File(projectRule.project.basePath + "/app/src/screenshotTest"), true)
    )
    PsiTestUtil.addSourceRoot(projectRule.fixture.module, screenshotTestDir, true)
  }

  @Test
  fun runScreenshotTestWithAndroidTestSuiteView() {
    val latch = CountDownLatch(1)
    var processStarted = false
    var executionConsole: ExecutionConsole? = null

    ApplicationManager.getApplication().invokeAndWait {
      val project = projectRule.project
      val qualifiedName = "com.example.application.MyScreenshotTest"
      val methodName = "PreviewMethod"
      val psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project))
      requireNotNull(psiClass) { "PsiClass for $qualifiedName was not found." }
      val psiMethod = psiClass.methods.firstOrNull{ it.name == methodName }
      requireNotNull(psiMethod) { "PsiMethod for $methodName was not found." }

      val runnerAndConfig = createRunnerAndConfigurationSettingsFromPsiElement(project, psiMethod as PsiElement)
      val executor = DefaultRunExecutor.getRunExecutorInstance()
      val environment = ExecutionEnvironmentBuilder.create(executor, runnerAndConfig).apply {
        contentToReuse(null)
        dataContext(null)
        activeTarget()
      }.build()

      ProgramRunnerUtil.executeConfigurationAsync(environment, false, true, object : ProgramRunner.Callback {
        override fun processStarted(descriptor: RunContentDescriptor) {
          executionConsole = (descriptor.executionConsole as BuildView).consoleView
          processStarted = true
          latch.countDown()
        }

        override fun processNotStarted(error: Throwable?) {
          latch.countDown()
        }
      })
    }

    assertTrue(latch.await(3, TimeUnit.MINUTES))
    assertTrue(processStarted)
    assertThat(executionConsole).isInstanceOf(AndroidTestSuiteView::class.java)
  }

  private fun createRunnerAndConfigurationSettingsFromPsiElement(project: Project, psiElement: PsiElement) : RunnerAndConfigurationSettings {
    val context = TestConfigurationTesting.createContext(project, psiElement)
    val settings = requireNotNull(context.configuration)

    val runManager = RunManager.getInstance(project)
    runManager.addConfiguration(settings)
    runManager.selectedConfiguration = settings

    return settings
  }

  private fun createProjectStructureForTest() {
    //simple screenshotTest
    createRelativeFileWithContent(
      "app/src/screenshotTest/java/com/example/application/MyScreenshotTest.kt",
      """
        package com.example.application

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        class MyScreenshotTest {
          @Preview(showBackground = true)
          @Composable
          fun PreviewMethod() {
          }

          @Preview(showBackground = true)
          @Composable
          fun AnotherPreviewMethod() {
          }
        }
      """.trimIndent())
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
      """.trimIndent()
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
      """.trimIndent()
    )
  }

  private fun createRelativeFileWithContent(relativePath: String, content: String): File {
    val newFile = File(
      projectRule.project.basePath,
      FileUtils.toSystemDependentPath(relativePath)
    )
    FileUtil.createIfDoesntExist(newFile)
    newFile.writeText(content)
    return newFile
  }
}