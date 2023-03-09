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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.updateSceneViewVisibilities

/** The interface for implementing the filtering logic of [ComposeFilterTextAction]. */
interface ComposeViewFilter {
  fun filter(query: String?)
}

/**
 * A filter which treats the query text as a single word and compare it to the model display name.
 * The case of the word doesn't matter. A [com.android.tools.idea.common.surface.SceneView] is
 * visible only when its model name contains the searched text, otherwise it is invisible. If the
 * query is blank or null, all [com.android.tools.idea.common.surface.SceneView] would be visible.
 */
class ComposeViewSingleWordFilter(private val surface: DesignSurface<*>) : ComposeViewFilter {
  override fun filter(query: String?) {
    if (query.isNullOrBlank()) {
      surface.updateSceneViewVisibilities { true }
      return
    }
    val trimmedKeyword = query.trim()

    surface.updateSceneViewVisibilities { view ->
      val name = view.sceneManager.model.modelDisplayName
      name?.contains(trimmedKeyword, ignoreCase = true) ?: false
    }
  }
}
