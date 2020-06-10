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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * Responsible for opening the [AnimationInspectorPanel] and managing its state. When the bytecode manipulation mechanism intercepts calls
 * on `ui-tooling`, the [ComposePreviewAnimationManager] should provide methods that can be called to populate the animation inspector with
 * the intercepted data.
 */
internal object ComposePreviewAnimationManager {

  fun createAnimationInspectorPanel(surface: NlDesignSurface, parent: Disposable): AnimationInspectorPanel {
    val animationInspectorPanel = AnimationInspectorPanel(surface)
    Disposer.register(parent, animationInspectorPanel)
    return animationInspectorPanel
  }
}
