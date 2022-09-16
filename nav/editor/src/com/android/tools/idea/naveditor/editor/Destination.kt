// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor.editor

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_LABEL
import com.android.SdkConstants.ATTR_MODULE_NAME
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_INCLUDE
import com.android.resources.ResourceType
import com.android.tools.adtui.ImageUtils.iconToImage
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.draw.HQ_RENDERING_HINTS
import com.android.tools.idea.naveditor.model.schema
import com.android.tools.idea.naveditor.model.setAsStartDestination
import com.android.tools.idea.naveditor.model.startDestinationId
import com.android.tools.idea.naveditor.scene.NavColors.PLACEHOLDER_BACKGROUND
import com.android.tools.idea.naveditor.scene.NavColors.PLACEHOLDER_BORDER
import com.android.tools.idea.naveditor.scene.NavColors.PLACEHOLDER_TEXT
import com.android.tools.idea.uibuilder.model.createChild
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.jetbrains.android.dom.navigation.NavigationSchema
import java.awt.BasicStroke
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Rectangle
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.swing.ImageIcon

private const val THUMBNAIL_MAX_DIMENSION = 60f
private const val THUMBNAIL_BORDER_THICKNESS = 1f
private const val THUMBNAIL_OUTER_RADIUS = 5f
private const val THUMBNAIL_INNER_RADIUS = 3f
private val THUMBNAIL_BORDER_STROKE = BasicStroke(THUMBNAIL_BORDER_THICKNESS)
private val INCLUDE_ICON_WIDTH = 45
private val INCLUDE_ICON_HEIGHT = 60

sealed class Destination(protected open val parent: NlComponent) : Comparable<Destination> {
  /**
   * Add this to the graph. Must be called in a write action.
   */

  enum class DestinationOrder {
    PLACEHOLDER,
    FRAGMENT,
    INCLUDE,
    ACTIVITY,
    OTHER
  }

  abstract fun addToGraph()

  abstract val label: String
  abstract fun thumbnail(iconCallback: (VirtualFile, Dimension) -> ImageIcon, component: Component): Image
  abstract val typeLabel: String
  abstract val destinationOrder: DestinationOrder
  abstract val inProject: Boolean
  abstract val iconWidth: Int

  // open for testing
  open var component: NlComponent? = null

  override fun compareTo(other: Destination): Int {
    return comparator.compare(this, other)
  }

  protected fun createComponent(tagName: String): NlComponent? {
    val newComponent = parent.createChild(tagName, true)
    if (newComponent == null) {
      ApplicationManager.getApplication().invokeLater {
        Messages.showErrorDialog(parent.model.project, "Failed to create Destination!", "Error")
      }
    }
    return newComponent
  }

  abstract class ScreenShapedDestination(parent: NlComponent) : Destination(parent) {
    override val iconWidth
      get() = thumbnailDimension.width

    private val thumbnailDimension: Dimension
      get() {
        val model = parent.model
        val screenSize = model.configuration.deviceState?.orientation?.let { model.configuration.cachedDevice?.getScreenSize(it) }
                         ?: error("No device in configuration!")
        val ratio = THUMBNAIL_MAX_DIMENSION / maxOf(screenSize.height, screenSize.width)
        return Dimension((screenSize.width * ratio - 2 * THUMBNAIL_BORDER_THICKNESS).toInt(),
                         (screenSize.height * ratio - 2 * THUMBNAIL_BORDER_THICKNESS).toInt())
      }

    override fun thumbnail(iconCallback: (VirtualFile, Dimension) -> ImageIcon, component: Component): Image {
      val model = parent.model

      val result = UIUtil.createImage(component, thumbnailDimension.width + 2 * THUMBNAIL_BORDER_THICKNESS.toInt(),
                                      thumbnailDimension.height + 2 * THUMBNAIL_BORDER_THICKNESS.toInt(), BufferedImage.TYPE_INT_ARGB)

      val graphics = result.createGraphics()
      val roundRect = RoundRectangle2D.Float(THUMBNAIL_BORDER_THICKNESS, THUMBNAIL_BORDER_THICKNESS, thumbnailDimension.width.toFloat(),
                                             thumbnailDimension.height.toFloat(), THUMBNAIL_INNER_RADIUS, THUMBNAIL_INNER_RADIUS)
      val oldClip = graphics.clip
      graphics.clip = roundRect
      graphics.setRenderingHints(HQ_RENDERING_HINTS)
      drawThumbnailContents(model, thumbnailDimension, graphics, iconCallback)

      graphics.clip = oldClip
      graphics.color = PLACEHOLDER_BORDER
      graphics.stroke = THUMBNAIL_BORDER_STROKE
      roundRect.width = roundRect.width + THUMBNAIL_BORDER_THICKNESS
      roundRect.height = roundRect.height + THUMBNAIL_BORDER_THICKNESS
      roundRect.x = 0.5f
      roundRect.y = 0.5f
      roundRect.archeight = THUMBNAIL_OUTER_RADIUS
      roundRect.arcwidth = THUMBNAIL_OUTER_RADIUS
      graphics.draw(roundRect)

      return result
    }

    abstract fun drawThumbnailContents(model: NlModel, thumbnailDimension: Dimension, graphics: Graphics2D,
                                       iconCallback: (VirtualFile, Dimension) -> ImageIcon)

    protected fun drawBackground(thumbnailDimension: Dimension, graphics: Graphics2D) {
      graphics.color = PLACEHOLDER_BACKGROUND
      graphics.fillRect(THUMBNAIL_BORDER_THICKNESS.toInt(), THUMBNAIL_BORDER_THICKNESS.toInt(),
                        thumbnailDimension.width, thumbnailDimension.height)
    }
  }

  @VisibleForTesting
  data class RegularDestination @JvmOverloads constructor(
    override val parent: NlComponent, val tag: String, private val destinationLabel: String? = null, val destinationClass: PsiClass,
    val idBase: String = destinationClass.name ?: tag, private val layoutFile: XmlFile? = null,
    override val inProject: Boolean = true, val dynamicModuleName: String? = null)
    : ScreenShapedDestination(parent) {

    override fun drawThumbnailContents(model: NlModel, thumbnailDimension: Dimension, graphics: Graphics2D,
                                       iconCallback: (VirtualFile, Dimension) -> ImageIcon) {
      if (layoutFile != null) {
        val icon = iconCallback(layoutFile.virtualFile, thumbnailDimension)
        StartupUiUtil.drawImage(graphics, IconUtil.toImage(icon, ScaleContext.create(graphics)),
                                Rectangle(THUMBNAIL_BORDER_THICKNESS.toInt(), THUMBNAIL_BORDER_THICKNESS.toInt(), thumbnailDimension.width,
                                          thumbnailDimension.height), null)
      }
      else {
        drawBackground(thumbnailDimension, graphics)
        graphics.font = graphics.font.deriveFont(13).deriveFont(Font.BOLD)
        val unknownString = "?"
        val stringWidth = graphics.fontMetrics.charWidth('?')
        graphics.color = PLACEHOLDER_TEXT
        graphics.drawString(unknownString, (thumbnailDimension.width - stringWidth) / 2 + THUMBNAIL_BORDER_THICKNESS,
                            (thumbnailDimension.height + graphics.fontMetrics.ascent) / 2 + THUMBNAIL_BORDER_THICKNESS)
      }
    }

    override val typeLabel: String
      get() = parent.model.schema.getTagLabel(tag)

    override val destinationOrder = parent.model.schema.getDestinationTypesForTag(tag).let {
      when {
        it.contains(NavigationSchema.DestinationType.FRAGMENT) -> DestinationOrder.FRAGMENT
        it.contains(NavigationSchema.DestinationType.ACTIVITY) -> DestinationOrder.ACTIVITY
        else -> DestinationOrder.OTHER
      }
    }

    override val label = destinationLabel ?: layoutFile?.let { FileUtil.getNameWithoutExtension(it.name) } ?: destinationClass.name ?: tag

    override fun addToGraph() {
      val newComponent = createComponent(tag) ?: return

      newComponent.assignId(idBase)
      newComponent.setAndroidAttribute(ATTR_NAME, destinationClass.qualifiedName)
      newComponent.setAttribute(AUTO_URI, ATTR_MODULE_NAME, dynamicModuleName)
      newComponent.setAndroidAttribute(ATTR_LABEL, label)
      if (parent.startDestinationId == null) {
        newComponent.setAsStartDestination()
      }
      layoutFile?.let {
        // TODO: do this the right way
        val layoutId = "@${ResourceType.LAYOUT.getName()}/${FileUtil.getNameWithoutExtension(it.name)}"
        newComponent.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, layoutId)
      }
      component = newComponent
    }
  }

  data class IncludeDestination(val graph: String, override val parent: NlComponent) : Destination(parent) {
    override fun addToGraph() {
      val newComponent = createComponent(TAG_INCLUDE) ?: return

      newComponent.setAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_GRAPH,
                                "@${ResourceType.NAVIGATION.getName()}/${FileUtil.getNameWithoutExtension(graph)}")
      component = newComponent
    }

    override val iconWidth = INCLUDE_ICON_WIDTH

    override val label = graph

    // TODO: update
    override fun thumbnail(iconCallback: (VirtualFile, Dimension) -> ImageIcon, component: Component): Image {
      return iconToImage(StudioIcons.NavEditor.ExistingDestinations.NESTED).getScaledInstance(
        INCLUDE_ICON_WIDTH, INCLUDE_ICON_HEIGHT, Image.SCALE_SMOOTH)
    }

    override val typeLabel: String
      get() = parent.model.schema.getTagLabel(SdkConstants.TAG_INCLUDE)

    override val destinationOrder = DestinationOrder.INCLUDE

    override val inProject = true
  }

  data class PlaceholderDestination(override val parent: NlComponent) : ScreenShapedDestination(parent) {
    override fun addToGraph() {
      val newComponent = createComponent("fragment") ?: return

      newComponent.assignId("placeholder")
      if (parent.startDestinationId == null) {
        newComponent.setAsStartDestination()
      }
      component = newComponent
    }

    override val label = "placeholder"

    override fun drawThumbnailContents(model: NlModel, thumbnailDimension: Dimension, graphics: Graphics2D,
                                       iconCallback: (VirtualFile, Dimension) -> ImageIcon) {
      drawBackground(thumbnailDimension, graphics)
      graphics.color = PLACEHOLDER_BORDER
      graphics.drawLine(0, 0, thumbnailDimension.width, thumbnailDimension.height)
      graphics.drawLine(thumbnailDimension.width, 0, 0, thumbnailDimension.height)
    }

    override val typeLabel = "Empty destination"

    override val destinationOrder = DestinationOrder.PLACEHOLDER

    override val inProject = true
  }

  companion object {
    private val comparator = Comparator.comparing<Destination, Boolean> { it.inProject }
      .thenComparingInt { it.destinationOrder.ordinal }
      .thenComparing<String> { it.label }
  }
}