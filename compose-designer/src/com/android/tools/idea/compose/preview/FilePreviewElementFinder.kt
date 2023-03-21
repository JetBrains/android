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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.preview.util.ComposePreviewElement
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.psiFileChangeFlow
import com.android.tools.idea.preview.PreviewElementProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiModificationTracker
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage

/** Default [FilePreviewElementFinder]. This will be used by default by production code */
val defaultFilePreviewElementFinder = AnnotationFilePreviewElementFinder

/**
 * Interface to be implemented by classes able to find [ComposePreviewElement]s on [VirtualFile]s.
 */
interface FilePreviewElementFinder {
  /**
   * Returns whether this Preview element finder might apply to the given Kotlin file. The main
   * difference with [findPreviewMethods] is that method might be called on Dumb mode so it must not
   * use any indexes.
   */
  fun hasPreviewMethods(project: Project, vFile: VirtualFile): Boolean

  /**
   * Returns if this file contains `@Composable` methods. This is similar to [hasPreviewMethods] but
   * allows deciding if this file might allow previews to be added.
   */
  fun hasComposableMethods(project: Project, vFile: VirtualFile): Boolean

  /**
   * Returns all the [ComposePreviewElement]s present in the passed Kotlin [VirtualFile].
   *
   * This method always runs on smart mode.
   */
  suspend fun findPreviewMethods(
    project: Project,
    vFile: VirtualFile
  ): Collection<ComposePreviewElement>
}

/**
 * Creates a new [StateFlow] containing all the [ComposePreviewElement]s contained in the given
 * [psiFilePointer]. The given [FilePreviewElementFinder] is used to parse the file and obtain the
 * [ComposePreviewElement]s. This flow takes into account any changes in any Kotlin files since
 * Multi-Preview can cause previews to be altered in this file.
 */
@OptIn(FlowPreview::class)
suspend fun previewElementFlowForFile(
  scope: CoroutineScope,
  parentDisposable: Disposable,
  psiFilePointer: SmartPsiElementPointer<PsiFile>,
  filePreviewElementProvider: () -> FilePreviewElementFinder = ::defaultFilePreviewElementFinder,
): StateFlow<Set<ComposePreviewElement>> {
  val state = MutableStateFlow<Set<ComposePreviewElement>>(emptySet())

  val previewProvider =
    object : PreviewElementProvider<ComposePreviewElement> {
      override suspend fun previewElements(): Sequence<ComposePreviewElement> =
        withContext(AndroidDispatchers.workerThread) {
          filePreviewElementProvider()
            .findPreviewMethods(psiFilePointer.project, psiFilePointer.virtualFile)
            .asSequence()
        }
    }

  val kotlinPsiTracker =
    PsiModificationTracker.getInstance(psiFilePointer.project).forLanguages { lang ->
      lang.`is`(KotlinLanguage.INSTANCE)
    }
  scope.launch(AndroidDispatchers.workerThread) {
    psiFileChangeFlow(psiFilePointer.project, parentDisposable)
      // filter only by Kotlin changes. We care about any Kotlin changes since Multi-preview can
      // trigger changes from any file.
      .filter { it.fileType == KotlinFileType.INSTANCE }
      // do not generate events if there has not been modifications to the file since the last time
      .distinctUntilChangedBy { kotlinPsiTracker.modificationCount }
      // debounce to avoid many equality comparisons of the set
      .debounce(250)
      .collect { state.value = previewProvider.previewElements().toSet() }
  }

  // Set the initial state to the first elements found
  state.value = previewProvider.previewElements().toSet()
  return state
}

suspend fun previewElementFlowForFile(
  parentDisposable: Disposable,
  psiFilePointer: SmartPsiElementPointer<PsiFile>,
  filePreviewElementProvider: () -> FilePreviewElementFinder = ::defaultFilePreviewElementFinder,
): StateFlow<Set<ComposePreviewElement>> =
  previewElementFlowForFile(
    AndroidCoroutineScope(parentDisposable, coroutineContext),
    parentDisposable,
    psiFilePointer,
    filePreviewElementProvider
  )
