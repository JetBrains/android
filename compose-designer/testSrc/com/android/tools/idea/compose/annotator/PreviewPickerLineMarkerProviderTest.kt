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
package com.android.tools.idea.compose.annotator

import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_NAME
import com.android.tools.idea.compose.ComposeExperimentalConfiguration
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.preview.namespaceVariations
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.moveCaret
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndGet
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.apache.commons.lang.StringUtils
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PreviewPickerLineMarkerProviderTest(
  previewAnnotationPackage: String,
  composableAnnotationPackage: String
) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}.Preview {1}.Composable")
    val namespaces = namespaceVariations
  }

  private val composableAnnotationFqName = "$composableAnnotationPackage.Composable"
  private val previewToolingPackage = previewAnnotationPackage

  private val filePath = "src/main/Test.kt"

  @get:Rule
  val rule =
    ComposeProjectRule(
      previewAnnotationPackage = previewAnnotationPackage,
      composableAnnotationPackage = composableAnnotationPackage
    )

  private val fixture
    get() = rule.fixture

  @get:Rule val edtRule = EdtRule()

  @Before
  fun setup() {
    StudioFlags.COMPOSE_PREVIEW_ELEMENT_PICKER.override(true)
    StudioFlags.COMPOSE_MULTIPREVIEW.override(true)
    ComposeExperimentalConfiguration.getInstance().isPreviewPickerEnabled = true
    (rule.fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.registerLanguageExtensionPoint(
      LineMarkerProviders.getInstance(),
      PreviewPickerLineMarkerProvider(),
      KotlinLanguage.INSTANCE
    )

    fixture.addFileToProject(
      filePath,
      // language=kotlin
      """
        import $composableAnnotationFqName
        import $previewToolingPackage.Preview

        @Preview
        class MyNotAnnotation() {}

        @Preview
        annotation class MyAnnotation() {}

        @Composable
        fun composable1() {}

        @Preview
        fun badPreview1() {}

        @Preview(
          group = "my group"
        )
        @Composable
        fun preview1() {}
      """.trimIndent()
    )
  }

  @After
  fun teardown() {
    StudioFlags.COMPOSE_PREVIEW_ELEMENT_PICKER.clearOverride()
    StudioFlags.COMPOSE_MULTIPREVIEW.clearOverride()
  }

  @RunsInEdt
  @Test
  fun gutterIconOnCorrectAnnotation() {
    val psiFile = fixture.findPsiFile(filePath)
    fixture.configureFromExistingVirtualFile(psiFile.virtualFile)

    fixture.doHighlighting()
    getAndAssertPreviewLineMarkers()

    fixture.moveCaret("\"my group\"\n)|\n@Composable")
    // The Modifier should still hold even after adding whitespace, and the LineMarker should be
    // available
    fixture.type('\n')
    fixture.type('\n')

    fixture.doHighlighting()
    val previewLineMarkerInfos = getAndAssertPreviewLineMarkers()
    listOf(psiFile.findPreviewAnnotation(2), psiFile.findPreviewAnnotation(4)).forEachIndexed {
      idx,
      validAnnotation ->
      assertEquals(validAnnotation.startOffset, previewLineMarkerInfos[idx].startOffset)
      assertEquals(validAnnotation.endOffset, previewLineMarkerInfos[idx].endOffset)
      assertEquals(COMPOSE_PREVIEW_ANNOTATION_NAME, previewLineMarkerInfos[idx].element!!.text)
    }
  }

  @RunsInEdt
  @Test
  fun lineMarkerAvailabilityOnVariedDeviceConfiguration() {
    val psiFile = fixture.findPsiFile(filePath)
    fixture.configureFromExistingVirtualFile(psiFile.virtualFile)

    fixture.moveCaret("\"my group\"|\n)\n@Composable")

    // Type incomplete device spec
    fixture.type(",\ndevice = \"spec:shape=Normal\"")
    fixture.doHighlighting()
    assertMissingLineMarker()

    fixture.moveCaret("spec:shape=Normal|\"")
    fixture.type(
      ",width=1080,height=1920,unit=px,dpi=480"
    ) // Type the rest of a correct Device spec.
    fixture.doHighlighting()
    getAndAssertPreviewLineMarkers()
  }

  private fun assertMissingLineMarker() {
    assertEquals(1, getPreviewLineMarkers().size)
  }

  private fun getAndAssertPreviewLineMarkers(): List<LineMarkerInfo<*>> {
    val previewLineMarkerInfos = getPreviewLineMarkers()

    assertEquals(2, previewLineMarkerInfos.size)
    assertTrue(
      previewLineMarkerInfos.all {
        "Preview Picker" == it.createGutterRenderer().clickAction!!.templateText
      }
    )
    return previewLineMarkerInfos
  }

  private fun getPreviewLineMarkers(): List<LineMarkerInfo<*>> =
    DaemonCodeAnalyzerImpl.getLineMarkers(fixture.editor.document, rule.project).filter {
      lineMarkerInfo ->
      lineMarkerInfo.lineMarkerTooltip == "Preview configuration picker"
    }
}

private fun PsiFile.findPreviewAnnotation(ordinal: Int): PsiElement = runInEdtAndGet {
  // The element should start after the '@' so add 1 to the offset
  val indexOfElement =
    StringUtils.ordinalIndexOf(text, "@$COMPOSE_PREVIEW_ANNOTATION_NAME", ordinal) + 1
  checkNotNull(
    PsiTreeUtil.findElementOfClassAtOffset(
      this,
      indexOfElement,
      KtNameReferenceExpression::class.java,
      true
    )
  )
}
