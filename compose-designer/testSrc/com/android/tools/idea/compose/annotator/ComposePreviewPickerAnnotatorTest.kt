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

import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.preview.addFileToProjectAndInvalidate
import com.android.tools.idea.compose.preview.namespaceVariations
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.apache.commons.lang.StringUtils
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private const val PREVIEW_ANNOT_TEXT = "@Preview"

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
    (rule.fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true

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
      checkPreviewAnnotation(1, false)
      checkPreviewAnnotation(2, true)
    }
  }

  /**
   * Asserts that the [ComposePreviewPickerAnnotator] annotates the given [occurrenceInFile] of the @Preview annotation.
   */
  private fun checkPreviewAnnotation(occurrenceInFile: Int, expectAnnotation: Boolean) {
    require(occurrenceInFile > 0)
    val psiFile = rule.findPsiFile(FILE_PATH)
    val annotator = ComposePreviewPickerAnnotator()
    val annotationHolder = AnnotationHolderImpl(AnnotationSession(psiFile))
    annotationHolder.runAnnotatorWithContext(psiFile.findNthPreviewAnnotation(occurrenceInFile), annotator)
    assert(annotationHolder.hasAnnotations() == expectAnnotation)
  }
}

/**
 * Returns the nth [occurrence] of the @Preview annotation as [PsiElement]
 */
private fun PsiFile.findNthPreviewAnnotation(occurrence: Int): PsiElement =
  runInEdtAndGet {
    val indexOfElement = StringUtils.ordinalIndexOf(text, PREVIEW_ANNOT_TEXT, occurrence)
    checkNotNull(PsiTreeUtil.findElementOfClassAtOffset(this, indexOfElement, KtAnnotationEntry::class.java, true))
  }

private fun ComposeProjectRule.findPsiFile(tempDirPath: String): PsiFile {
  val file = checkNotNull(fixture.findFileInTempDir(tempDirPath))
  return checkNotNull(PsiManager.getInstance(project).findFile(file))
}