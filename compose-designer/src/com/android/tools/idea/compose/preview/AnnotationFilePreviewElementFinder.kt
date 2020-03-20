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
import com.android.tools.idea.kotlin.fqNameMatches
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.psi.KtAnnotationEntry
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
  val width = node.findAttributeIntValue("width") ?: node.findAttributeIntValue(WIDTH_PARAMETER)
  val height = node.findAttributeIntValue("height") ?: node.findAttributeIntValue(HEIGHT_PARAMETER)
  val fontScale = node.findAttributeFloatValue("fontScale")

  return PreviewConfiguration.cleanAndGet(apiLevel, theme, width, height, fontScale)
}

/**
 * [FilePreviewElementFinder] that uses `@Preview` annotations.
 */
object AnnotationFilePreviewElementFinder : FilePreviewElementFinder {
  override fun hasPreviewMethods(project: Project, vFile: VirtualFile): Boolean = ReadAction.compute<Boolean, Throwable> {
    val psiFile = PsiManager.getInstance(project).findFile(vFile)
    PsiTreeUtil.findChildrenOfType(psiFile, KtImportDirective::class.java)
      .any { PREVIEW_ANNOTATION_FQN == it.importedFqName?.asString() } ||
    PsiTreeUtil.findChildrenOfType(psiFile, KtAnnotationEntry::class.java)
      .any { it.fqNameMatches(PREVIEW_ANNOTATION_FQN) }
  }

  /**
   * Returns all the `@Composable` functions in the [uFile] that are also tagged with `@Preview`.
   * The order of the elements will be the same as the order of the composable functions.
   */
  override fun findPreviewMethods(uFile: UFile): List<PreviewElement> = ReadAction.compute<List<PreviewElement>, Throwable> {
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
        val previewName = previewAnnotation.findDeclaredAttributeValue("name")?.evaluateString() ?: annotatedMethod.name
        val groupName = previewAnnotation.findDeclaredAttributeValue("group")?.evaluateString()
        val showDecorations = previewAnnotation.findDeclaredAttributeValue("showDecoration")?.evaluate() as? Boolean ?: false
        val showBackground = previewAnnotation.findDeclaredAttributeValue("showBackground")?.evaluate() as? Boolean ?: false

        // If the same composable functions is found multiple times, only keep the first one. This usually will happen during
        // copy & paste and both the compiler and Studio will flag it as an error.
        if (previewMethodsFqName.add(composableMethod)) {
          val displaySettings = PreviewDisplaySettings(previewName,
                                                       groupName,
                                                       showDecorations,
                                                       showBackground,
                                                       null)

          previewElements.add(PreviewElement(composableMethod,
                                             displaySettings,
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
              Logger.getInstance(AnnotationFilePreviewElementFinder::class.java).debug("Preview functions must not have any parameters")
            }

            // The method must also be annotated with @Composable
            if (it.uAnnotations.any { annotation -> COMPOSABLE_ANNOTATION_FQN == annotation.qualifiedName }) {
              visitPreviewAnnotation(node, it)
            }
          }
        }

        return super.visitAnnotation(node)
      }
    })

    return@compute previewElements
  }
}