/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui.tree

import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.BuildAttributionViewControllersProvider
import com.android.build.attribution.ui.controllers.TaskIssueReporter
import com.android.build.attribution.ui.controllers.TreeNodeSelector
import com.android.build.attribution.ui.panels.AbstractBuildAttributionInfoPanel
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.CachingSimpleNode
import javax.swing.Icon
import javax.swing.JComponent

abstract class ControllersAwareBuildAttributionNode(
  aParent: ControllersAwareBuildAttributionNode?
) : CachingSimpleNode(aParent),
    BuildAttributionViewControllersProvider

abstract class AbstractBuildAttributionNode protected constructor(
  parent: ControllersAwareBuildAttributionNode,
  val nodeName: String
) : ControllersAwareBuildAttributionNode(parent), ComponentContainer {

  open val nodeId: String = if (parent is AbstractBuildAttributionNode) "${parent.nodeId} > $nodeName" else nodeName

  override val nodeSelector: TreeNodeSelector = parent.nodeSelector
  override val analytics: BuildAttributionUiAnalytics = parent.analytics
  override val issueReporter: TaskIssueReporter = parent.issueReporter

  abstract val pageType: BuildAttributionUiEvent.Page.PageType
  abstract val presentationIcon: Icon?
  abstract val issuesCountsSuffix: String?
  abstract val timeSuffix: String?

  override fun dispose() = Unit

  override fun getPreferredFocusableComponent(): JComponent {
    return component
  }

  override fun createPresentation(): PresentationData {
    val presentation = super.createPresentation()

    if (presentationIcon != null) presentation.setIcon(presentationIcon)

    presentation.addText(" $nodeName", SimpleTextAttributes.REGULAR_ATTRIBUTES)

    if (issuesCountsSuffix?.isNotBlank() == true) presentation.addText(" ${issuesCountsSuffix}", SimpleTextAttributes.GRAYED_ATTRIBUTES)

    return presentation
  }

  private var cachedComponent: JComponent? = null
    get() {
      if (field == null) {
        field = createComponent().init().apply {
          name = "infoPage"
        }
      }
      return field
    }

  final override fun getComponent(): JComponent {
    return cachedComponent!!
  }

  abstract fun createComponent(): AbstractBuildAttributionInfoPanel
}
