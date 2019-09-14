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

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.collect.ImmutableList
import com.intellij.pom.Navigatable


/**
 * Handles navigation for compose preview when NlDesignSurface preview is clicked.
 */
class PreviewNavigationHandler : NlDesignSurface.NavigationHandler {

  private val map = HashMap<NlModel, Navigatable>()

  fun addMap(model: NlModel, navigatable: Navigatable) {
    map[model] = navigatable
  }

  override fun handleNavigate(sceneView: SceneView, models: ImmutableList<NlModel>, requestFocus: Boolean) {
    for (model in models) {
      if (sceneView.model == model) {
        val navigatable = map[model] ?: return
        navigatable.navigate(requestFocus)
        return
      }
    }
  }

  override fun dispose() {
    map.clear()
  }
}