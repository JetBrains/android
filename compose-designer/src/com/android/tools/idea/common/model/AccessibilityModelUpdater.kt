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
import com.android.tools.idea.rendering.parsers.PsiXmlTag
import com.android.tools.idea.rendering.parsers.TagSnapshot
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.getAccessibilitySourceId
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.xml.XmlTag

/** Updates the [NlModel] for Compose Preview to match data coming following a render result. */
class AccessibilityModelUpdater : NlModel.NlModelUpdaterInterface {
  /** Updates the root component of the [NlModel]. */
  override fun updateFromTagSnapshot(
    model: NlModel,
    newRoot: XmlTag?,
    roots: MutableList<NlModel.TagSnapshotTreeNode>
  ) {
    var currentRootComponent = model.components.firstOrNull()
    if (newRoot != null) {
      val currentRoot = currentRootComponent?.let { runReadAction { it.tag } }
      if (newRoot != currentRoot) {
        currentRootComponent = newRoot.let { model.createComponent(it) }
      }
    }
    model.setRootComponent(currentRootComponent)
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
    model.components.forEach {
      val tag = runReadAction { it.tag }
      val viewInfo = tagToViewInfo[tag]
      it.accessibilityId = viewInfo?.getAccessibilitySourceId() ?: return@forEach
      createTree(it, viewInfo.children, model)
    }
  }

  private fun createTree(root: NlComponent, viewInfos: List<ViewInfo>, model: NlModel) {
    for (viewInfo in viewInfos) {
      val childComponent = NlComponent(model, viewInfo.getAccessibilitySourceId())
      NlComponentRegistrar.accept(childComponent)
      root.addChild(childComponent)
      createTree(childComponent, viewInfo.children, model)
    }
  }
}
