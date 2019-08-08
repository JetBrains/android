package com.android.tools.idea.compose.preview

/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.idea.kotlin.getQualifiedName
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.visitor.UastVisitor

private fun UAnnotation.findAttributeIntValue(name: String, defaultValue: Int) =
  findAttributeValue(name)?.evaluate() as? Int ?: defaultValue

/**
 * [PreviewElementFinder] that uses `@Preview` annotations.
 */
object AnnotationPreviewElementFinder : PreviewElementFinder {
  /**
   * Reads the `@Preview` annotation parameters and returns a [PreviewConfiguration] containing the values.
   */
  private fun attributesToConfiguration(node: UAnnotation): PreviewConfiguration {
    val apiLevel = node.findAttributeIntValue("apiLevel", UNDEFINED_API_LEVEL)
    val theme = node.findAttributeValue("theme")?.evaluateString()?.nullize()
    val width = node.findAttributeIntValue("width", UNDEFINED_DIMENSION)
    val height = node.findAttributeIntValue("height", UNDEFINED_DIMENSION)

    return PreviewConfiguration(apiLevel, theme, width, height)
  }

  /**
   * Returns all the `@Composable` methods in the [uFile] that are also tagged with `@Preview`.
   */
  override fun findPreviewMethods(uFile: UFile): Set<PreviewElement> {
    val previewMethodsFqNames = mutableSetOf<PreviewElement>()
    uFile.accept(object : UastVisitor {
      // Return false so we explore all the elements in the file (in case there are multiple @Preview elements)
      override fun visitElement(node: UElement): Boolean = false

      /**
       * Called for every `@Preview` annotation.
       */
      private fun visitPreviewAnnotation(previewAnnotation: UAnnotation) {
        val uMethod = previewAnnotation.uastParent as UMethod

        if (!uMethod.parameterList.isEmpty) {
          error("Preview methods must not have any parameters")
        }

        val uClass: UClass = uMethod.uastParent as UClass
        val composableMethod = "${uClass.qualifiedName}.${uMethod.name}"
        val previewName = previewAnnotation.findAttributeValue("name")?.evaluateString() ?: ""

        previewMethodsFqNames.add(PreviewElement(previewName, composableMethod,
                                                 attributesToConfiguration(previewAnnotation)))
      }

      override fun visitAnnotation(node: UAnnotation): Boolean {
        if (PREVIEW_ANNOTATION_FQN == node.qualifiedName) {
          visitPreviewAnnotation(node)
        }

        return super.visitAnnotation(node)
      }
    })

    return previewMethodsFqNames
  }

  override fun elementBelongsToPreviewElement(element: PsiElement): Boolean {
    val annotationEntry: KtAnnotationEntry? = PsiTreeUtil.getParentOfType(element, KtAnnotationEntry::class.java, false)
    return PREVIEW_ANNOTATION_FQN == annotationEntry?.getQualifiedName()
  }
}