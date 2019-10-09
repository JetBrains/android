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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.visitor.UastVisitor

private fun UAnnotation.findAttributeIntValue(name: String) =
  findAttributeValue(name)?.evaluate() as? Int

private fun UAnnotation.findAttributeFloatValue(name: String) =
  findAttributeValue(name)?.evaluate() as? Float

/**
 * Reads the `@Preview` annotation parameters and returns a [PreviewConfiguration] containing the values.
 */
private fun attributesToConfiguration(node: UAnnotation): PreviewConfiguration {
  val apiLevel = node.findAttributeIntValue("apiLevel")
  val theme = node.findAttributeValue("theme")?.evaluateString()?.nullize()
  // Both width and height have to support old ("width") and new ("widthDp") conventions
  val width = node.findAttributeIntValue("width") ?: node.findAttributeIntValue("widthDp")
  val height = node.findAttributeIntValue("height") ?: node.findAttributeIntValue("heightDp")
  val fontScale = node.findAttributeFloatValue("fontScale")

  return PreviewConfiguration.cleanAndGet(apiLevel, theme, width, height, fontScale)
}

/**
 * [PreviewElementFinder] that uses `@Preview` annotations.
 */
object AnnotationPreviewElementFinder : PreviewElementFinder {
  override fun hasPreviewMethods(project: Project, file: VirtualFile): Boolean =
    PsiTreeUtil.findChildrenOfType(PsiManager.getInstance(project).findFile(file), KtImportDirective::class.java)
      .any { PREVIEW_ANNOTATION_FQN == it.importedFqName?.asString() }

  /**
   * Returns all the `@Composable` methods in the [uFile] that are also tagged with `@Preview`.
   * The order of the elements will be the same as the order of the composable methods.
   */
  override fun findPreviewMethods(uFile: UFile): List<PreviewElement> {
    val previewMethodsFqName = mutableSetOf<String>()
    val previewElements = mutableListOf<PreviewElement>()
    uFile.accept(object : UastVisitor {
      // Return false so we explore all the elements in the file (in case there are multiple @Preview elements)
      override fun visitElement(node: UElement): Boolean = false

      /**
       * Called for every `@Preview` annotation.
       */
      private fun visitPreviewAnnotation(previewAnnotation: UAnnotation, annotatedMethod: UMethod) {
        val uClass: UClass = annotatedMethod.uastParent as UClass
        val composableMethod = "${uClass.qualifiedName}.${annotatedMethod.name}"
        val previewName = previewAnnotation.findAttributeValue("name")?.evaluateString() ?: ""

        // If the same composable method is found multiple times, only keep the first one. This usually will happen during
        // copy & paste and both the compiler and Studio will flag it as an error.
        if (previewMethodsFqName.add(composableMethod)) {
          previewElements.add(PreviewElement(previewName, composableMethod,
                                             previewAnnotation.toSmartPsiPointer(),
                                             annotatedMethod.uastBody.toSmartPsiPointer(),
                                             attributesToConfiguration(previewAnnotation)))
        }
      }

      override fun visitAnnotation(node: UAnnotation): Boolean {
        if (PREVIEW_ANNOTATION_FQN == node.qualifiedName) {
          val uMethod = node.getContainingUMethod()
          uMethod?.let {
            if (it.uastParameters.isNotEmpty()) {
              // We do not fail here. The ComposeViewAdapter will throw an exception that will be surfaced to the user
              Logger.getInstance(AnnotationPreviewElementFinder::class.java).debug("Preview methods must not have any parameters")
            }

            // The method must also be annotated with @Composable
            if (it.annotations.any { annotation -> COMPOSABLE_ANNOTATION_FQN == annotation.qualifiedName }) {
              visitPreviewAnnotation(node, it)
            }
          }
        }

        return super.visitAnnotation(node)
      }
    })

    return previewElements
  }
}