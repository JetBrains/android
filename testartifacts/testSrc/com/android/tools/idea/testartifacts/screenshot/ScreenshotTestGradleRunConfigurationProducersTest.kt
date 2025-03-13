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
package com.android.tools.idea.testartifacts.screenshot

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testartifacts.TestConfigurationTesting
import com.android.tools.idea.testartifacts.getPsiElement
import com.android.tools.idea.testartifacts.testsuite.GradleRunConfigurationExtension.BooleanOptions.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationFromContextImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

@RunsInEdt
class ScreenshotTestGradleRunConfigurationProducersTest {
  @get:Rule
  val flagRule = FlagRule(StudioFlags.ENABLE_SCREENSHOT_TESTING, true)

  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  private val SIMPLE_SCREENSHOT = "app/src/screenshotTest/java/com/example/application/MyScreenshotTest.kt"
  private val MULTI_PREVIEW = "app/src/screenshotTest/java/com/example/application/MyScreenshotTestMultiPreview.kt"
  private val DIFFERENT_PACKAGE = "app/src/screenshotTest/java/com/example/package/MyScreenshotTest.kt"
  private val EMPTY_CLASS = "app/src/screenshotTest/java/com/example/application/MyEmptyClass.kt"
  private val NO_PREVIEWS = "app/src/screenshotTest/java/com/example/application/NoPreviewsClass.kt"
  private val TOP_LEVEL = "app/src/screenshotTest/java/com/example/application/MyScreenshotTestTopLevel.kt"

  @Before
  fun setUp() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APP_WITH_SCREENSHOT_TEST)
    stubComposeAnnotation()
    stubPreviewAnnotation()
    createProjectStructureForTest()
    val screenshotTestDir = findFileByIoFile(File(projectRule.project.basePath + "/app/src/screenshotTest"), true)
    PsiTestUtil.addSourceRoot(projectRule.fixture.module, screenshotTestDir!!, true)
  }

  @Test
  fun testConfigurationFromClass() {
    val project = projectRule.project
    val qualifiedName = "com.example.application.MyScreenshotTest"
    val psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project))
    Assert.assertNotNull(psiClass)
    val runConfiguration = createGradleConfigurationFromPsiElement(project, psiClass as PsiElement)
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:validateDebugScreenshotTest", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.application.MyScreenshotTest*\"", runConfiguration.settings.taskNames[2])
  }

  @Test
  fun testConfigurationFromClassEmptyClass() {
    val project = projectRule.project
    val qualifiedName = "com.example.application.MyEmptyClass"
    val psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project))
    Assert.assertNotNull(psiClass)
    val runConfiguration = createGradleConfigurationFromPsiElement(project, psiClass as PsiElement)
    Assert.assertNull(runConfiguration)
  }

  @Test
  fun testConfigurationFromClassNoPreviewMethods() {
    val project = projectRule.project
    val qualifiedName = "com.example.application.NoPreviewsClass"
    val psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project))
    Assert.assertNotNull(psiClass)
    val runConfiguration = createGradleConfigurationFromPsiElement(project, psiClass as PsiElement)
    Assert.assertNull(runConfiguration)
  }

  @Test
  fun testConfigurationFromMethod() {
    val project = projectRule.project
    // test simple method
    val qualifiedName = "com.example.application.MyScreenshotTest"
    val methodName = "PreviewMethod"
    val psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project))
    Assert.assertNotNull(psiClass)
    val psiMethod = psiClass!!.methods.firstOrNull{ it.name == methodName }
    Assert.assertNotNull(psiMethod)
    val runConfiguration = createGradleConfigurationFromPsiElement(project, psiMethod as PsiElement)
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:validateDebugScreenshotTest", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.application.MyScreenshotTest.PreviewMethod\"", runConfiguration.settings.taskNames[2])
  }

  @Test
  fun testConfigurationFromMethodMultiPreview() {
    val project = projectRule.project
    val qualifiedName = "com.example.application.MyScreenshotTestMultiPreview"
    val methodName = "PreviewMethod"
    val psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project))
    Assert.assertNotNull(psiClass)
    val psiMethod = psiClass!!.methods.firstOrNull{ it.name == methodName }
    val runConfiguration = createGradleConfigurationFromPsiElement(project, psiMethod as PsiElement)
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:validateDebugScreenshotTest", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.application.MyScreenshotTestMultiPreview.PreviewMethod\"", runConfiguration.settings.taskNames[2])
  }

  @Test
  fun testConfigurationFromMethodTopLevel() {
    val project = projectRule.project
    val qualifiedName = "com.example.application.MyScreenshotTestTopLevelKt"
    val methodName = "PreviewMethod"
    val psiClassKt = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project))
    val psiMethod = psiClassKt!!.allMethods.firstOrNull { it.name == methodName }
    val runConfiguration = createGradleConfigurationFromPsiElement(project, psiMethod as PsiElement)
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:validateDebugScreenshotTest", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.application.MyScreenshotTestTopLevelKt.PreviewMethod\"", runConfiguration.settings.taskNames[2])

  }

  @Test
  fun testConfigurationFromPackage() {
    val project = projectRule.project
    val psiFile = getPsiElement(project, "app/src/screenshotTest/java/com/example", true)
    val context = TestConfigurationTesting.createContext(project, psiFile)
    val contextConfiguration = context.configurationsFromContext?.firstOrNull()  as ConfigurationFromContextImpl?
    val runConfiguration = contextConfiguration!!.configuration as GradleRunConfiguration
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:validateDebugScreenshotTest", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.*\"", runConfiguration.settings.taskNames[2])
    assertThat(contextConfiguration.configurationProducer).isInstanceOf(ScreenshotTestAllInPackageGradleConfigurationProducer::class.java)
  }

  @Test
  fun testConfigurationFromSubPackage() {
    val project = projectRule.project
    val psiFile = getPsiElement(project, "app/src/screenshotTest/java/com/example/package", true)
    val context = TestConfigurationTesting.createContext(project, psiFile)
    val contextConfiguration = context.configurationsFromContext?.firstOrNull()  as ConfigurationFromContextImpl?
    val runConfiguration = contextConfiguration!!.configuration as GradleRunConfiguration
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(3, runConfiguration.settings.taskNames.size)
    assertEquals(":app:validateDebugScreenshotTest", runConfiguration.settings.taskNames[0])
    assertEquals("--tests", runConfiguration.settings.taskNames[1])
    assertEquals("\"com.example.package.*\"", runConfiguration.settings.taskNames[2])
    assertThat(contextConfiguration.configurationProducer).isInstanceOf(ScreenshotTestAllInPackageGradleConfigurationProducer::class.java)
  }

  @Test
  fun testConfigurationFromAppDirectory() {
    val project = projectRule.project
    // test app directory
    val psiFile = getPsiElement(project, "app", true)
    val context = TestConfigurationTesting.createContext(project, psiFile)
    val contextConfiguration = context.configurationsFromContext?.firstOrNull{
      it.configuration.name.contains("Screenshot")
    }  as ConfigurationFromContextImpl?
    val runConfiguration = contextConfiguration!!.configuration as GradleRunConfiguration
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(1, runConfiguration.settings.taskNames.size)
    assertEquals(":app:validateDebugScreenshotTest", runConfiguration.settings.taskNames[0])
    assertThat(contextConfiguration.configurationProducer).isInstanceOf(ScreenshotTestAllInDirectoryGradleConfigurationProducer::class.java)
  }

  @Test
  fun testConfigurationFromSrcDirectory() {
    val project = projectRule.project
    val psiFile = getPsiElement(project, "app/src", true)
    val context = TestConfigurationTesting.createContext(project, psiFile)
    val contextConfiguration = context.configurationsFromContext?.firstOrNull{
      it.configuration.name.contains("Screenshot")
    }  as ConfigurationFromContextImpl?
    val runConfiguration = contextConfiguration!!.configuration as GradleRunConfiguration
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(1, runConfiguration.settings.taskNames.size)
    assertEquals(":app:validateDebugScreenshotTest", runConfiguration.settings.taskNames[0])
    assertThat(contextConfiguration.configurationProducer).isInstanceOf(ScreenshotTestAllInDirectoryGradleConfigurationProducer::class.java)

    // Compare generated run-config against unrelated context. It should return false.
    // This should not cause NPE.
    assertThat(contextConfiguration.configurationProducer.isConfigurationFromContext(
      runConfiguration,
      TestConfigurationTesting.createContext(project, getPsiElement(project, "nonAndroidModule", true))))
      .isFalse()
  }

  @Test
  fun testConfigurationFromSSTSourceSetDirectory() {
    val project = projectRule.project
    val psiFile = getPsiElement(project, "app/src/screenshotTest/java", true)
    val context = TestConfigurationTesting.createContext(project, psiFile)
    val contextConfiguration = context.configurationsFromContext?.firstOrNull{
      it.configuration.name.contains("Screenshot")
    }  as ConfigurationFromContextImpl?
    val runConfiguration = contextConfiguration!!.configuration as GradleRunConfiguration
    requireNotNull(runConfiguration)
    assertEquals(true, runConfiguration.getUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey))
    assertEquals(true, runConfiguration.isRunAsTest)
    assertEquals(1, runConfiguration.settings.taskNames.size)
    assertEquals(":app:validateDebugScreenshotTest", runConfiguration.settings.taskNames[0])
    assertThat(contextConfiguration.configurationProducer).isInstanceOf(ScreenshotTestAllInDirectoryGradleConfigurationProducer::class.java)
  }

  @Test
  fun testConfigurationFromOtherSourceSetDirectory() {
    val project = projectRule.project
    val psiFile = getPsiElement(project, "app/src/main/java", true)
    val context = TestConfigurationTesting.createContext(project, psiFile)
    val contextConfiguration = context.configurationsFromContext?.firstOrNull{
      it.configuration.name.contains("Screenshot")
    }  as ConfigurationFromContextImpl?
    Assert.assertNull(contextConfiguration)
  }

  @Test
  fun runConfigProducerShouldNotCrashForNonAndroidModule() {
    val project = projectRule.project
    val psiFile = getPsiElement(project, "nonAndroidModule", true)

    // This should not cause NPE.
    TestConfigurationTesting.createContext(project, psiFile).configuration
  }

  private fun createGradleConfigurationFromPsiElement(project: Project, psiElement: PsiElement) : GradleRunConfiguration? {
    val context = TestConfigurationTesting.createContext(project, psiElement)
    val settings = context.configuration ?: return null
    // Save run configuration in the project.
    val runManager = RunManager.getInstance(project)
    runManager.addConfiguration(settings)

    val configuration = settings.configuration
    if (configuration !is GradleRunConfiguration) return null
    val tasksToRun = configuration.settings.taskNames
    // Having no tasks to run means that there shouldn't be a configuration created. This will be handled by Intellij in
    // https://youtrack.jetbrains.com/issue/IDEA-277826.
    if (tasksToRun.isEmpty()) return null
    return configuration
  }

  private fun createProjectStructureForTest() {
    //simple screenshotTest
    createRelativeFileWithContent(SIMPLE_SCREENSHOT,
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

    //Multi preview annotations
    createRelativeFileWithContent(MULTI_PREVIEW,
    """
    package com.example.application

    import androidx.compose.runtime.Composable
    import androidx.compose.ui.tooling.preview.Preview

    class MyScreenshotTestMultiPreview {
      @Preview(showBackground = true)
      @Composable
      fun PreviewMethod() {
      }
      
      @MultiPreview
      @Composable
      fun AnotherPreviewMethod() {
      }
    }
    
    @Preview(name = "with background", showBackground = true)
    @Preview(name = "without background", showBackground = false)
    annotation class MultiPreview

    """.trimIndent())

    //file in a different package
    createRelativeFileWithContent(DIFFERENT_PACKAGE,
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

    //class with no methods
    createRelativeFileWithContent(EMPTY_CLASS,
    """
    package com.example.application

    import androidx.compose.runtime.Composable
    import androidx.compose.ui.tooling.preview.Preview

    class MyEmptyClass {
    }

    """.trimIndent())

    //class with no preview methods
    createRelativeFileWithContent(NO_PREVIEWS,
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

    """.trimIndent())

    //top level function
    createRelativeFileWithContent(TOP_LEVEL,
    """
    package com.example.application

    import androidx.compose.runtime.Composable
    import androidx.compose.ui.tooling.preview.Preview
 
    @Preview(showBackground = true)
    @Composable
    fun PreviewMethod() {
    }

    """.trimIndent())

  }

  private fun stubComposeAnnotation() {
    createRelativeFileWithContent(
      "app/src/screenshotTest/java/androidx/compose/runtime/Composable.kt", """
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
    createRelativeFileWithContent("app/src/screenshotTest/java/androidx/compose/ui/tooling/preview/Preview.kt", """
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
  """)
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