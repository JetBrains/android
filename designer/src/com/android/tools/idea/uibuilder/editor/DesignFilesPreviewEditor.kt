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
package com.android.tools.idea.uibuilder.editor

import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.editor.DesignerEditor
import com.android.tools.idea.common.editor.DesignerEditorPanel
import com.android.tools.idea.common.editor.SplitEditor
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.SceneMode
import com.android.tools.idea.uibuilder.type.AnimatedVectorFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

private const val WORKBENCH_NAME = "DESIGN_FILES_PREVIEW_EDITOR"

const val DESIGN_FILES_PREVIEW_EDITOR_ID = "android-preview-designer"

/**
 * [DesignerEditor] containing a [NlDesignSurface] without a border layer and a [WorkBench] without any tool windows. It should be used as
 * the preview portion of [SplitEditor] and to open non-editable [DesignerEditorFileType] files, such as fonts and drawables.
 */
class DesignFilesPreviewEditor(file: VirtualFile, project: Project) : DesignerEditor(file, project) {

  override fun getEditorId() = DESIGN_FILES_PREVIEW_EDITOR_ID

  override fun createEditorPanel(): DesignerEditorPanel {
    val workBench = WorkBench<DesignSurface>(myProject, WORKBENCH_NAME, this)
    val surface: (panel: DesignerEditorPanel) -> DesignSurface = {
      NlDesignSurface.build(myProject, this).apply {
        setCentered(true)
        setScreenMode(SceneMode.SCREEN_ONLY, false)
      }
    }

    return DesignerEditorPanel(this, myProject, myFile, workBench, surface, { emptyList() },
                               if (StudioFlags.NELE_ANIMATIONS_PREVIEW.get()) this::addAnimationToolbar else null)
  }

  private fun addAnimationToolbar(surface: DesignSurface, model: NlModel?) = if (model?.type is AnimatedVectorFileType) {
    // If opening an animated vector, add an unlimited animation bar
    AnimationToolbar.createUnlimitedAnimationToolbar(this, AnimationToolbar.AnimationListener { frameTimeMs ->
      (surface.sceneManager as? LayoutlibSceneManager)?.let {
        it.setElapsedFrameTimeMs(frameTimeMs)
        it.requestRender()
      }
    }, 16, 500L)
  }
  else null

  override fun getName() = "Design"
}
