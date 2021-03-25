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
import com.android.tools.idea.common.editor.DesignToolsSplitEditor
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.type.AnimatedStateListFileType
import com.android.tools.idea.uibuilder.type.AnimatedVectorFileType
import com.android.tools.idea.uibuilder.type.AnimatedStateListTempFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Consumer
import javax.swing.JPanel

private const val WORKBENCH_NAME = "DESIGN_FILES_PREVIEW_EDITOR"

const val DESIGN_FILES_PREVIEW_EDITOR_ID = "android-preview-designer"

/**
 * [DesignerEditor] containing a [NlDesignSurface] without a border layer and a [WorkBench] without any tool windows. It should be used as
 * the preview portion of [DesignToolsSplitEditor] and to open non-editable [DesignerEditorFileType] files, such as fonts and drawables.
 */
class DesignFilesPreviewEditor(file: VirtualFile, project: Project) : DesignerEditor(file, project) {

  // Used when previewing animated selector file, to provide the required data for AnimatedSelectorToolbar.
  private var animatedSelectorModel: AnimatedSelectorModel? = null

  override fun getEditorId() = DESIGN_FILES_PREVIEW_EDITOR_ID

  override fun createEditorPanel(): DesignerEditorPanel {
    val workBench = WorkBench<DesignSurface>(myProject, WORKBENCH_NAME, this, this)
    val surface: (panel: DesignerEditorPanel) -> DesignSurface = {
      NlDesignSurface.builder(myProject, this)
        .build()
        .apply {
          setScreenViewProvider(NlScreenViewProvider.RENDER, false)
          // Make DesignSurface be focused when mouse clicked. This make the DataContext is provided from it while user clicks it.
          interactionPane.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
              interactionPane.requestFocus()
            }
          })
        }
    }

    val modelProvider =
      if (StudioFlags.NELE_ANIMATED_SELECTOR_PREVIEW.get() && (myFile.toPsiFile(myProject)?.typeOf() is AnimatedStateListFileType))
        MyAnimatedSelectorModelProvider()
      else DesignerEditorPanel.ModelProvider.defaultModelProvider

    return DesignerEditorPanel(this, myProject, myFile, workBench, surface, modelProvider, { emptyList() },
                               { designSurface, model -> addAnimationToolbar(designSurface, model) },
                               AndroidEditorSettings.getInstance().globalState.preferredDrawableSurfaceState())
  }

  private inner class MyAnimatedSelectorModelProvider : DesignerEditorPanel.ModelProvider {
    override fun createModel(parentDisposable: Disposable,
                             project: Project,
                             facet: AndroidFacet,
                             componentRegistrar: Consumer<NlComponent>,
                             file: VirtualFile): NlModel {
      val config = ConfigurationManager.getOrCreateInstance(facet).getConfiguration(file)
      animatedSelectorModel = WriteCommandAction.runWriteCommandAction(project, Computable {
        AnimatedSelectorModel(file, parentDisposable, project, facet, componentRegistrar, config)
      })
      return animatedSelectorModel!!.getNlModel()
    }
  }

  private fun addAnimationToolbar(surface: DesignSurface, model: NlModel?): JPanel? {
    if (StudioFlags.NELE_ANIMATED_SELECTOR_PREVIEW.get() && model?.type is AnimatedStateListTempFile) {
      return AnimatedSelectorToolbar.createToolbar(this, animatedSelectorModel!!, MyAnimationListener(surface), 16, 0L)
    }
    else if (StudioFlags.NELE_ANIMATIONS_PREVIEW.get() && model?.type is AnimatedVectorFileType) {
      // If opening an animated vector, add an unlimited animation bar
      return AnimationToolbar.createUnlimitedAnimationToolbar(this, MyAnimationListener(surface), 16, 0L)
    }
    return null
  }

  override fun getName() = "Design"
}

class MyAnimationListener(val surface: DesignSurface) : AnimationListener {
  override fun animateTo(framePositionMs: Long) {
    (surface.sceneManager as? LayoutlibSceneManager)?.let {
      if (framePositionMs <= 0L) {
        // This condition happens when animation is reset (stop and set elapsed frame to 0) or the elapsed frame is backed to negative.

        // For performance reason, if there is a rendering task, the new render request will be ignored and the callback of new request
        // will be triggered after the current rendering task is completed.
        // But the current rendering task may work on different elapsed frame time. We need to request a new render with correct elapsed
        // frame time after the current rendering task is completed.
        // For now we don't have a good way to get the completion of current rendering task. Thus we request a render first then request
        // another after the first one is completed. This makes sure the second request is not ignored and have correct elapsed frame
        // time. Even the first request is not ignored, it is still fine because we just have an additional render request. Having an
        // additional rendering doesn't cause the performance issue, because this condition only happens when animation is not playing.
        it.setElapsedFrameTimeMs(0L)
        it.requestRender().whenComplete { _, _ ->
          // The shape may be changed if it is a vector drawable. Reinflate it.
          it.forceReinflate()
          // This rendering guarantees the elapsed frame time is 0 and it must re-inflates the drawable to have the correct shape.
          it.requestRender()
        }
      }
      else {
        // We don't need to worry about wrong elapsed frame time here.
        // In practise, this else branch happens when:
        //   (1): The animation is playing.
        //   (2): The elapsed time is changed by back frame or forward frame. In this case the animation must paused or stop before.
        // In case 1, some of rendering can be ignored to improve the performance. It is similar to frame dropping.
        // In case 2, the animation is paused or stopped first so there is no rendering task. We can just simply request a new render
        // for the new elapsed frame time.
        it.setElapsedFrameTimeMs(framePositionMs)
        it.requestRender()
      }
    }
  }
}

fun AndroidEditorSettings.GlobalState.preferredDrawableSurfaceState() = when(preferredDrawableEditorMode) {
  AndroidEditorSettings.EditorMode.CODE -> DesignerEditorPanel.State.DEACTIVATED
  AndroidEditorSettings.EditorMode.SPLIT -> DesignerEditorPanel.State.SPLIT
  AndroidEditorSettings.EditorMode.DESIGN -> DesignerEditorPanel.State.FULL
  else -> DesignerEditorPanel.State.SPLIT // default
}
