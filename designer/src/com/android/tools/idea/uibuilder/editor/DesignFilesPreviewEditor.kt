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

import android.graphics.drawable.AnimationDrawable
import android.widget.ImageView
import com.android.SdkConstants
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.actions.ANIMATION_TOOLBAR
import com.android.tools.idea.common.editor.DesignToolsSplitEditor
import com.android.tools.idea.common.editor.DesignerEditor
import com.android.tools.idea.common.editor.DesignerEditorPanel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceSettings
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.actions.DrawableScreenViewProvider
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.type.AdaptiveIconFileType
import com.android.tools.idea.uibuilder.type.AnimatedStateListFileType
import com.android.tools.idea.uibuilder.type.AnimatedStateListTempFileType
import com.android.tools.idea.uibuilder.type.AnimatedVectorFileType
import com.android.tools.idea.uibuilder.type.AnimationListFileType
import com.android.tools.idea.uibuilder.type.DrawableFileType
import com.android.tools.idea.uibuilder.type.getPreviewConfig
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Consumer
import javax.swing.JComponent
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
    val workBench = WorkBench<DesignSurface<*>>(myProject, WORKBENCH_NAME, this, this)
    val surface: (panel: DesignerEditorPanel) -> DesignSurface<*> = {
      NlDesignSurface.builder(myProject, this)
        .setActionManagerProvider { surface ->
          PreviewEditorActionManagerProvider(surface as NlDesignSurface, file?.toPsiFile(myProject)?.typeOf())
        }
        .build()
        .apply {
          val screenViewProvider = if (StudioFlags.NELE_DRAWABLE_BACKGROUND_MENU.get()) {
            when (file?.toPsiFile(project)?.typeOf()) {
              is AdaptiveIconFileType, is DrawableFileType -> {
                val lastBackgroundType = DesignSurfaceSettings.getInstance(project).surfaceState.loadDrawableBackgroundType(project, file!!)
                DrawableScreenViewProvider(lastBackgroundType)
              }
              else -> NlScreenViewProvider.RENDER
            }
          }
          else {
            NlScreenViewProvider.RENDER
          }
          setScreenViewProvider(screenViewProvider, false)
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

    return DesignerEditorPanel(this, myProject, myFile, workBench, surface, NlComponentRegistrar, modelProvider, { emptyList() },
                               { panel, model -> addAnimationToolbar(panel, model) },
                               AndroidEditorSettings.getInstance().globalState.preferredDrawableSurfaceState())
  }

  private inner class MyAnimatedSelectorModelProvider : DesignerEditorPanel.ModelProvider {
    override fun createModel(parentDisposable: Disposable,
                             project: Project,
                             facet: AndroidFacet,
                             componentRegistrar: Consumer<NlComponent>,
                             file: VirtualFile): NlModel {
      val config = ConfigurationManager.getOrCreateInstance(facet).getPreviewConfig()
      animatedSelectorModel = WriteCommandAction.runWriteCommandAction(project, Computable {
        AnimatedSelectorModel(file, parentDisposable, project, facet, componentRegistrar, config)
      })
      return animatedSelectorModel!!.getNlModel()
    }
  }

  private fun addAnimationToolbar(panel: DesignerEditorPanel, model: NlModel?): JPanel? {
    val surface = panel.surface
    val toolbar = if (StudioFlags.NELE_ANIMATED_SELECTOR_PREVIEW.get() && model?.type is AnimatedStateListTempFileType) {
      AnimatedSelectorToolbar.createToolbar(this, animatedSelectorModel!!, AnimatedSelectorListener(surface), 16, 0L)
    }
    else if (StudioFlags.NELE_ANIMATIONS_PREVIEW.get() && model?.type is AnimatedVectorFileType) {
      // If opening an animated vector, add an unlimited animation bar
      AnimationToolbar.createUnlimitedAnimationToolbar(this, AnimatedVectorListener(surface), 16, 0L)
    }
    else if (StudioFlags.NELE_ANIMATIONS_LIST_PREVIEW.get() && model?.type is AnimationListFileType) {
      // If opening an animation list, add an animation bar with progress
      val animationDrawable = (surface.getSceneManager(model) as? LayoutlibSceneManager)?.let { findAnimationDrawable(it) }
      val maxTimeMs = animationDrawable?.let { drawable ->
        (0 until drawable.numberOfFrames).sumByLong { index -> drawable.getDuration(index).toLong() }
      } ?: 0L
      val oneShotString = animationDrawable?.isOneShot ?: false
      AnimationToolbar.createAnimationToolbar(this, AnimationListListener(surface), 16, 0, maxTimeMs)
        .apply { setLooping(!oneShotString) }
    }
    else {
      null
    }
    DataManager.registerDataProvider(panel) { if (ANIMATION_TOOLBAR.`is`(it)) toolbar else null }
    if (toolbar != null) {
      myProject.messageBus.connect(toolbar).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
          if ((event.oldEditor as? DesignToolsSplitEditor)?.designerEditor == this@DesignFilesPreviewEditor) {
            // pause the animation when this editor loses the focus.
            if (toolbar.getPlayStatus() == PlayStatus.PLAY) {
              toolbar.pause()
            }
          }
          else if ((event.newEditor as? DesignToolsSplitEditor)?.designerEditor == this@DesignFilesPreviewEditor) {
            // Needs to reinflate when grabbing the focus back. This makes sure the elapsed frame time is correct when animation resuming.
            toolbar.forceElapsedReset = true
          }
        }
      })
    }
    return toolbar
  }

  override fun getName() = "Design"
}

/**
 * The [com.android.tools.idea.common.editor.ActionManager] for [DesignFilesPreviewEditor]. This gives the chance to have different actions
 * depends on the give [fileType].
 */
class PreviewEditorActionManagerProvider(surface: NlDesignSurface,
                                         private val fileType: DesignerEditorFileType?) : NlActionManager(surface) {
  override fun getSceneViewContextToolbar(sceneView: SceneView): JComponent? {
    return when (fileType) {
      is AnimatedStateListFileType, is AnimatedStateListTempFileType, is AnimatedVectorFileType, is AnimationListFileType -> null
      else -> super.getSceneViewContextToolbar(sceneView)
    }
  }
}

/**
 * Animation listener for <animated-vector>.
 */
private class AnimatedVectorListener(val surface: DesignSurface<*>) : AnimationListener {
  override fun animateTo(controller: AnimationController, framePositionMs: Long) {
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
        it.requestRenderAsync().whenComplete { _, _ ->
          // The shape may be changed if it is a vector drawable. Reinflate it.
          it.forceReinflate()
          // This rendering guarantees the elapsed frame time is 0 and it must re-inflates the drawable to have the correct shape.
          it.requestRenderAsync()
        }
      }
      else {
        // In practise, this else branch happens when the animation is playing.
        // The new render request is ignored when the previous request is not completed yet. Some frames are dropped when it happens.
        // We don't handle that case because dropping some frames for the playing animation is acceptable.
        it.setElapsedFrameTimeMs(framePositionMs)
        if (controller.forceElapsedReset) {
          it.forceReinflate()
          controller.forceElapsedReset = false
        }
        it.requestRenderAsync()
      }
    }
  }
}

/**
 * Animation listener for <animation-list>.
 */
private class AnimationListListener(val surface: DesignSurface<*>) : AnimationListener {
  private var currentAnimationDrawable: AnimationDrawable? = null
  private var modelTimeMap = listOf<Long>()

  override fun animateTo(controller: AnimationController, framePositionMs: Long) {
    (surface.sceneManager as? LayoutlibSceneManager)?.let { sceneManager ->
      val imageView = sceneManager.renderResult?.rootViews?.firstOrNull()?.viewObject as ImageView? ?: return
      val animationDrawable = imageView.drawable as? AnimationDrawable ?: return
      if (currentAnimationDrawable != animationDrawable) {
        updateAnimationDrawableInformation(controller, sceneManager)
        currentAnimationDrawable = animationDrawable
      }

      val targetImageIndex = findTargetDuration(animationDrawable, framePositionMs)
      animationDrawable.currentIndex = targetImageIndex
      sceneManager.requestRenderAsync()
    }
  }

  /**
   * Update the maximum time and repeating to toolbar. Pre-process a time map to find the target Drawable Frame when playing animation.
   */
  private fun updateAnimationDrawableInformation(controller: AnimationController, manager: LayoutlibSceneManager) {
    val animationDrawable = findAnimationDrawable(manager)
    modelTimeMap = if (animationDrawable != null) {
      val timeMap = mutableListOf<Long>()
      var durationSum = 0L
      repeat(animationDrawable.numberOfFrames) { index ->
        durationSum += animationDrawable.getDuration(index)
        timeMap.add(durationSum)
      }
      controller.setLooping(!animationDrawable.isOneShot)
      controller.setMaxTimeMs(durationSum)
      timeMap
    }
    else {
      emptyList()
    }
  }

  private fun findTargetDuration(animationDrawable: AnimationDrawable, framePositionMs: Long): Int {
    return binarySearch(modelTimeMap, framePositionMs, 0, animationDrawable.numberOfFrames - 1)
  }

  /**
   * Binary search to find an index which [modelTimeMap].get(index - 1) <= [target] < [modelTimeMap].get(index)`.
   * If [target] is larger than [modelTimeMap].get([modelTimeMap].size - 1), then we return [modelTimeMap].size - 1, because
   * AnimationDrawable stays at last image when animation ends.
  */
  private fun binarySearch(map: List<Long>, target: Long, start: Int, end: Int): Int {
    if (end <= start) {
      return end
    }
    val mid = (start + end) / 2
    return when {
      map[mid] < target -> binarySearch(map, target, mid + 1, end)
      target < map[mid] -> binarySearch(map, target, start, mid)
      else -> mid + 1 // map[mid] == target
    }
  }
}

/**
 * Animation listener for <animated-selector> file.
 * <animated-selector> may have embedded <animated-vector> and/or <animation-list>.
 */
private class AnimatedSelectorListener(val surface: DesignSurface<*>) : AnimationListener {
  private val animatedVectorDelegate = AnimatedVectorListener(surface)
  private val animationListDelegate = AnimationListListener(surface)

  override fun animateTo(controller: AnimationController, framePositionMs: Long) {
    (surface.sceneManager as? LayoutlibSceneManager)?.let {
      when (it.model.file.rootTag?.name) {
        SdkConstants.TAG_ANIMATED_VECTOR -> {
          animatedVectorDelegate.animateTo(controller, framePositionMs)
        }
        SdkConstants.TAG_ANIMATION_LIST -> animationListDelegate.animateTo(controller, framePositionMs)
        else -> {
          it.setElapsedFrameTimeMs(framePositionMs)
          it.requestRenderAsync()
        }
      }
    }
  }
}

private fun findAnimationDrawable(sceneManager: LayoutlibSceneManager): AnimationDrawable? {
  return (sceneManager.renderResult?.rootViews?.firstOrNull()?.viewObject as ImageView?)?.drawable as? AnimationDrawable
}

fun AndroidEditorSettings.GlobalState.preferredDrawableSurfaceState() = when(preferredDrawableEditorMode) {
  AndroidEditorSettings.EditorMode.CODE -> DesignerEditorPanel.State.DEACTIVATED
  AndroidEditorSettings.EditorMode.SPLIT -> DesignerEditorPanel.State.SPLIT
  AndroidEditorSettings.EditorMode.DESIGN -> DesignerEditorPanel.State.FULL
  else -> DesignerEditorPanel.State.SPLIT // default
}
