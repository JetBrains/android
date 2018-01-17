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
package com.android.tools.idea.uibuilder.handlers.assistant

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.editor.AnimationToolbar
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutHandler
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import java.awt.BorderLayout
import javax.swing.JPanel

class TransitionLayoutAssistantPanel(val designSurface: DesignSurface, val component: NlComponent, val close: Function0<Unit>) :
    JPanel() {
  val toolbar: AnimationToolbar

  init {
    layout = BorderLayout()

    val helper = MotionLayoutHandler.MotionLayoutComponentHelper(component)
    val maxTimeMs = helper.maxTimeMs
    toolbar = AnimationToolbar.createAnimationToolbar({}, { timeMs ->
      val sceneManager = designSurface.sceneManager as? LayoutlibSceneManager
      if (sceneManager != null) {
        sceneManager.setElapsedFrameTimeMs(timeMs)
        helper.setValue((timeMs - 500L) / maxTimeMs.toFloat())
      }
    }, 16, 500L, maxTimeMs + 500L)
    add(toolbar)
  }

  override fun setVisible(aFlag: Boolean) {
    super.setVisible(aFlag)

    if (!aFlag) {
      toolbar.stop()
    }
  }
}
