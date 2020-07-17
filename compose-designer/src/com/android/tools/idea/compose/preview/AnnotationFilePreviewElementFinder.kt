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
import com.android.tools.idea.compose.preview.util.FilePreviewElementFinder
import com.android.tools.idea.compose.preview.util.HEIGHT_PARAMETER
import com.android.tools.idea.compose.preview.util.ParametrizedPreviewElementTemplate
import com.android.tools.idea.compose.preview.util.PreviewConfiguration
import com.android.tools.idea.compose.preview.util.PreviewDisplaySettings
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.PreviewParameter
import com.android.tools.idea.compose.preview.util.SinglePreviewElementInstance
import com.android.tools.idea.compose.preview.util.WIDTH_PARAMETER
import com.android.tools.idea.compose.preview.util.toSmartPsiPointer
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.fqNameMatches
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.nullize
import org.jetbrains.android.compose.COMPOSABLE_FQ_NAMES
import org.jetbrains.android.compose.PREVIEW_ANNOTATION_FQNS
import org.jetbrains.android.compose.PREVIEW_PARAMETER_FQNS
import org.jetbrains.android.compose.findComposeLibraryNamespace
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.kotlin.KotlinUClassLiteralExpression
import org.jetbrains.uast.visitor.UastVisitor

private fun UAnnotation.findAttributeIntValue(name: String) =
  findAttributeValue(name)?.evaluate() as? Int

private fun UAnnotation.findAttributeFloatValue(name: String) =
  findAttributeValue(name)?.evaluate() as? Float

private fun UAnnotation.findClassNameValue(name: String) =
  (findAttributeValue(name) as? KotlinUClassLiteralExpression)?.type?.canonicalText

/**
 * Looks up for annotation element using a set of annotation qualified names.
 *
 * @param fqName the qualified name to search
 * @return the first annotation element with the specified qualified name, or null if there is no annotation with such name.
 */
private fun UAnnotated.findAnnotation(fqName: Set<String>): UAnnotation? = uAnnotations.firstOrNull { fqName.contains(it.qualifiedName) }

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
  val uiMode = node.findAttributeIntValue("uiMode")
  val device = node.findAttributeValue("device")?.evaluateString()?.nullize()

  return PreviewConfiguration.cleanAndGet(apiLevel, theme, width, height, fontScale, uiMode, device)
}

/**
 * [FilePreviewElementFinder] that uses `@Preview` annotations.
 */
object AnnotationFilePreviewElementFinder : FilePreviewElementFinder {
  override fun hasPreviewMethods(project: Project, vFile: VirtualFile): Boolean = ReadAction.compute<Boolean, Throwable> {
    val psiFile = PsiManager.getInstance(project).findFile(vFile)
    PsiTreeUtil.findChildrenOfType(psiFile, KtImportDirective::class.java)
      .any { PREVIEW_ANNOTATION_FQNS.contains(it.importedFqName?.asString()) } ||
    PsiTreeUtil.findChildrenOfType(psiFile, KtAnnotationEntry::class.java)
      .any { it.fqNameMatches(PREVIEW_ANNOTATION_FQNS) }
  }

  /**
   * Returns all the `@Composable` functions in the [uFile] that are also tagged with `@Preview`.
   */
  override fun findPreviewMethods(uFile: UFile): Sequence<PreviewElement> = ReadAction.compute<Sequence<PreviewElement>, Throwable> {
    if (DumbService.isDumb(uFile.sourcePsi.project)) {
      Logger.getInstance(AnnotationFilePreviewElementFinder::class.java)
        .debug("findPreviewMethods called in dumb mode. No annotations will be found")
      return@compute sequenceOf()
    }

    val previewElements = mutableSetOf<PreviewElement>()
    uFile.accept(object : UastVisitor {
      // Return false so we explore all the elements in the file (in case there are multiple @Preview elements)
      override fun visitElement(node: UElement): Boolean = false

      /**
       * Returns a list of [PreviewParameter] for the given [Collection<UParameter>]. If the parameters are annotated with
       * `PreviewParameter`, then they will be returned as part of the collection.
       */
      private fun getPreviewParameters(parameters: Collection<UParameter>): Collection<PreviewParameter> =
        parameters.mapIndexedNotNull { index, parameter ->
          val annotation = parameter.findAnnotation(PREVIEW_PARAMETER_FQNS) ?: return@mapIndexedNotNull null
          val providerClassFqn = (annotation.findClassNameValue("provider")) ?: return@mapIndexedNotNull null
          val limit = annotation.findAttributeIntValue("limit") ?: Int.MAX_VALUE
          PreviewParameter(parameter.name, index, providerClassFqn, limit)
        }

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
        val backgroundColor = previewAnnotation.findDeclaredAttributeValue("backgroundColor")?.evaluate() as? Int

        // If the same composable functions is found multiple times, only keep the first one. This usually will happen during
        // copy & paste and both the compiler and Studio will flag it as an error.
        val displaySettings = PreviewDisplaySettings(previewName,
                                                     groupName,
                                                     showDecorations,
                                                     showBackground,
                                                     backgroundColor?.toString(16)?.let { "#$it" })

        val parameters = getPreviewParameters(annotatedMethod.uastParameters)
        val composeLibraryNamespace = previewAnnotation.findComposeLibraryNamespace()
        val basePreviewElement = SinglePreviewElementInstance(composableMethod,
                                                              displaySettings,
                                                              previewAnnotation.toSmartPsiPointer(),
                                                              annotatedMethod.uastBody.toSmartPsiPointer(),
                                                              attributesToConfiguration(previewAnnotation),
                                                              composeLibraryNamespace)
        if (!parameters.isEmpty()) {
          if (StudioFlags.COMPOSE_PREVIEW_DATA_SOURCES.get()) {
            previewElements.add(ParametrizedPreviewElementTemplate(basePreviewElement, parameters))
          }
        }
        else {
          previewElements.add(basePreviewElement)
        }
      }

      override fun visitAnnotation(node: UAnnotation): Boolean {
        if (PREVIEW_ANNOTATION_FQNS.contains(node.qualifiedName)) {
          val uMethod = node.getContainingUMethod()
          uMethod?.let {
            if (it.uastParameters.isNotEmpty()) {
              // We do not fail here. The ComposeViewAdapter will throw an exception that will be surfaced to the user
              Logger.getInstance(AnnotationFilePreviewElementFinder::class.java).debug("Preview functions must not have any parameters")
            }

            // The method must also be annotated with @Composable
            if (it.uAnnotations.any { annotation -> COMPOSABLE_FQ_NAMES.contains(annotation.qualifiedName) }) {
              visitPreviewAnnotation(node, it)
            }
          }
        }

        return super.visitAnnotation(node)
      }
    })

    return@compute previewElements.asSequence()
  }
}