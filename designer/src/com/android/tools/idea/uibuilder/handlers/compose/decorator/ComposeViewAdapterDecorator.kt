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
package com.android.tools.idea.uibuilder.handlers.compose.decorator

import com.android.tools.compose.Anchor
import com.android.tools.compose.DESIGN_INFO_LIST_KEY
import com.android.tools.compose.DesignInfo
import com.android.tools.compose.PxBounds
import com.android.tools.compose.ViewDescription
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnection
import com.intellij.openapi.diagnostic.Logger
import java.awt.Rectangle
import kotlin.math.roundToInt

private val LOG = Logger.getInstance(ComposeViewAdapterDecorator::class.java)

/**
 * [SceneDecorator] for the ComposeViewAdapter class, the class is used to preview Composables on the DesignSurface and this [SceneDecorator]
 * provides additional visual information, based on the [DesignInfo] attached to the [SceneComponent].
 */
class ComposeViewAdapterDecorator : SceneDecorator() {

  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    super.addContent(list, time, sceneContext, component)
    if (!StudioFlags.COMPOSE_CONSTRAINT_VISUALIZATION.get()) return

    restoreDesignInfoList(component).forEach { designInfo ->
      addConstraints(list, designInfo.content, sceneContext, time)
    }
  }
}

/**
 * Returns the [DesignInfo] list from the [SceneComponent] cache if available.
 *
 * The cache does not need to be updated since new [SceneComponent]s are generated every time the Composable preview is updated.
 *
 * The cache is populated during the hierarchy creation for Compose preview.
 */
@Suppress("UNCHECKED_CAST")
private fun restoreDesignInfoList(component: SceneComponent): List<DesignInfo> {
  return (component.myCache[DESIGN_INFO_LIST_KEY] as? List<DesignInfo>) ?: emptyList()
}

private fun addConstraints(list: DisplayList,
                           viewDescriptionsMap: Map<String, ViewDescription>,
                           sceneContext: SceneContext,
                           time: Long) {
  // TODO(b/148788971): Find a way to relate SceneComponents to Views in this map (preferably during parsing), this would allow us to
  //  support things like highlighting based on selection or hover states.
  viewDescriptionsMap.values.forEach {
    addConnections(list, it, viewDescriptionsMap, sceneContext, time)
  }
}

private fun addConnections(
  list: DisplayList,
  viewDescription: ViewDescription,
  viewDescriptionsMap: Map<String, ViewDescription>,
  sceneContext: SceneContext,
  time: Long
) {
  val rooViewDescription = viewDescriptionsMap.values.firstOrNull { it.isRoot }
  if (rooViewDescription == null) {
    LOG.warn("Root view missing from constraints map")
    return
  }
  val rootId = rooViewDescription.viewId
  val rootRectangle = rooViewDescription.box.toSwingRectangle(sceneContext)

  val sourceRect = viewDescription.box.toSwingRectangle(sceneContext)
  if (viewDescription.isHelper) {
    ensureHelperSize(sourceRect, rootRectangle)
  }
  viewDescription.constraints.forEach { constraintInfo ->
    val targetIsRoot = constraintInfo.target == rootId
    val targetInfo = viewDescriptionsMap[constraintInfo.target]
    if (targetInfo == null) {
      LOG.warn("Constraint target: ${constraintInfo.target} not found in map")
      return@forEach
    }
    val targetRect = targetInfo.box.toSwingRectangle(sceneContext)
    val targetIsHelper = targetInfo.isHelper
    if (targetIsHelper) {
      ensureHelperSize(targetRect, rootRectangle)
    }
    // TODO(b/148788971): finish constraints support: bias, chains, etc.
    DrawConnection.buildDisplayList(
      list,
      null,
      DrawConnection.TYPE_NORMAL,
      sourceRect,
      constraintInfo.originAnchor.toDrawDirection(),
      targetRect,
      when {
        targetIsHelper -> constraintInfo.originAnchor.toOppositeDrawDirection()
        else -> constraintInfo.targetAnchor.toDrawDirection()
      },
      when {
        targetIsRoot -> DrawConnection.DEST_PARENT
        targetIsHelper -> DrawConnection.DEST_GUIDELINE
        else -> DrawConnection.DEST_NORMAL
      },
      false,
      sceneContext.pxToDp(constraintInfo.margin).roundToInt(),
      sceneContext.getSwingDimensionDip(sceneContext.pxToDp(constraintInfo.margin)),
      false,
      0.5f,
      DrawConnection.MODE_NORMAL,
      DrawConnection.MODE_NORMAL,
      time
    )
  }
}

private fun PxBounds.toSwingRectangle(sceneContext: SceneContext) = Rectangle(
  sceneContext.getSwingX(left),
  sceneContext.getSwingY(top),
  sceneContext.getSwingDimension(width),
  sceneContext.getSwingDimension(height)
)

/**
 * Helpers usually only have one value set on one of their coordinates, with size zero, for better constraint visualization, we extend their
 * length to match the root view. This results in constraints to the helper going towards the center of the layout instead of the edges.
 */
private fun ensureHelperSize(targetRectangle: Rectangle, rootRectangle: Rectangle) {
  if (targetRectangle.width == 0) {
    targetRectangle.x = rootRectangle.x
    targetRectangle.width = rootRectangle.width
  }
  else if (targetRectangle.height == 0) {
    targetRectangle.y = rootRectangle.y
    targetRectangle.height = rootRectangle.height
  }
}

private fun Anchor.toDrawDirection() = when (this) {
  Anchor.LEFT -> DrawConnection.DIR_LEFT
  Anchor.TOP -> DrawConnection.DIR_TOP
  Anchor.RIGHT -> DrawConnection.DIR_RIGHT
  Anchor.BOTTOM -> DrawConnection.DIR_BOTTOM
  Anchor.BASELINE -> DrawConnection.DIR_LEFT
  Anchor.CENTER -> DrawConnection.DIR_LEFT
  Anchor.CENTER_X -> DrawConnection.DIR_LEFT
  Anchor.CENTER_Y -> DrawConnection.DIR_LEFT
  Anchor.NONE -> DrawConnection.DIR_LEFT
}

private fun Anchor.toOppositeDrawDirection() = when (this) {
  Anchor.LEFT -> DrawConnection.DIR_RIGHT
  Anchor.TOP -> DrawConnection.DIR_BOTTOM
  Anchor.RIGHT -> DrawConnection.DIR_LEFT
  Anchor.BOTTOM -> DrawConnection.DIR_TOP
  Anchor.BASELINE -> DrawConnection.DIR_LEFT
  Anchor.CENTER -> DrawConnection.DIR_LEFT
  Anchor.CENTER_X -> DrawConnection.DIR_LEFT
  Anchor.CENTER_Y -> DrawConnection.DIR_LEFT
  Anchor.NONE -> DrawConnection.DIR_LEFT
}
