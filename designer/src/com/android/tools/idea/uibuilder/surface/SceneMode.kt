/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger

enum class SceneMode(val displayName: String,
                     val primary: (NlDesignSurface, LayoutlibSceneManager) -> ScreenView,
                     val secondary: ((NlDesignSurface, LayoutlibSceneManager) -> ScreenView)? = null,
                     val visibleToUser: Boolean = true) {
  SCREEN_ONLY("Design", ::ScreenView),
  BLUEPRINT_ONLY("Blueprint", ::BlueprintView),
  BOTH("Design + Blueprint", ::ScreenView, ::BlueprintView),
  SCREEN_COMPOSE_ONLY("Compose", { surface, manager -> ScreenView(surface, manager, true, false) }, visibleToUser = false);

  operator fun next(): SceneMode {
    val values = values().filter { it.visibleToUser }
    return values[(ordinal + 1) % values.size]
  }

  fun createPrimarySceneView(surface: NlDesignSurface, manager: LayoutlibSceneManager): ScreenView =
      primary(surface, manager)

  fun createSecondarySceneView(surface: NlDesignSurface, manager: LayoutlibSceneManager): ScreenView? =
      secondary?.invoke(surface, manager)?.apply { isSecondary = true }

  companion object {

    @VisibleForTesting
    val DEFAULT_SCREEN_MODE = BOTH

    @VisibleForTesting
    val SCREEN_MODE_PROPERTY = "NlScreenMode"

    var cachedSceneMode: SceneMode? = null

    @Synchronized fun loadPreferredMode(): SceneMode {
      if (cachedSceneMode != null) {
        return cachedSceneMode!!
      }

      val modeName = PropertiesComponent.getInstance()?.getValue(SCREEN_MODE_PROPERTY, DEFAULT_SCREEN_MODE.name)
                     ?: return DEFAULT_SCREEN_MODE // In unit testing we might not have the PropertiesComponent
      cachedSceneMode = try {
        valueOf(modeName)
      }
      catch (e: IllegalArgumentException) {
        // If the code reach here, that means some of unexpected SceneMode is saved as user's preference.
        // In this case, return the default mode instead.
        Logger.getInstance(NlDesignSurface::class.java)
            .warn("The mode $modeName is not recognized, use default mode $SCREEN_MODE_PROPERTY instead")
        DEFAULT_SCREEN_MODE
      }

      return cachedSceneMode!!
    }

    @Synchronized  fun savePreferredMode(mode: SceneMode) {
      if (cachedSceneMode == mode) {
        return
      }

      cachedSceneMode = mode
      PropertiesComponent.getInstance().setValue(SCREEN_MODE_PROPERTY, mode.name)
    }
  }
}
