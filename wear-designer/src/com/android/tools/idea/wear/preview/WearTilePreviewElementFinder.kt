/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.wear.preview

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.runReadActionWithWritePriority
import com.android.tools.idea.preview.FilePreviewElementFinder
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.InheritanceUtil
import kotlinx.coroutines.withContext
import org.jetbrains.android.util.AndroidSlowOperations

/** Object that can detect wear tile preview elements in a file. */
internal object WearTilePreviewElementFinder : FilePreviewElementFinder<WearTilePreviewElement> {
  override suspend fun hasPreviewElements(project: Project, vFile: VirtualFile): Boolean {
    return readAction { PsiManager.getInstance(project).findFile(vFile)!! }.tileServiceSuccessors().any()
  }

  override suspend fun findPreviewElements(project: Project, vFile: VirtualFile): Collection<WearTilePreviewElement> {
    return readAction { PsiManager.getInstance(project).findFile(vFile)!! }
      .tileServiceSuccessors()
      .map { readAction { it.toWearTilePreviewElement() } }
      .toList()
  }
}

private const val TILE_SERVICE_CLASS = "androidx.wear.tiles.TileService"

/** Extension method that returns a (possibly empty) sequence of [PsiClass]es that are descendants on [TileService]. */
internal suspend fun PsiFile.tileServiceSuccessors(): Collection<PsiClass> = withContext(AndroidDispatchers.workerThread) {
  return@withContext when (this@tileServiceSuccessors) {
    is PsiClassOwner -> try {
      val classes = runReadActionWithWritePriority { this@tileServiceSuccessors.classes }
      // Properly detect inheritance from View in Smart mode
      runReadActionWithWritePriority {
        classes.filter { aClass ->
          aClass.isValid && aClass.extendsTileService()
        }
      }
    }
    catch (t: Exception) {
      Logger.getInstance(WearTilePreviewElementFinder::class.java).warn(t)
      emptyList()
    }
    else -> emptyList()
  }
}

/**
 * Extension method that detects if this [PsiClass] is a descendant of [TileService]. Has to be called from a read action.
 */
@Slow
internal fun PsiClass.extendsTileService(): Boolean = InheritanceUtil.isInheritor(this, TILE_SERVICE_CLASS)

/** Extension method that returns a [WearTilePreviewElement] from a [PsiClass]. */
internal fun PsiClass.toWearTilePreviewElement(): WearTilePreviewElement {
  val pointer = SmartPointerManager.createPointer<PsiElement>(this)
  return WearTilePreviewElement(
    PreviewDisplaySettings(this.name ?: "", null, false, false, null),
    pointer,
    pointer,
    this.qualifiedName ?: ""
  )
}