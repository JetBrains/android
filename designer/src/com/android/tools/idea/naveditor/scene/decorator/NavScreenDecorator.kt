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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.SdkConstants
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneComponent.DrawState
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.model.destinationType
import com.android.tools.idea.naveditor.scene.*
import com.android.tools.idea.naveditor.scene.draw.DrawFilledRectangle
import com.android.tools.idea.naveditor.scene.draw.DrawNavScreen
import com.android.tools.idea.naveditor.scene.draw.DrawRectangle
import com.android.tools.idea.rendering.ImagePool.Image
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.navigation.NavigationSchema
import java.awt.Font
import java.awt.Rectangle
import java.io.File
import java.util.concurrent.ExecutionException

/**
 * [SceneDecorator] responsible for creating draw commands for one screen/fragment/destination in the navigation editor.
 */

@NavCoordinate private val FRAGMENT_BORDER_SPACING = 2
@NavCoordinate private val FRAGMENT_OUTER_BORDER_THICKNESS = 2

// Swing defines rounded rectangle corners in terms of arc diameters instead of corner radii, so use 2x the desired radius value
@NavCoordinate private val ACTIVITY_ARC_SIZE = 12
@NavCoordinate private val ACTIVITY_PADDING = 8
@NavCoordinate private val ACTIVITY_TEXT_HEIGHT = 26
@NavCoordinate private val ACTIVITY_BORDER_THICKNESS = 2

class NavScreenDecorator : SceneDecorator() {

  override fun addFrame(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
  }

  override fun addBackground(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
  }

  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    super.addContent(list, time, sceneContext, component)

    if (component.nlComponent.destinationType == NavigationSchema.DestinationType.ACTIVITY) {
      addActivityContent(list, sceneContext, component)
    }
    else {
      addFragmentContent(list, sceneContext, component)
    }
  }

  override fun buildList(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    val displayList = DisplayList()
    super.buildList(displayList, time, sceneContext, component)
    list.add(NavigationDecorator.createDrawCommand(displayList, component))
  }

  private fun addFragmentContent(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
    @SwingCoordinate val drawRectangle = Coordinates.getSwingRectDip(sceneContext, component.fillDrawRect(0, null))
    list.add(DrawRectangle(drawRectangle, sceneContext.colorSet.frames, 1))

    val imageRectangle = Rectangle(drawRectangle)
    imageRectangle.grow(-1, -1)
    drawImage(list, sceneContext, component, imageRectangle)

    when (component.drawState) {
      DrawState.DRAG, DrawState.SELECTED, DrawState.HOVER -> {
        @SwingCoordinate val borderSpacing = Coordinates.getSwingDimension(sceneContext, FRAGMENT_BORDER_SPACING)
        @SwingCoordinate val outerBorderThickness = Coordinates.getSwingDimension(sceneContext, FRAGMENT_OUTER_BORDER_THICKNESS)

        val outerRectangle = Rectangle(drawRectangle)
        outerRectangle.grow(2 * borderSpacing, 2 * borderSpacing)

        list.add(DrawRectangle(outerRectangle, frameColor(sceneContext, component), outerBorderThickness, 2 * borderSpacing))
      }
      else -> {
      }
    }
  }

  private fun addActivityContent(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
    @SwingCoordinate val drawRectangle = Coordinates.getSwingRectDip(sceneContext, component.fillDrawRect(0, null))
    val arcSize = Coordinates.getSwingDimension(sceneContext, ACTIVITY_ARC_SIZE)
    list.add(DrawFilledRectangle(drawRectangle, sceneContext.colorSet.componentBackground, arcSize))

    @SwingCoordinate val strokeThickness = strokeThickness(sceneContext, component, ACTIVITY_BORDER_THICKNESS)
    list.add(DrawRectangle(drawRectangle, frameColor(sceneContext, component), strokeThickness, arcSize))

    val imageRectangle = Rectangle(drawRectangle)

    @SwingCoordinate val activityPadding = Coordinates.getSwingDimension(sceneContext, ACTIVITY_PADDING)
    imageRectangle.grow(-activityPadding, -activityPadding)

    @SwingCoordinate val activityTextHeight = Coordinates.getSwingDimension(sceneContext, ACTIVITY_TEXT_HEIGHT)
    imageRectangle.height -= (activityTextHeight - activityPadding)

    drawImage(list, sceneContext, component, imageRectangle)

    val textRectangle = Rectangle(drawRectangle.x, imageRectangle.y + imageRectangle.height, drawRectangle.width, activityTextHeight)
    list.add(DrawTruncatedText(DRAW_SCREEN_LABEL_LEVEL, "Activity", textRectangle, textColor(sceneContext, component),
        scaledFont(sceneContext, Font.BOLD), true))
  }

  private fun drawImage(list: DisplayList, sceneContext: SceneContext, component: SceneComponent, rectangle: Rectangle) {
    val image = buildImage(sceneContext, component)
    if (image == null) {
      list.add(DrawFilledRectangle(rectangle, sceneContext.colorSet.componentBackground, 0))
      list.add(DrawTruncatedText(DRAW_SCREEN_LABEL_LEVEL, "Preview Unavailable", rectangle, sceneContext.colorSet.text,
          scaledFont(sceneContext, Font.PLAIN), true))
    }
    else {
      list.add(DrawNavScreen(rectangle.x, rectangle.y, rectangle.width, rectangle.height, image))
    }
  }

  private fun buildImage(sceneContext: SceneContext, component: SceneComponent): Image? {
    val surface = sceneContext.surface ?: return null
    val configuration = surface.configuration
    val facet = surface.model!!.facet

    val layout = component.nlComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT) ?: return null
    val fileName = configuration?.resourceResolver?.findResValue(layout, false)?.value ?: return null
    val file = File(fileName)
    if (!file.exists()) {
      return null
    }
    val manager = ThumbnailManager.getInstance(facet)
    val virtualFile = VfsUtil.findFileByIoFile(file, false) ?: return null
    val psiFile = AndroidPsiUtils.getPsiFileSafely(surface.project, virtualFile) as? XmlFile ?: return null
    val thumbnail = manager.getThumbnail(psiFile, surface, configuration) ?: return null
    return try {
      // TODO: show progress icon during image creation
      thumbnail.get()
    }
    catch (ignore: InterruptedException) {
      // Shouldn't happen
      null
    }
    catch (ignore: ExecutionException) {
      null
    }
  }
}
