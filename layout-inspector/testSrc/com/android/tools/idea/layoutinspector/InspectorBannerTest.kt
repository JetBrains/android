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
package com.android.tools.idea.layoutinspector

import com.android.tools.adtui.actions.ZoomActualAction
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.idea.layoutinspector.model.StatusNotificationImpl
import com.android.tools.idea.layoutinspector.ui.INSPECTOR_BANNER_ACTION_PANEL_NAME
import com.android.tools.idea.layoutinspector.ui.INSPECTOR_BANNER_TEXT_NAME
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import java.awt.Container
import java.awt.Dimension
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JLabel

class InspectorBannerTest {

  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun testInitiallyHidden() {
    val banner = InspectorBanner(projectRule.project)
    assertThat(banner.isVisible).isFalse()
  }

  @Test
  fun testVisibleWithStatus() {
    val banner = InspectorBanner(projectRule.project)
    val bannerService = InspectorBannerService.getInstance(projectRule.project) ?: error("no banner")
    bannerService.notification = StatusNotificationImpl ("There is an error somewhere", emptyList())
    invokeAndWaitIfNeeded {
      UIUtil.dispatchAllInvocationEvents()
    }
    assertThat(banner.isVisible).isTrue()
  }

  @Test
  fun testInvisibleAfterEmptyStatus() {
    val banner = InspectorBanner(projectRule.project)
    val bannerService = InspectorBannerService.getInstance(projectRule.project) ?: error("no banner")
    bannerService.notification = StatusNotificationImpl("There is an error somewhere", emptyList())
    bannerService.notification = null
    invokeAndWaitIfNeeded {
      UIUtil.dispatchAllInvocationEvents()
    }
    assertThat(banner.isVisible).isFalse()
  }

  @Test
  fun testMessageShortenedOnLimitedSpace() {
    val banner = InspectorBanner(projectRule.project)
    val label = banner.getComponent(0)
    val action = object: AnAction("Fix") {
      override fun actionPerformed(event: AnActionEvent) {}
    }
    val bannerService = InspectorBannerService.getInstance(projectRule.project) ?: error("no banner")
    bannerService.notification = StatusNotificationImpl("There is an error somewhere", listOf(action))
    invokeAndWaitIfNeeded {
      UIUtil.dispatchAllInvocationEvents()
    }
    banner.size = banner.preferredSize
    banner.doLayout()
    assertThat(label.size).isEqualTo(label.preferredSize)
    banner.size = Dimension(banner.preferredSize.width - 80, banner.preferredSize.height)
    banner.doLayout()
    assertThat(label.size).isEqualTo(Dimension(label.preferredSize.width - 80, label.preferredSize.height))
  }

  @Test
  fun testSynchronization() {
    val banner = InspectorBanner(projectRule.project)
    val service = InspectorBannerService.getInstance(projectRule.project) ?: error("no banner")
    val actionPanel = banner.components.find { it.name == INSPECTOR_BANNER_ACTION_PANEL_NAME } as Container
    val text = banner.components.find { it.name == INSPECTOR_BANNER_TEXT_NAME } as JLabel
    var addedSecond = false
    // Add a listener that will change the banner in the middle of when it's being changed initially
    actionPanel.addContainerListener(object: ContainerAdapter() {
      override fun componentAdded(e: ContainerEvent?) {
        if (!addedSecond) {
          addedSecond = true
          service.notification = StatusNotificationImpl("There is an error somewhere else",
                                                        listOf(ZoomToFitAction.getInstance(), ZoomActualAction.getInstance()))
        }
      }
    })
    // Set the initial banner. It will be overridden by the second.
    service.notification = StatusNotificationImpl ("There is an error somewhere",
                                                   listOf(ZoomInAction.getInstance(), ZoomOutAction.getInstance()))
    invokeAndWaitIfNeeded {
      UIUtil.dispatchAllInvocationEvents()
    }
    // We should only get the content of the second banner.
    assertThat(actionPanel.components.filterIsInstance<HyperlinkLabel>().map(HyperlinkLabel::getText))
      .isEqualTo(listOf("Zoom to Fit Screen", "Zoom to Actual Size (100%)"))
    assertThat(text.text).isEqualTo("There is an error somewhere else")
  }
}
