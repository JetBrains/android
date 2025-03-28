/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.common.model

import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.compose.preview.ComposeViewInfo
import com.android.tools.idea.compose.preview.navigation.findNavigatableComponentHit
import com.android.tools.idea.compose.preview.parseViewInfo
import com.android.tools.idea.rendering.parsers.PsiXmlTag
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.getAccessibilitySourceId
import com.android.tools.rendering.parsers.TagSnapshot
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.xml.XmlTag

/** Updates the [NlModel] for Compose Preview to match data coming following a render result. */
class AccessibilityModelUpdater : NlModelUpdaterInterface {
  /** Updates the root component of the [NlModel]. */
  override fun updateFromTagSnapshot(
    model: NlModel,
    newRoot: XmlTag?,
    roots: List<TagSnapshotTreeNode>,
  ) {
    var currentRootComponent = model.treeReader.components.firstOrNull()
    if (newRoot != null) {
      val currentRoot = currentRootComponent?.let { runReadAction { it.tag } }
      if (newRoot != currentRoot) {
        currentRootComponent = newRoot.let { model.createComponent(it) }
      }
    }
    model.treeReader.setRootComponent(currentRootComponent)
  }

  /**
   * Creates the [NlComponent] hierarchy based on the [AccessibilityNodeInfo] coming from the
   * [ViewInfo].
   */
  override fun updateFromViewInfo(model: NlModel, viewInfos: List<ViewInfo>) {
    val tagToViewInfo = mutableMapOf<XmlTag, ViewInfo>()
    viewInfos.forEach {
      val tag = (it.cookie as? TagSnapshot)?.tag as? PsiXmlTag ?: return@forEach
      tagToViewInfo[tag.psiXmlTag] = it
    }
    model.treeReader.components.forEach {
      val tag = runReadAction { it.tag }
      val viewInfo = tagToViewInfo[tag]
      it.accessibilityId = viewInfo?.getAccessibilitySourceId() ?: return@forEach
      val composeViewInfos =
        parseViewInfo(
          rootViewInfo = viewInfo,
          logger = Logger.getInstance(AccessibilityModelUpdater::class.java),
        )
      createTree(it, composeViewInfos, viewInfo.children, model, viewInfo.left, viewInfo.top)
    }
  }

  private fun createTree(
    root: NlComponent,
    composeViewInfos: List<ComposeViewInfo>,
    viewInfos: List<ViewInfo>,
    model: NlModel,
    rootGlobalX: Int,
    rootGlobalY: Int,
  ) {
    for (viewInfo in viewInfos) {
      val childComponent = NlComponent(model, viewInfo.getAccessibilitySourceId())
      NlComponentRegistrar.accept(childComponent)
      root.addChild(childComponent)
      val globalLeft = rootGlobalX + viewInfo.left
      val globalTop = rootGlobalY + viewInfo.top
      var midPointX = globalLeft + (viewInfo.right - viewInfo.left) / 2
      var midPointY = globalTop + (viewInfo.bottom - viewInfo.top) / 2
      if (composeViewInfos.isNotEmpty()) {
        val globalBounds = composeViewInfos.maxBy { it.bounds.width * it.bounds.height }.bounds
        // ViewInfo bounds from accessibility can be larger than ComposeViewInfo bounds.
        // If a view is on the edge of a preview, make sure to pick a point that is inside the
        // ComposeViewInfo bounds when looking for a navigatable.
        midPointX = minOf(midPointX, globalBounds.right)
        midPointY = minOf(midPointY, globalBounds.bottom)
      }
      val navigatable =
        findNavigatableComponentHit(model.module, composeViewInfos, midPointX, midPointY)
      if (navigatable != null) {
        childComponent.setNavigatable(navigatable)
      }
      createTree(childComponent, composeViewInfos, viewInfo.children, model, globalLeft, globalTop)
    }
  }
}
