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
import com.android.annotations.concurrency.Slow
import com.android.tools.idea.compose.preview.util.FilePreviewElementFinder
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.android.compose.COMPOSE_PREVIEW_ANNOTATION_NAME
import org.jetbrains.android.compose.PREVIEW_ANNOTATION_FQNS
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElementOfType

/**
 * [FilePreviewElementFinder] that uses `@Preview` annotations.
 */
object AnnotationFilePreviewElementFinder : FilePreviewElementFinder {
  private fun findAllPreviewAnnotations(project: Project, vFile: VirtualFile): Sequence<KtAnnotationEntry> {
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
      .filterIsInstance<KtAnnotationEntry>()
      .filter { it.isPreviewAnnotation() }
  }

  override fun hasPreviewMethods(project: Project, vFile: VirtualFile): Boolean = ReadAction.compute<Boolean, Throwable> {
    // This method can not call any methods that require smart mode.
    fun isFullNamePreviewAnnotation(annotation: KtAnnotationEntry) =
      // We use text() to avoid obtaining the FQN as that requires smart mode
      PREVIEW_ANNOTATION_FQNS.any { previewFqn ->
        annotation.text.startsWith("@$previewFqn")
      }

    val psiFile = PsiManager.getInstance(project).findFile(vFile)
    val hasPreviewImport = PsiTreeUtil.findChildrenOfType(psiFile, KtImportDirective::class.java)
      .any { PREVIEW_ANNOTATION_FQNS.contains(it.importedFqName?.asString()) }

    return@compute if (hasPreviewImport) {
      PsiTreeUtil.findChildrenOfType(psiFile, KtAnnotationEntry::class.java)
        .any {
          it.shortName?.asString() == COMPOSE_PREVIEW_ANNOTATION_NAME ||
          isFullNamePreviewAnnotation(it)
        }
    }
    else {
      // The Preview annotation is not imported so only
      PsiTreeUtil.findChildrenOfType(psiFile, KtAnnotationEntry::class.java)
        .any(::isFullNamePreviewAnnotation)
    }
  }

  /**
   * Returns all the `@Composable` functions in the [vFile] that are also tagged with `@Preview`.
   */
  @Slow
  override fun findPreviewMethods(project: Project, vFile: VirtualFile): Sequence<PreviewElement> {
    return findAllPreviewAnnotations(project, vFile)
      .mapNotNull { ReadAction.compute<UAnnotation?, Throwable> { it.psiOrParent?.toUElementOfType() }?.toPreviewElement() }
      .distinct()
  }
}