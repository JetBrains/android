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

import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnection
import com.android.tools.idea.uibuilder.model.viewInfo
import com.intellij.openapi.diagnostic.Logger
import java.awt.Rectangle
import kotlin.math.roundToInt

private val LOG = Logger.getInstance(ComposeViewAdapterDecorator::class.java)

private const val DESIGN_INFO_KEY = "DesignInfo"

/**
 * [SceneDecorator] for the ComposeViewAdapter class, used to preview Composables on the DesignSurface.
 */
class ComposeViewAdapterDecorator : SceneDecorator() {

  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    super.addContent(list, time, sceneContext, component)
    // We only add constraints for pure Compose previews (when it has no parent), supporting it in XML previews, would require to manually
    // enable a flag on the ComposeViewAdapter XML declaration
    if (!StudioFlags.COMPOSE_CONSTRAINT_VISUALIZATION.get() && component.parent != null) return

    val designInfoList: List<DesignInfo> = restoreOrGetNewDesignInfoList(component)

    designInfoList.forEach { designInfo ->
      addConstraints(list, designInfo.content, component, sceneContext, time)
    }
  }
}

/**
 * Returns the [DesignInfo] list from the [SceneComponent] cache if available, otherwise, it will parse it from the ViewInfo object and
 * store it in the cache.
 *
 * The cache does not need to be updated since new [SceneComponent]s are generated every time the Composable preview is updated.
 */
@Suppress("UNCHECKED_CAST")
private fun restoreOrGetNewDesignInfoList(component: SceneComponent): List<DesignInfo> {
  return if (component.myCache[DESIGN_INFO_KEY] != null) {
    (component.myCache[DESIGN_INFO_KEY] as? List<DesignInfo>) ?: emptyList()
  }
  else {
    val newDesignInfoList = component.nlComponent.viewInfo?.toDesignInfoList() ?: emptyList()
    newDesignInfoList.also {
      component.myCache[DESIGN_INFO_KEY] = newDesignInfoList
    }
  }
}

private fun addConstraints(list: DisplayList,
                           viewDescriptionsMap: Map<String, ViewDescription>,
                           component: SceneComponent,
                           sceneContext: SceneContext,
                           time: Long) {
  val parentRect = convert(sceneContext, component.fillRect(null))
  viewDescriptionsMap.values.forEach {
    addConnections(list, it, viewDescriptionsMap, parentRect, sceneContext, time)
  }
}

private fun addConnections(
  list: DisplayList,
  viewDescription: ViewDescription,
  viewDescriptionsMap: Map<String, ViewDescription>,
  parentRectangle: Rectangle,
  sceneContext: SceneContext,
  time: Long
) {
  val rootId = viewDescriptionsMap.values.firstOrNull { it.isRoot }?.viewId
  if (rootId == null) {
    LOG.warn("No root declared in constraints map")
  }
  val sourceRect = viewDescription.box.toSwingRectangle(sceneContext)
  if (viewDescription.isHelper) {
    if (sourceRect.width == 0) {
      sourceRect.x = parentRectangle.x
      sourceRect.width = parentRectangle.width
    }
    else if (sourceRect.height == 0) {
      sourceRect.y = parentRectangle.y
      sourceRect.height = parentRectangle.height
    }
  }
  viewDescription.constraints.forEach { constraintInfo ->
    val targetIsParent = constraintInfo.target == rootId
    val targetInfo = viewDescriptionsMap[constraintInfo.target]
    if (targetInfo == null) {
      LOG.warn("Constraint target: ${constraintInfo.target} not found in map")
      return@forEach
    }
    val targetRect = targetInfo.box.toSwingRectangle(
      sceneContext)
    val targetIsHelper = targetInfo.isHelper
    if (targetIsHelper) {
      if (targetRect.width == 0) {
        targetRect.x = parentRectangle.x
        targetRect.width = parentRectangle.width
      }
      else if (targetRect.height == 0) {
        targetRect.y = parentRectangle.y
        targetRect.height = parentRectangle.height
      }
    }
    // TODO(b/13051896): finish constraints support: bias, chains, etc.
    DrawConnection.buildDisplayList(
      list,
      null,
      DrawConnection.TYPE_NORMAL,
      sourceRect,
      constraintInfo.originAnchor.toDrawDirection(),
      targetRect,
      if (targetIsHelper) constraintInfo.originAnchor.toOppositeDrawDirection() else constraintInfo.targetAnchor.toDrawDirection(),
      if (targetIsParent) DrawConnection.DEST_PARENT else if (targetIsHelper) DrawConnection.DEST_GUIDELINE else DrawConnection.DEST_NORMAL,
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

private fun convert(sceneContext: SceneContext, rectangle: Rectangle): Rectangle {
  rectangle.x = sceneContext.getSwingXDip(rectangle.x.toFloat())
  rectangle.y = sceneContext.getSwingYDip(rectangle.y.toFloat())
  rectangle.width = sceneContext.getSwingDimensionDip(rectangle.width.toFloat())
  rectangle.height = sceneContext.getSwingDimensionDip(rectangle.height.toFloat())
  return rectangle
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

