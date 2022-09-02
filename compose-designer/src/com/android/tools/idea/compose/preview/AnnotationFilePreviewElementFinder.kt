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
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_NAME
import com.android.tools.idea.annotations.findAnnotatedMethodsValues
import com.android.tools.idea.annotations.hasAnnotations
import com.android.tools.idea.compose.preview.analytics.MultiPreviewEvent
import com.android.tools.idea.compose.preview.analytics.MultiPreviewNode
import com.android.tools.idea.compose.preview.analytics.MultiPreviewUsageTracker
import com.android.tools.idea.compose.preview.util.ComposePreviewElement
import com.android.tools.idea.compose.preview.util.FilePreviewElementFinder
import com.android.tools.idea.concurrency.getPsiFileSafely
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.core.getFqNameByDirectory
import org.jetbrains.uast.UMethod

/** [FilePreviewElementFinder] that uses `@Preview` annotations. */
object AnnotationFilePreviewElementFinder : FilePreviewElementFinder {
  override fun hasPreviewMethods(project: Project, vFile: VirtualFile) =
    hasAnnotations(
      project,
      vFile,
      setOf(COMPOSE_PREVIEW_ANNOTATION_FQN),
      COMPOSE_PREVIEW_ANNOTATION_NAME
    )

  override fun hasComposableMethods(project: Project, vFile: VirtualFile) =
    hasAnnotations(project, vFile, COMPOSABLE_FQ_NAMES, COMPOSABLE_ANNOTATION_NAME)

  /**
   * Returns all the `@Composable` functions in the [vFile] that are also tagged with `@Preview`.
   */
  override suspend fun findPreviewMethods(
    project: Project,
    vFile: VirtualFile
  ): Collection<ComposePreviewElement> {
    val psiFile = getPsiFileSafely(project, vFile) ?: return emptyList()
    return findAnnotatedMethodsValues(
      project,
      vFile,
      COMPOSABLE_FQ_NAMES,
      COMPOSABLE_ANNOTATION_NAME
    ) { methods ->
      val previewNodes = getPreviewNodes(methods, includeAllNodes = true)
      val previewElements = previewNodes.filterIsInstance<ComposePreviewElement>().distinct()

      if (previewElements.isNotEmpty()) {
        MultiPreviewUsageTracker.getInstance(psiFile.androidFacet)
          .logEvent(
            MultiPreviewEvent(
              previewNodes.filterIsInstance<MultiPreviewNode>(),
              "${psiFile.getFqNameByDirectory().asString()}.${psiFile.name}"
            )
          )
      }

      previewElements.asSequence()
    }
  }

  @VisibleForTesting
  internal fun getPreviewNodes(methods: List<UMethod>, includeAllNodes: Boolean) =
    methods.flatMap {
      ProgressManager.checkCanceled()
      getPreviewNodes(it, includeAllNodes = includeAllNodes)
    }
}
