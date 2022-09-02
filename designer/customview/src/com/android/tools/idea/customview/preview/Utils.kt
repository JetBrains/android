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
package com.android.tools.idea.customview.preview

import com.android.SdkConstants.CLASS_VIEW
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.runReadAction
import com.android.tools.idea.concurrency.runReadActionWithWritePriority
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.android.tools.idea.preview.representation.InMemoryLayoutVirtualFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.util.InheritanceUtil
import kotlinx.coroutines.withContext
import org.jetbrains.android.util.AndroidSlowOperations

internal const val CUSTOM_VIEW_PREVIEW_ID = "android-custom-view"

/**
 * [InMemoryLayoutVirtualFile] for custom views.
 */
internal class CustomViewLightVirtualFile(
  name: String,
  content: String,
  originFileProvider: () -> VirtualFile?
) : InMemoryLayoutVirtualFile(name, content, originFileProvider)

internal fun PsiClass.extendsView(): Boolean = AndroidSlowOperations.allowSlowOperationsInIdea<Boolean, Throwable> {
  InheritanceUtil.isInheritor(this, CLASS_VIEW)
}

internal suspend fun PsiFile.containsViewSuccessor(): Boolean = withContext(workerThread) {
  // Quickly reject non-custom view files. A custom view constructor should have Context and AttributeSet as parameters
  // (https://developer.android.com/training/custom-views/create-view#subclassview).
  // Heuristic to check that the code in the file uses android.util.AttributeSet
  if (runReadAction { viewProvider.document?.charsSequence?.contains("AttributeSet") } == false) {
    return@withContext false
  }

  return@withContext when (this@containsViewSuccessor) {
    is PsiClassOwner -> try {
      val classes = runReadActionWithWritePriority { this@containsViewSuccessor.classes }
      // Properly detect inheritance from View in Smart mode
      runReadActionWithWritePriority {
        classes.any { aClass ->
          aClass.isValid && aClass.extendsView()
        }
      }
    }
    catch (t: Exception) {
      Logger.getInstance(Utils::class.java).warn(t)
      false
    }
    else -> false
  }
}

internal fun FileEditor.getCustomViewPreviewManager(): CustomViewPreviewManager? = when(this) {
  is MultiRepresentationPreview -> this.currentRepresentation as? CustomViewPreviewManager
  else -> null
}

object Utils