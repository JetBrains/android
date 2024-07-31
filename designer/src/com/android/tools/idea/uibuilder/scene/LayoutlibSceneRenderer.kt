/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene

import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.NlModel
import com.android.tools.rendering.RenderResult
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// TODO(b/335424569): this class is meant to be used for extracting the rendering responsibilities
//   out of LayoutlibSceneManager. Add proper class description later.
internal class LayoutlibSceneRenderer(private val model: NlModel) {
  private val updateHierarchyLock = ReentrantLock()

  // TODO(b/335424569): make this method private
  fun updateHierarchy(result: RenderResult?): Boolean {
    var reverseUpdate = false
    try {
      updateHierarchyLock.withLock {
        reverseUpdate =
          if (result == null || !result.renderResult.isSuccess) {
            NlModelHierarchyUpdater.updateHierarchy(emptyList<ViewInfo>(), model)
          } else {
            NlModelHierarchyUpdater.updateHierarchy(result, model)
          }
      }
    } catch (ignored: InterruptedException) {}
    return reverseUpdate
  }
}
