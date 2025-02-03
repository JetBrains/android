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
package com.android.tools.idea.common.surface

import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import kotlinx.coroutines.launch

/**
 * Requests a render and updates the [com.android.tools.idea.common.scene.SceneComponent]s
 * immediately after the render has completed. This allows that, after a scroll, the coordinates of
 * the bounding boxes are correctly updated.
 */
internal fun SceneManager.requestRenderAndUpdate() =
  AndroidCoroutineScope(this).launch {
    // Request an update that will refresh the bitmap coordinates
    requestRenderAndWait()
    // Update the SceneComponents to the latest coordinates to ensure the bounding boxes are
    // rendered correctly.
    // This is called "manually" since after a scroll, our NlModel and friends is not aware of a
    // change in the coordinates.
    update()
  }
