/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.uipreview

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.openapi.util.SimpleModificationTracker
import java.nio.file.Path


/**
 * Component that keeps a list of current paths that have class overlays.
 */
class ModuleClassLoaderOverlays private constructor() : ModificationTracker {
  private val modificationTracker = SimpleModificationTracker()

  /**
   * Path for the current overlay. The overlay will contain the last classes compiled.
   */
  var overlayPath: Path? = null
    @Synchronized set(newValue) {
      if (newValue == field) return
      // TODO(b/199367756): Remove the previous overlay from disk?
      field = newValue
      modificationTracker.incModificationCount()
    }
    @Synchronized get

  companion object {
    private val OVERLAY_KEY: NotNullLazyKey<ModuleClassLoaderOverlays, Module> = NotNullLazyKey.create(
      ModuleClassLoaderOverlays::class.qualifiedName!!) {
      ModuleClassLoaderOverlays()
    }

    @JvmStatic
    fun getInstance(module: Module): ModuleClassLoaderOverlays = OVERLAY_KEY.getValue(module)
  }

  override fun getModificationCount(): Long = modificationTracker.modificationCount
}