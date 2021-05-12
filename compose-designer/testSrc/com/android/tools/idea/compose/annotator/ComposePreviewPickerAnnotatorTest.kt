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
import com.android.tools.idea.compose.preview.addFileToProjectAndInvalidate
import com.android.tools.idea.compose.preview.namespaceVariations
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.daemon.impl.LineMarkersPass
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.apache.commons.lang.StringUtils
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class ComposePreviewPickerAnnotatorTest(previewAnnotationPackage: String, composableAnnotationPackage: String) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}.Preview {1}.Composable")
    val namespaces = namespaceVariations
  }

  private val COMPOSABLE_ANNOTATION_FQN = "$composableAnnotationPackage.Composable"
  private val PREVIEW_TOOLING_PACKAGE = previewAnnotationPackage

  private val FILE_PATH = "src/main/Test.kt"

  @get:Rule
  val rule = ComposeProjectRule(previewAnnotationPackage = previewAnnotationPackage,
                                composableAnnotationPackage = composableAnnotationPackage)

  @Before
  fun setup() {
    StudioFlags.COMPOSE_PREVIEW_ELEMENT_PICKER.override(true)
    StudioFlags.COMPOSE_EDITOR_SUPPORT.override(true)
    ComposeExperimentalConfiguration.getInstance().isPreviewPickerEnabled = true
    (rule.fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    registerPreviewPickerAnnotator()

    rule.fixture.addFileToProjectAndInvalidate(
      FILE_PATH,
      // language=kotlin
      """
        import $COMPOSABLE_ANNOTATION_FQN
        import $PREVIEW_TOOLING_PACKAGE.Preview

        @Composable
        fun composable1() {}
        
        @Preview
        fun badPreview1() {}
        
        @Composable
        @Preview
        fun preview1() {}
      """.trimIndent())
  }

  @After
  fun teardown() {
    StudioFlags.COMPOSE_PREVIEW_ELEMENT_PICKER.override(false)
    StudioFlags.COMPOSE_EDITOR_SUPPORT.override(false)
  }

  @Test
  fun gutterIconActionOnPreview() {
    runInEdtAndWait {
      val psiFile = rule.findPsiFile(FILE_PATH)
      val previewLineMarkerInfos = LineMarkersPass.queryLineMarkers(psiFile, psiFile.viewProvider.document!!).filter { lineMarkerInfo ->
        lineMarkerInfo.lineMarkerTooltip == "Preview configuration picker"
      }
      assertEquals(1, previewLineMarkerInfos.size)
      val previewLineMarkerInfo = previewLineMarkerInfos.first()
      assertEquals("Preview Picker", previewLineMarkerInfo.createGutterRenderer().clickAction!!.templateText)

      val validAnnotation = psiFile.findValidPreviewAnnotation()
      assertEquals(validAnnotation.startOffset, previewLineMarkerInfo.startOffset)
      assertEquals(validAnnotation.endOffset, previewLineMarkerInfo.endOffset)
      assertEquals(COMPOSE_PREVIEW_ANNOTATION_NAME, previewLineMarkerInfo.element!!.text)
    }
  }

  private fun registerPreviewPickerAnnotator() {
    val pickerAnnotatorEp: LanguageExtensionPoint<LineMarkerProvider> =
      LanguageExtensionPoint(KotlinLanguage.INSTANCE.id, ComposePreviewPickerAnnotator())
    val extensionPoint: ExtensionPoint<LanguageExtensionPoint<LineMarkerProvider>> =
      ApplicationManager.getApplication().extensionArea.getExtensionPoint(LineMarkerProviders.EP_NAME)

    extensionPoint.registerExtension(pickerAnnotatorEp, rule.fixture.testRootDisposable)
  }
}

private fun PsiFile.findValidPreviewAnnotation(): PsiElement =
  runInEdtAndGet {
    // The second @Preview annotation has the correct syntax
    val indexOfElement = StringUtils.ordinalIndexOf(text, "@$COMPOSE_PREVIEW_ANNOTATION_NAME", 2)
    checkNotNull(PsiTreeUtil.findElementOfClassAtOffset(this, indexOfElement, KtAnnotationEntry::class.java, true))
  }

private fun ComposeProjectRule.findPsiFile(tempDirPath: String): PsiFile {
  val file = checkNotNull(fixture.findFileInTempDir(tempDirPath))
  return checkNotNull(PsiManager.getInstance(project).findFile(file))
}