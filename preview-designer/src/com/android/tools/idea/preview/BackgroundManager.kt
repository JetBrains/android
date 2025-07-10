/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.preview

import com.android.tools.preview.PreviewDisplaySettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.SmartPsiElementPointer
import java.util.WeakHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Service that keeps track of runtime backgrounds using for Compose previews when using
 * [com.android.tools.preview.PreviewDisplaySettings.Background.Image]. This only maintains the
 * information in memory and it's only used when an image background has been explicitly set.
 */
@Service(Service.Level.PROJECT)
class BackgroundManager(project: Project) {
  private val background:
    WeakHashMap<SmartPsiElementPointer<*>, PreviewDisplaySettings.Background.Image> =
    WeakHashMap()
  private val _modificationTracker = SimpleModificationTracker()

  /**
   * [com.intellij.openapi.util.ModificationTracker] that changes every time backgrounds are
   * updated.
   */
  val modificationTracker: ModificationTracker = _modificationTracker
  private val _modificationFlow = MutableStateFlow(_modificationTracker.modificationCount)

  /**
   * [kotlinx.coroutines.flow.StateFlow] that gets a new value every time that backgrounds are
   * updated.
   */
  val modificationFlow: StateFlow<Long> = _modificationFlow

  private fun fireModification() {
    _modificationTracker.incModificationCount()
    _modificationFlow.value = _modificationTracker.modificationCount
  }

  /** Sets a background for the given [element]. */
  fun setBackground(
    element: SmartPsiElementPointer<*>,
    background: PreviewDisplaySettings.Background.Image?,
  ) {
    if (background == null) {
      this.background.remove(element)?.also { fireModification() }
    } else {
      this.background.put(element, background).also { if (it != background) fireModification() }
    }
  }

  /** Gets the background for the given [element]. */
  fun getBackground(element: SmartPsiElementPointer<*>): PreviewDisplaySettings.Background.Image? {
    return background[element]
  }

  companion object {
    fun getInstance(project: Project): BackgroundManager {
      return project.getService(BackgroundManager::class.java)
    }
  }
}
