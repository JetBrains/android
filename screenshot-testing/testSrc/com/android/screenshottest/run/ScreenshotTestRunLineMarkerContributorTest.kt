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
package com.android.screenshottest.run


import com.android.flags.junit.FlagRule
import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.android.utils.FileUtils
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.AndroidTestCase.assertNotNull
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File


class ScreenshotTestRunLineMarkerContributorTest {
  @get:Rule
  val flagRule = FlagRule(StudioFlags.ENABLE_SCREENSHOT_TESTING, true)

  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  private val contributor = ScreenshotTestRunLineMarkerContributor()
  private var file: PsiFile? = null

  private val SRC_FILE_HEADER = """
      package com.example.runlinemarker;

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

    """.trimIndent()

  @Before
  fun setup() {
    projectRule.fixture.testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/screenshot-testing/testData").toString()
    projectRule.loadProject(TestProjectPaths.SIMPLE_APP_WITH_SCREENSHOT_TEST)
    stubComposeAnnotation()
    stubPreviewAnnotation()

  }

  @Test
  @RunsInEdt
  fun testStudioFlagDisabled() {
    StudioFlags.ENABLE_SCREENSHOT_TESTING.override(false)
    val cFile = createRelativeFilewithContent("app/src/screenshotTest/java/com/example/runlinemarker/PreviewScreenshotTest.kt", """
        $SRC_FILE_HEADER
        class PreviewScreenshotTest {
           @Preview(showBackground = true)
           @Composable
           fun GreetingAndroid() {
               println("Hello")
           }
        
           @Preview(showBackground = true)
           @Composable
           fun GreetingPreview() {
               println("Hi")
           }
        }
      """.trimIndent())
    val virtualFile = findFileByIoFile(cFile, true)
    val screenshotDir = virtualFile!!.parent.parent.parent.parent.parent
    PsiTestUtil.addSourceRoot(projectRule.fixture.module, screenshotDir!!, true)
    file = virtualFile.toPsiFile(projectRule.project)
    val function1 = file!!.findFunctionIdentifier("GreetingAndroid")
    val function2 = file!!.findFunctionIdentifier("GreetingPreview")
    val classElement = file!!.findClassdentifier("PreviewScreenshotTest")
    assertNull(contributor.getSlowInfo(function1))
    assertNull(contributor.getSlowInfo(function2))
    assertNull(contributor.getSlowInfo(classElement))
  }

  @Test
  @RunsInEdt
  fun testRunLineMarkerContributor() {
    val cFile = createRelativeFilewithContent("app/src/screenshotTest/java/com/example/runlinemarker/PreviewScreenshotTest.kt", """
        $SRC_FILE_HEADER
        class PreviewScreenshotTest {
           @Preview(showBackground = true)
           @Composable
           fun GreetingAndroid() {
               println("Hello")
           }
        
           @Preview(showBackground = true)
           @Composable
           fun GreetingPreview() {
               println("Hi")
           }
        }
      """.trimIndent())
    val virtualFile = findFileByIoFile(cFile, true)
    val screenshotDir = virtualFile!!.parent.parent.parent.parent.parent
    PsiTestUtil.addSourceRoot(projectRule.fixture.module, screenshotDir!!, true)
    file = virtualFile.toPsiFile(projectRule.project)
    val function1 = file!!.findFunctionIdentifier("GreetingAndroid")
    val function2 = file!!.findFunctionIdentifier("GreetingPreview")
    val classElement = file!!.findClassdentifier("PreviewScreenshotTest")
    val fun1Info = contributor.getSlowInfo(function1)
    val fun2Info = contributor.getSlowInfo(function2)
    val classInfo = contributor.getSlowInfo(classElement)
    assertNotNull(fun1Info)
    assertNotNull(fun2Info)
    assertNotNull(classInfo)
  }

  @Test
  @RunsInEdt
  fun testRunLineMarkerContributorMultiPreview() {
    val cFile = createRelativeFilewithContent("app/src/screenshotTest/java/com/example/runlinemarker/PreviewScreenshotTest.kt", """
        $SRC_FILE_HEADER
        class PreviewScreenshotTest {
           @MultiPreview(showBackground = true)
           @Composable
           fun GreetingAndroid() {
               println("Hello")
           }
        }
        
        @Preview(name = "with background", showBackground = true)
        @Preview(name = "without background", showBackground = false)
        annotation class MultiPreview
      """.trimIndent())
    val virtualFile = findFileByIoFile(cFile, true)
    val screenshotDir = virtualFile!!.parent.parent.parent.parent.parent
    PsiTestUtil.addSourceRoot(projectRule.fixture.module, screenshotDir!!, true)
    file = virtualFile.toPsiFile(projectRule.project)
    val function1 = file!!.findFunctionIdentifier("GreetingAndroid")
    val classElement = file!!.findClassdentifier("PreviewScreenshotTest")
    val fun1Info = contributor.getSlowInfo(function1)
    val classInfo = contributor.getSlowInfo(classElement)
    assertNotNull(fun1Info)
    assertNotNull(classInfo)
  }

  @Test
  @RunsInEdt
  fun testRunLineMarkerContributorNoPreviewMethod() {
    val cFile = createRelativeFilewithContent("app/src/screenshotTest/java/com/example/runlinemarker/PreviewScreenshotTest.kt", """
        $SRC_FILE_HEADER
        class PreviewScreenshotTest {
           @Composable
           fun GreetingAndroid() {
               println("Hello")
           }

           @Composable
           fun GreetingPreview() {
               println("Hi")
           }
        }
      """.trimIndent())
    val virtualFile = findFileByIoFile(cFile, true)
    val screenshotDir = virtualFile!!.parent.parent.parent.parent.parent
    PsiTestUtil.addSourceRoot(projectRule.fixture.module, screenshotDir!!, true)
    file = virtualFile.toPsiFile(projectRule.project)
    val function1 = file!!.findFunctionIdentifier("GreetingAndroid")
    val function2 = file!!.findFunctionIdentifier("GreetingPreview")
    val classElement = file!!.findClassdentifier("PreviewScreenshotTest")
    assertNull(contributor.getSlowInfo(function1))
    assertNull(contributor.getSlowInfo(function2))
    assertNull(contributor.getSlowInfo(classElement))
  }

  @Test
  @RunsInEdt
  fun testRunLineMarkerContributorNoMethod() {
    val cFile = createRelativeFilewithContent("app/src/screenshotTest/java/com/example/runlinemarker/PreviewScreenshotTest.kt", """
        $SRC_FILE_HEADER
        class PreviewScreenshotTest {
        }
      """.trimIndent())
    val virtualFile = findFileByIoFile(cFile, true)
    val screenshotDir = virtualFile!!.parent.parent.parent.parent.parent
    PsiTestUtil.addSourceRoot(projectRule.fixture.module, screenshotDir!!, true)
    file = virtualFile.toPsiFile(projectRule.project)
    val classElement = file!!.findClassdentifier("PreviewScreenshotTest")
    assertNull(contributor.getSlowInfo(classElement))
  }

  private fun PsiFile.findFunctionIdentifier(name: String): PsiElement {
    val function = PsiTreeUtil.findChildrenOfType(this, KtNamedFunction::class.java).first { it.name == name }
    return PsiTreeUtil.getChildrenOfType(function, LeafPsiElement::class.java)?.first {
      it.node.elementType == KtTokens.IDENTIFIER }!!
  }

  private fun PsiFile.findClassdentifier(name: String): PsiElement {
    val function = PsiTreeUtil.findChildrenOfType(this, KtClass::class.java).first { it.name == name }
    return PsiTreeUtil.getChildrenOfType(function, LeafPsiElement::class.java)?.first {
      it.node.elementType == KtTokens.IDENTIFIER }!!
  }

  private fun stubComposeAnnotation() {
    createRelativeFilewithContent(
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
    createRelativeFilewithContent("app/src/screenshotTest/java/androidx/compose/ui/tooling/preview/Preview.kt", """
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
  private fun createRelativeFilewithContent(relativePath: String, content: String): File {
    val newFile = File(
      projectRule.project.basePath,
      FileUtils.toSystemDependentPath(relativePath)
    )
    FileUtil.createIfDoesntExist(newFile)
    newFile.writeText(content)
    return newFile
  }
}