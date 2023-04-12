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
package com.android.tools.idea.uibuilder.surface

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.validator.ValidatorData
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.ImmutableBiMap

/**
 * Parse the layout for Accessibility Testing Framework.
 * Builds the metadata required to link a11y lints to the source [NlComponent].
 */
class NlScannerLayoutParser {

  @VisibleForTesting
  val idToComponent: BiMap<Int, NlComponent> = HashBiMap.create()
  @VisibleForTesting
  val viewToComponent: BiMap<View, NlComponent> = HashBiMap.create()
  val nodeIdToComponent: BiMap<Long, NlComponent> = HashBiMap.create()

  /** Returns the list of [NlComponent] that is <[SdkConstants.VIEW_INCLUDE]> */
  val includeComponents: List<NlComponent>
    get() = _includeComponents

  private val _includeComponents = ArrayList<NlComponent>()

  /** Counts number of components with [View] info. */
  var componentCount = 0
    private set

  /** It's needed to build bridge from [Long] to [View] to [NlComponent]. */
  fun buildViewToComponentMap(component: NlComponent) {
    val root = tryFindingRootWithViewInfo(component)
    componentCount++
    val className = root.tagName
    if (className == SdkConstants.VIEW_INCLUDE) {
      if (root.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_IGNORE) != SdkConstants.ATTR_IGNORE_A11Y_LINTS) {
        _includeComponents.add(root)
        return
      }
    }

    root.viewInfo?.let { viewInfo ->
      val viewObj = viewInfo.viewObject
      if (viewObj is View) {
        viewToComponent[viewObj] = component

        if (View.NO_ID != viewObj.id) {
          idToComponent[viewObj.id] = component
        }
      }

      val accessibilityNodeInfo = viewInfo.accessibilityObject
      if (accessibilityNodeInfo is AccessibilityNodeInfo) {
        nodeIdToComponent[accessibilityNodeInfo.sourceNodeId] = component
      }

      component.children.forEach { buildViewToComponentMap(it) }
    }
  }

  /**
   * Look for the root view with appropriate view information from the immediate
   * children. Returns itself if it cannot find one.
   *
   * This is done to support views with data binding.
   */
  fun tryFindingRootWithViewInfo(component: NlComponent): NlComponent {
    if (component.viewInfo?.viewObject != null) {
      return component
    }

    component.children.forEach {
      if (it.viewInfo?.viewObject != null) {
        return it
      }
    }

    return component
  }

  /** Find the source [NlComponent] based on issue. If no source is found it returns null. */
  fun findComponent(result: ValidatorData.Issue, map: BiMap<Long, View>, nodeInfoMap: ImmutableBiMap<Long, AccessibilityNodeInfo>): NlComponent? {
    val view = map[result.mSrcId]
    if (view != null) {
      var toReturn = viewToComponent[view]
      if (toReturn == null) {
        // attempt to see if we can do id matching.
        toReturn = idToComponent[view.id]
      }
      return toReturn
    } else {
      val node = nodeInfoMap[result.mSrcId] ?: return null
      return nodeIdToComponent[node.sourceNodeId]
    }
  }

  /** Clear all maps and meta data from parsing. */
  fun clear() {
    viewToComponent.clear()
    idToComponent.clear()
    nodeIdToComponent.clear()
    _includeComponents.clear()
    componentCount = 0
  }

  /** Returns true if all maps are cleared. Flase otherwise. */
  fun isEmpty(): Boolean {
    return viewToComponent.isEmpty() && idToComponent.isEmpty() && _includeComponents.isEmpty()
  }
}