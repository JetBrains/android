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
import com.android.tools.idea.preview.FilePreviewElementFinder
import com.android.tools.idea.preview.find.findAnnotatedMethodsValues
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.core.getFqNameByDirectory
import org.jetbrains.uast.UMethod

/** [FilePreviewElementFinder] for Compose Preview annotations. */
object AnnotationFilePreviewElementFinder : FilePreviewElementFinder<PsiComposePreviewElement> {
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
      flow {
        val previewNodes = getPreviewNodes(methods, includeAllNodes = true)
        val previewElements = previewNodes.filterIsInstance<PsiComposePreviewElement>().toSet()

        if (previewElements.firstOrNull() != null) {
          getPsiFileSafely(project, vFile)?.let { psiFile ->
            MultiPreviewUsageTracker.getInstance(psiFile.androidFacet)
              .logEvent(
                MultiPreviewEvent(
                  previewNodes.filterIsInstance<MultiPreviewNode>().toList(),
                  "${psiFile.getFqNameByDirectory().asString()}.${psiFile.name}",
                )
              )
          }
        }
        emitAll(previewElements.asFlow())
      }
    }
  }

  @VisibleForTesting
  internal fun getPreviewNodes(methods: List<UMethod>, includeAllNodes: Boolean) =
    methods.asFlow().flatMapConcat {
      ProgressManager.checkCanceled()
      getPreviewNodes(it, includeAllNodes = includeAllNodes)
    }
}
