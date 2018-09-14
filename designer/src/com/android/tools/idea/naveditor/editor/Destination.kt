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
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceType
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.util.iconToImage
import com.android.tools.idea.naveditor.model.schema
import com.android.tools.idea.naveditor.model.setAsStartDestination
import com.android.tools.idea.naveditor.model.startDestinationId
import com.android.tools.idea.naveditor.scene.ThumbnailManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.xml.XmlFile
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import icons.StudioIcons.NavEditor.ExistingDestinations.ACTIVITY
import icons.StudioIcons.NavEditor.ExistingDestinations.DESTINATION
import org.jetbrains.android.dom.navigation.NavigationSchema
import java.awt.Image
import java.awt.image.BufferedImage

private const val THUMBNAIL_X = 11
private const val THUMBNAIL_Y = 28
private const val THUMBNAIL_WIDTH = 50
private const val THUMBNAIL_HEIGHT = 56

sealed class Destination : Comparable<Destination> {
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
  abstract val thumbnail: Image
  abstract val typeLabel: String
  abstract val destinationOrder: DestinationOrder
  abstract val inProject: Boolean

  var component: NlComponent? = null

  override fun compareTo(other: Destination): Int {
    return comparator.compare(this, other)
  }

  @VisibleForTesting
  data class RegularDestination @JvmOverloads constructor(
    val parent: NlComponent, val tag: String, private val destinationLabel: String? = null, val destinationClass: PsiClass,
    val idBase: String = destinationClass.name ?: tag, private val layoutFile: XmlFile? = null,
    override val inProject: Boolean = true)
    : Destination() {

    // TODO: get border color from theme
    override val thumbnail: Image by lazy {
      val model = parent.model
      if (layoutFile != null) {
        val refinableImage = ThumbnailManager.getInstance(model.facet).getThumbnail(layoutFile, model.configuration)
        // TODO: wait for rendering nicely
        val image = refinableImage.terminalImage
        if (image != null) {
          val scale = (DESTINATION as? JBUI.RasterJBIcon)?.getScale(JBUI.ScaleType.PIX_SCALE) ?: 1.0
          val result = BufferedImage((73 * scale).toInt(), (94 * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
          DESTINATION.paintIcon(null, result.graphics, 0, 0)
          result.graphics.drawImage(image, (THUMBNAIL_X * scale).toInt(), (THUMBNAIL_Y * scale).toInt(),
                                    (THUMBNAIL_WIDTH * scale).toInt(), (THUMBNAIL_HEIGHT * scale).toInt(), null)
          return@lazy result
        }
      }
      val isActivity = model.schema.isActivityTag(tag)

      return@lazy iconToImage(if (isActivity) ACTIVITY else DESTINATION)
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
      val model = parent.model

      val tag = parent.tag.createChildTag(tag, null, null, true)
      val newComponent = model.createComponent(null, tag, parent, null, InsertType.CREATE)
      newComponent.assignId(idBase)
      newComponent.setAndroidAttribute(SdkConstants.ATTR_NAME, destinationClass.qualifiedName)
      newComponent.setAndroidAttribute(SdkConstants.ATTR_LABEL, label)
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

  data class IncludeDestination(val graph: String, val parent: NlComponent) : Destination() {
    override fun addToGraph() {
      val model = parent.model

      val tag = parent.tag.createChildTag(SdkConstants.TAG_INCLUDE, null, null, true)
      val newComponent = model.createComponent(null, tag, parent, null, InsertType.CREATE)
      newComponent.setAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_GRAPH,
                                "@${ResourceType.NAVIGATION.getName()}/${FileUtil.getNameWithoutExtension(graph)}")
      component = newComponent
    }

    override val label = graph

    override val thumbnail: Image by lazy { iconToImage(StudioIcons.NavEditor.ExistingDestinations.NESTED) }

    override val typeLabel: String
      get() = parent.model.schema.getTagLabel(SdkConstants.TAG_INCLUDE)

    override val destinationOrder = DestinationOrder.INCLUDE

    override val inProject = true
  }

  data class PlaceholderDestination(val parent: NlComponent) : Destination() {
    override fun addToGraph() {
      val model = parent.model

      val tag = parent.tag.createChildTag("fragment", null, null, true)
      val newComponent = model.createComponent(null, tag, parent, null, InsertType.CREATE)
      newComponent.assignId()
      if (parent.startDestinationId == null) {
        newComponent.setAsStartDestination()
      }
      component = newComponent
    }

    override val label = "placeholder"

    override val thumbnail: Image by lazy { iconToImage(StudioIcons.NavEditor.ExistingDestinations.PLACEHOLDER) }

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