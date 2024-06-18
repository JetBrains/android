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
import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.compose.COMPOSABLE_ANNOTATION_NAME
import com.android.tools.idea.AndroidPsiUtils.getPsiFileSafely
import com.android.tools.idea.compose.PsiComposePreviewElement
import com.android.tools.idea.compose.preview.analytics.MultiPreviewEvent
import com.android.tools.idea.compose.preview.analytics.MultiPreviewNode
import com.android.tools.idea.compose.preview.analytics.MultiPreviewUsageTracker
import com.android.tools.idea.preview.annotations.findAnnotatedMethodsValues
import com.android.tools.idea.preview.annotations.hasAnnotation
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.core.getFqNameByDirectory
import org.jetbrains.uast.UMethod

/** [ComposeFilePreviewElementFinder] that uses `@Preview` annotations. */
object AnnotationFilePreviewElementFinder : ComposeFilePreviewElementFinder {
  override suspend fun hasPreviewElements(project: Project, vFile: VirtualFile) =
    findAnnotatedMethodsValues(
        project,
        vFile,
        COMPOSABLE_ANNOTATION_FQ_NAME,
        COMPOSABLE_ANNOTATION_NAME,
      ) { methods ->
        getPreviewNodes(methods, false)
      }
      .any()

  override suspend fun hasComposableMethods(project: Project, vFile: VirtualFile) =
    hasAnnotation(project, vFile, COMPOSABLE_ANNOTATION_FQ_NAME, COMPOSABLE_ANNOTATION_NAME)

  /**
   * Returns all the preview elements in the [vFile]. Preview elements are `@Composable` functions
   * that are also tagged with `@Preview`. A `@Composable` function tagged with `@Preview` can
   * return multiple preview elements.
   */
  override suspend fun findPreviewElements(
    project: Project,
    vFile: VirtualFile,
  ): Collection<PsiComposePreviewElement> {
    return findAnnotatedMethodsValues(
      project,
      vFile,
      COMPOSABLE_ANNOTATION_FQ_NAME,
      COMPOSABLE_ANNOTATION_NAME,
    ) { methods ->
      val previewNodes = getPreviewNodes(methods, includeAllNodes = true)
      val previewElements = previewNodes.filterIsInstance<PsiComposePreviewElement>().distinct()

      if (previewElements.any()) {
        getPsiFileSafely(project, vFile)?.let { psiFile ->
          MultiPreviewUsageTracker.getInstance(psiFile.androidFacet)
            .logEvent(
              MultiPreviewEvent(
                previewNodes.filterIsInstance<MultiPreviewNode>(),
                "${psiFile.getFqNameByDirectory().asString()}.${psiFile.name}",
              )
            )
        }
      }

      previewElements
    }
  }

  @VisibleForTesting
  internal fun getPreviewNodes(methods: List<UMethod>, includeAllNodes: Boolean) =
    methods.asSequence().flatMap {
      ProgressManager.checkCanceled()
      getPreviewNodes(it, includeAllNodes = includeAllNodes)
    }
}
