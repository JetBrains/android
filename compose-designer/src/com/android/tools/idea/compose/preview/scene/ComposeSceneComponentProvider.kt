/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.scene

import com.android.tools.compose.DESIGN_INFO_LIST_KEY
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.DefaultHitProvider
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.ComposeViewInfo
import com.android.tools.idea.compose.preview.PxBounds
import com.android.tools.idea.compose.preview.designinfo.parseDesignInfoList
import com.android.tools.idea.compose.preview.isEmpty
import com.android.tools.idea.compose.preview.parseViewInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.model.viewInfo
import com.intellij.openapi.diagnostic.Logger

/**
 * [SceneManager.SceneComponentHierarchyProvider] for Compose Preview. It provides the ability to
 * sync Compose bounding boxes into SceneComponents.
 */
class ComposeSceneComponentProvider : SceneManager.SceneComponentHierarchyProvider {
  private val LOG = Logger.getInstance(ComposeSceneComponentProvider::class.java)

  /**
   * When true, we will map the existing Composables to SceneComponents. If false, we disable the
   * mapping.
   */
  var enabled = true
  private val hitProvider = DefaultHitProvider()

  /**
   * Maps a [ComposeViewInfo] into one or more [SceneComponent]s. This method will remove
   * [ComposeViewInfo]s if they have empty bounds or if their bounds are already present in the
   * hierarchy but it will still process the children.
   */
  private fun ComposeViewInfo.mapToSceneComponent(
    manager: SceneManager,
    component: NlComponent,
    boundsSet: MutableSet<PxBounds>
  ): List<SceneComponent> =
    if (bounds.isEmpty() || boundsSet.contains(bounds)) {
      children.flatMap { it.mapToSceneComponent(manager, component, boundsSet) }
    } else {
      listOf(
        SceneComponent(manager.scene, component, hitProvider).also {
          it.setPosition(
            Coordinates.pxToDp(manager, bounds.left),
            Coordinates.pxToDp(manager, bounds.top)
          )
          it.setSize(
            Coordinates.pxToDp(manager, bounds.width),
            Coordinates.pxToDp(manager, bounds.height)
          )
          it.setPrioritizeSelectedDrawState(false)
          boundsSet.add(bounds)
          children
            .flatMap { child -> child.mapToSceneComponent(manager, component, boundsSet) }
            .forEach { newComponent -> it.addChild(newComponent) }
        }
      )
    }

  /** Walks the given list of [SceneComponent] for debugging displaying all children. */
  private fun debugResult(result: List<SceneComponent>, indent: Int = 0): List<SceneComponent> =
    if (LOG.isDebugEnabled()) {
      result
    } else {
      result.onEach {
        val indentStr = " ".repeat(indent)

        LOG.debug("${indentStr}${it.drawX}, ${it.drawY} -> ${it.drawWidth}, ${it.drawHeight}")
        debugResult(it.children, indent = indent + 1)
      }
    }

  override fun createHierarchy(
    manager: SceneManager,
    component: NlComponent
  ): List<SceneComponent> {
    if (!enabled) return listOf()
    val viewInfo = component.viewInfo ?: return listOf()

    if (LOG.isDebugEnabled) {
      component.model.dataContext
        .getData(COMPOSE_PREVIEW_ELEMENT_INSTANCE)
        ?.displaySettings
        ?.name
        ?.let { LOG.debug(" ${it} component=${component} model=${component.model}") }
    }

    val sceneComponents =
      debugResult(
        parseViewInfo(viewInfo, logger = LOG).flatMap {
          it.mapToSceneComponent(manager, component, mutableSetOf())
        }
      )

    if (StudioFlags.COMPOSE_CONSTRAINT_VISUALIZATION.get()) {
      sceneComponents
        .getOrNull(0)
        ?.myCache
        ?.put(DESIGN_INFO_LIST_KEY, parseDesignInfoList(viewInfo))
    }
    return sceneComponents
  }

  // We do not sync information from the NlComponents back to SceneComponents in Compose
  override fun syncFromNlComponent(sceneComponent: SceneComponent) {}
}
