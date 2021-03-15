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
import com.android.tools.compose.COMPOSABLE_ANNOTATION_NAME
import com.android.tools.compose.COMPOSABLE_FQ_NAMES
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_NAME
import com.android.tools.compose.PREVIEW_ANNOTATION_FQNS
import com.android.tools.idea.compose.preview.util.FilePreviewElementFinder
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElementOfType
import java.util.concurrent.Callable

/**
 * Finds any methods annotated with any of the given [annotations] FQCN or the given [shortAnnotationName].
 */
fun hasAnnotatedMethods(project: Project, vFile: VirtualFile,
                        annotations: Set<String>,
                        shortAnnotationName: String): Boolean = ReadAction.compute<Boolean, Throwable> {
  // This method can not call any methods that require smart mode.
  fun isFullNamePreviewAnnotation(annotation: KtAnnotationEntry) =
    // We use text() to avoid obtaining the FQN as that requires smart mode
    annotations.any { previewFqn ->
      annotation.text.startsWith("@$previewFqn")
    }

  val psiFile = PsiManager.getInstance(project).findFile(vFile)
  // Look into the imports first to avoid resolving the class name into all methods.
  val hasPreviewImport = PsiTreeUtil.findChildrenOfType(psiFile, KtImportDirective::class.java)
    .any { annotations.contains(it.importedFqName?.asString()) }

  return@compute if (hasPreviewImport) {
    PsiTreeUtil.findChildrenOfType(psiFile, KtAnnotationEntry::class.java)
      .any {
        it.shortName?.asString() == shortAnnotationName || isFullNamePreviewAnnotation(it)
      }
  }
  else {
    // The annotation is not imported so check if the method is using full name import.
    PsiTreeUtil.findChildrenOfType(psiFile, KtAnnotationEntry::class.java)
      .any(::isFullNamePreviewAnnotation)
  }
}

/**
 * [FilePreviewElementFinder] that uses `@Preview` annotations.
 */
object AnnotationFilePreviewElementFinder : FilePreviewElementFinder {
  private fun findAllPreviewAnnotations(project: Project, vFile: VirtualFile): Collection<KtAnnotationEntry> {
    if (DumbService.isDumb(project)) {
      Logger.getInstance(AnnotationFilePreviewElementFinder::class.java)
        .debug("findPreviewMethods called while indexing. No annotations will be found")
      return emptyList()
    }

    val kotlinAnnotations: Sequence<PsiElement> = ReadAction.compute<Sequence<PsiElement>, Throwable> {
      KotlinAnnotationsIndex.getInstance().get(COMPOSE_PREVIEW_ANNOTATION_NAME, project,
                                               GlobalSearchScope.fileScope(project, vFile)).asSequence()
    }

    return kotlinAnnotations
      .filterIsInstance<KtAnnotationEntry>()
      .filter { it.isPreviewAnnotation() }
      .toList()
  }

  override fun hasPreviewMethods(project: Project, vFile: VirtualFile): Boolean =
    hasAnnotatedMethods(project, vFile, PREVIEW_ANNOTATION_FQNS, COMPOSE_PREVIEW_ANNOTATION_NAME)

  override fun hasComposableMethods(project: Project, vFile: VirtualFile): Boolean =
    hasAnnotatedMethods(project, vFile, COMPOSABLE_FQ_NAMES, COMPOSABLE_ANNOTATION_NAME)

  /**
   * Returns all the `@Composable` functions in the [vFile] that are also tagged with `@Preview`.
   */
  @Slow
  override fun findPreviewMethods(project: Project, vFile: VirtualFile): Collection<PreviewElement> = if (DumbService.isDumb(project))
    emptyList()
  else
    ReadAction
      .nonBlocking(Callable<Collection<PreviewElement>> {
        findAllPreviewAnnotations(project, vFile)
          .mapNotNull {
            ProgressManager.checkCanceled()
            (it.psiOrParent.toUElementOfType() as? UAnnotation)?.toPreviewElement()
          }
          .distinct()
      })
      .inSmartMode(project)
      .coalesceBy(project, vFile)
      .submit(AppExecutorUtil.getAppExecutorService())
      .get()
}