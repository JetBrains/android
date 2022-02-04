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
import com.android.tools.compose.COMPOSABLE_ANNOTATION_NAME
import com.android.tools.compose.COMPOSABLE_FQ_NAMES
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_NAME
import com.android.tools.compose.PREVIEW_ANNOTATION_FQNS
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.compose.preview.util.FilePreviewElementFinder
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.concurrency.AndroidDispatchers.ioThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.getPsiFileSafely
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.isRejected
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.idea.util.application.runReadAction
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
                        shortAnnotationName: String): Boolean = runReadAction {
  // This method can not call any methods that require smart mode.
  fun isFullNamePreviewAnnotation(annotation: KtAnnotationEntry) =
    // We use text() to avoid obtaining the FQN as that requires smart mode
    annotations.any { previewFqn ->
      // In brackets annotations don't start with '@', but typical annotations do. Normalize them by removing it
      annotation.text.removePrefix("@").startsWith("$previewFqn")
    }

  val psiFile = PsiManager.getInstance(project).findFile(vFile)
  // Look into the imports first to avoid resolving the class name into all methods.
  val hasPreviewImport = PsiTreeUtil.findChildrenOfType(psiFile, KtImportDirective::class.java)
    .any { annotations.contains(it.importedFqName?.asString()) }

  return@runReadAction if (hasPreviewImport) {
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

    val psiFile = AndroidPsiUtils.getPsiFileSafely(project, vFile)  ?: return emptyList()
    return CachedValuesManager.getManager(project).getCachedValue(psiFile) {
      val kotlinAnnotations: Sequence<PsiElement> = ReadAction.compute<Sequence<PsiElement>, Throwable> {
        KotlinAnnotationsIndex.getInstance().get(COMPOSE_PREVIEW_ANNOTATION_NAME, project,
                                                 GlobalSearchScope.fileScope(project, vFile)).asSequence()
      }

      val previewAnnotations = kotlinAnnotations
        .filterIsInstance<KtAnnotationEntry>()
        .filter { it.isPreviewAnnotation() }
        .toList()

      CachedValueProvider.Result.create(previewAnnotations, psiFile)
    }
  }

  override fun hasPreviewMethods(project: Project, vFile: VirtualFile): Boolean {
    val psiFile = AndroidPsiUtils.getPsiFileSafely(project, vFile) ?: return false
    return CachedValuesManager.getManager(project).getCachedValue(psiFile) {
      CachedValueProvider.Result.createSingleDependency(
        hasAnnotatedMethods(project, vFile, PREVIEW_ANNOTATION_FQNS, COMPOSE_PREVIEW_ANNOTATION_NAME),
        psiFile
      )
    }
  }

  override fun hasComposableMethods(project: Project, vFile: VirtualFile): Boolean {
    val psiFile = AndroidPsiUtils.getPsiFileSafely(project, vFile) ?: return false
    return CachedValuesManager.getManager(project).getCachedValue(psiFile) {
      CachedValueProvider.Result.createSingleDependency(
        hasAnnotatedMethods(project, vFile, COMPOSABLE_FQ_NAMES, COMPOSABLE_ANNOTATION_NAME),
        psiFile
      )
    }
  }

  /**
   * A [ModificationTracker] that tracks a [Promise] and can be used as a dependency for [CachedValuesManager.getCachedValue].
   * If the promise is rejected or fails, this will update the count, invalidating the cache (so it's not stored).
   */
  private class PromiseModificationTracker(private val promise: Promise<*>): ModificationTracker {
    private var modificationCount = 0L
    override fun getModificationCount(): Long = when {
      promise.isRejected -> ++modificationCount // The promise failed so we ensure it is not cached
      else -> modificationCount
    }
  }

  /**
   * Maximum number of times to retries [findPreviewMethodsCachedValue] if failed.
   */
  private const val MAX_NON_BLOCKING_ACTION_RETRIES = 3

  /**
   * Returns all the `@Composable` functions in the [vFile] that are also tagged with `@Preview`.
   */
  override suspend fun findPreviewMethods(project: Project, vFile: VirtualFile): Collection<PreviewElement> {
    val psiFile = getPsiFileSafely(project, vFile) ?: return emptyList()
    // This method will try to obtain the result MAX_NON_BLOCKING_ACTION_RETRIES, waiting 10 milliseconds more on every retry.
    // findPreviewMethodsCachedValue uses a non blocking read action so this allows for the action to be cancelled. The action will be
    // retried again. If MAX_NON_BLOCKING_ACTION_RETRIES retries are done, this method will return an empty list of elements.
    return withContext(workerThread) {
      var retries = MAX_NON_BLOCKING_ACTION_RETRIES
      var result: Collection<PreviewElement>? = null
      while (result == null && retries > 0) {
        retries--
        val promiseResult = CachedValuesManager.getManager(project).getCachedValue(psiFile,
                                                                                   findPreviewMethodsCachedValue(project, vFile, psiFile))
        result = promiseResult.await()
        if (promiseResult.isSucceeded) break // No need to retry even if the result is null, the result is valid
        if (result == null) delay((MAX_NON_BLOCKING_ACTION_RETRIES - retries) * 10L)
      }
      result ?: emptyList()
    }
  }

  @JvmStatic
  private fun findPreviewMethodsCachedValue(project: Project,
                                            vFile: VirtualFile,
                                            psiFile: PsiFile): CachedValueProvider<Promise<Collection<PreviewElement>?>> =
    CachedValueProvider {
      val promise = ReadAction
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

      CachedValueProvider.Result.create(promise, psiFile, PromiseModificationTracker(promise))
    }
}