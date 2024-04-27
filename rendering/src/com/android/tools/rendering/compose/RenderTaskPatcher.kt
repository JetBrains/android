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
@file:JvmName("ComposePatcher")

package com.android.tools.rendering.compose

import com.android.SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER
import com.android.tools.rendering.classloading.ModuleClassLoader
import com.intellij.openapi.diagnostic.Logger

const val RECOMPOSER_CLASS = "androidx.compose.runtime.Recomposer"
const val COMPANION_FIELD = "Companion"
const val RECOMPOSER_COMPANION_CLASS = "$RECOMPOSER_CLASS\$$COMPANION_FIELD"

internal object RenderTaskPatcher {
  /**
   * This method checks if the given [ModuleClassLoader] is using Compose and, if it is it will
   * enable the Hot-Reload mode.
   *
   * Hot-Reload mode does not have much use for the preview but will add some extra behaviour on the
   * Compose Runtime that will improve the handling of exceptions during recomposition. This is
   * important since exceptions are usually not expected to happen during recomposition. When
   * Hot-Reload mode is enabled, the runtime will have special handling of exceptions and will run
   * additional clean-up code. This ensures that the runtime will not leak recompositions.
   */
  @JvmStatic
  fun enableComposeHotReloadMode(moduleClassLoader: ModuleClassLoader) {
    if (!moduleClassLoader.hasLoadedClass(CLASS_COMPOSE_VIEW_ADAPTER)) return
    try {
      val recomposerClass = moduleClassLoader.loadClass(RECOMPOSER_CLASS)
      val recomposerCompanion = recomposerClass.getField(COMPANION_FIELD).get(null)
      val recomposerCompanionClass = moduleClassLoader.loadClass(RECOMPOSER_COMPANION_CLASS)
      recomposerCompanionClass.methods
        .singleOrNull { it.name.contains("setHotReloadEnabled") }
        ?.apply { invoke(recomposerCompanion, true) }
    } catch (e: ReflectiveOperationException) {
      Logger.getInstance(RenderTaskPatcher::class.java).warn(e)
    }
  }
}
