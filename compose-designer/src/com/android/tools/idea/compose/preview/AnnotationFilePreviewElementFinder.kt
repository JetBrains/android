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
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.text.nullize
import org.jetbrains.android.compose.COMPOSABLE_FQ_NAMES
import org.jetbrains.android.compose.COMPOSE_PREVIEW_ANNOTATION_NAME
import org.jetbrains.android.compose.PREVIEW_ANNOTATION_FQNS
import org.jetbrains.android.compose.PREVIEW_PARAMETER_FQNS
import org.jetbrains.android.compose.findComposeLibraryNamespace
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.kotlin.KotlinUClassLiteralExpression
import org.jetbrains.uast.toUElementOfType

/**
 * [FilePreviewElementFinder] that uses `@Preview` annotations.
 */
object AnnotationFilePreviewElementFinder : FilePreviewElementFinder {
  private fun findAllPreviewAnnotations(project: Project, vFile: VirtualFile): Sequence<UAnnotation> {
    if (DumbService.isDumb(project)) {
      Logger.getInstance(AnnotationFilePreviewElementFinder::class.java)
        .debug("findPreviewMethods called while indexing. No annotations will be found")
      return sequenceOf()
    }

    val kotlinAnnotations: Sequence<PsiElement> = ReadAction.compute<Sequence<PsiElement>, Throwable> {
      KotlinAnnotationsIndex.getInstance().get(COMPOSE_PREVIEW_ANNOTATION_NAME, project,
                                               GlobalSearchScope.fileScope(project, vFile)).asSequence()
    }

    return kotlinAnnotations
        .mapNotNull { ReadAction.compute<UAnnotation?, Throwable> { it.toUElementOfType() } }
        .filter { it.isPreviewAnnotation() }
  }

  override fun hasPreviewMethods(project: Project, vFile: VirtualFile): Boolean =
    findAllPreviewAnnotations(project, vFile).any()

  /**
   * Returns all the `@Composable` functions in the [vFile] that are also tagged with `@Preview`.
   */
  override fun findPreviewMethods(project: Project, vFile: VirtualFile): Sequence<PreviewElement> {
    return findAllPreviewAnnotations(project, vFile)
      .mapNotNull { it.toPreviewElement() }
      .distinct()
  }
}