/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util

import com.android.tools.idea.common.scene.render
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.executeCallbacks

/**
 * Utility method that requests a given [LayoutlibSceneManager] to render. It applies logic that
 * specific to compose to render components that do not simply render in a first pass.
 */
internal suspend fun LayoutlibSceneManager.requestDoubleRender() {
  render()
  if (StudioFlags.COMPOSE_PREVIEW_DOUBLE_RENDER.get()) {
    executeCallbacks()
    render()
  }
}
