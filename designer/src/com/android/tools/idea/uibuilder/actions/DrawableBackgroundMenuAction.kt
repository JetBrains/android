/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.surface.DesignSurfaceSettings
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.BorderLayer
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.idea.uibuilder.surface.ScreenViewLayer
import com.android.tools.idea.uibuilder.surface.ScreenViewProvider
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D

/**
 * A dropdown menu used in drawable resource to change the background.
 */
class DrawableBackgroundMenuAction : DropDownAction("", "Drawable Background", StudioIcons.LayoutEditor.Toolbar.VIEW_MODE) {
  init {
    addAction(SetScreenViewProviderAction("None", "None", DrawableBackgroundType.NONE))
    addAction(SetScreenViewProviderAction("White", "White", DrawableBackgroundType.WHITE))
    addAction(SetScreenViewProviderAction("Black", "Black", DrawableBackgroundType.BLACK))
    addAction(SetScreenViewProviderAction("Checkered", "Checkered", DrawableBackgroundType.CHECKERED))
  }
}

enum class DrawableBackgroundType {
  NONE,
  WHITE,
  BLACK,
  CHECKERED
}

/**
 * [ToggleAction] to that sets a specific [DrawableBackgroundType] to the [DrawableBackgroundLayer].
 */
private class SetScreenViewProviderAction(name: String, description: String, private val backgroundType: DrawableBackgroundType)
  : ToggleAction(name, description, null) {

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.getRequiredData(PlatformDataKeys.PROJECT)
    val file = e.getRequiredData(PlatformDataKeys.VIRTUAL_FILE)

    val currentType = DesignSurfaceSettings.getInstance(project).surfaceState.loadDrawableBackgroundType(project, file)
    return currentType == backgroundType
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.getRequiredData(PlatformDataKeys.PROJECT)
    val file = e.getRequiredData(PlatformDataKeys.VIRTUAL_FILE)

    DesignSurfaceSettings.getInstance(project).surfaceState.saveDrawableBackgroundType(project, file, backgroundType)

    val surface = e.getData(DESIGN_SURFACE) as? NlDesignSurface ?: return
    val resourceViewProvider = surface.screenViewProvider as? DrawableScreenViewProvider ?: return
    resourceViewProvider.setDrawableBackgroundType(backgroundType)
    surface.repaint()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/**
 * Provide the custom [ScreenView] to the current [NlDesignSurface] for the drawable files.
 */
class DrawableScreenViewProvider(private val defaultType: DrawableBackgroundType) : ScreenViewProvider {
  override val displayName: String = "Drawable Mode"
  private var myDrawableBackgroundLayer: DrawableBackgroundLayer? = null

  fun setDrawableBackgroundType(type: DrawableBackgroundType) {
    myDrawableBackgroundLayer?.backgroundType = type
  }

  override fun createPrimarySceneView(surface: NlDesignSurface, manager: LayoutlibSceneManager): ScreenView {
    return ScreenView.newBuilder(surface, manager)
      .withLayersProvider { screenView -> createScreenLayer(screenView) }
      .build()
  }

  override val surfaceType: LayoutEditorState.Surfaces = LayoutEditorState.Surfaces.SCREEN_SURFACE

  private fun createScreenLayer(screenView: ScreenView): ImmutableList<Layer> {
    val backgroundLayer = DrawableBackgroundLayer(screenView, defaultType)
    myDrawableBackgroundLayer = backgroundLayer
    val borderLayer = BorderLayer(screenView)
    val screenViewLayer = ScreenViewLayer(screenView)
    return ImmutableList.of(backgroundLayer, borderLayer, screenViewLayer)
  }
}

private const val GRID_WIDTH = 12
private val CHECKERED_GRID_GRAY = Color(236, 236, 236)

/**
 * The background layer of the custom [ScreenView] provided by [DrawableScreenViewProvider].
 */
private class DrawableBackgroundLayer(private val screenView: ScreenView, var backgroundType: DrawableBackgroundType) : Layer() {
  private val dim = Dimension()

  override fun paint(gc: Graphics2D) {
    val currentType = backgroundType
    if (currentType == DrawableBackgroundType.NONE) {
      return
    }

    val startX = screenView.x
    val startY = screenView.y
    screenView.getScaledContentSize(dim)
    val width = dim.width
    val height = dim.height

    gc.clipRect(startX, startY, width, height)
    if (currentType == DrawableBackgroundType.WHITE) {
      gc.color = Color.WHITE
      gc.fillRect(startX, startY, width, height)
      return
    }

    if (currentType == DrawableBackgroundType.BLACK) {
      gc.color = Color.BLACK
      gc.fillRect(startX, startY, width, height)
      return
    }

    gc.color = Color.WHITE
    gc.fillRect(startX, startY, width, height)

    var isOddRow = false
    gc.color = CHECKERED_GRID_GRAY
    for (y in startY until (startY + height) step GRID_WIDTH) {
      val shift = if (isOddRow) 0 else GRID_WIDTH
      for (x in (startX + shift) until (startX + width) step (2 * GRID_WIDTH)) {
        gc.fillRect(x, y, GRID_WIDTH, GRID_WIDTH)
      }
      isOddRow = !isOddRow
    }
  }
}
