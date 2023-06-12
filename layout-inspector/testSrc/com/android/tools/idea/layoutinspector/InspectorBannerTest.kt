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
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.testing.ui.flatten
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable

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
    bannerService.addNotification("There is an error somewhere", emptyList())
    invokeAndWaitIfNeeded {
      UIUtil.dispatchAllInvocationEvents()
    }
    assertThat(banner.isVisible).isTrue()
  }

  @Test
  fun testInvisibleAfterEmptyStatus() {
    val banner = InspectorBanner(projectRule.project)
    val bannerService = InspectorBannerService.getInstance(projectRule.project) ?: error("no banner")
    bannerService.addNotification("There is an error somewhere", emptyList())
    bannerService.clear()
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
    bannerService.addNotification("There is an error somewhere", listOf(action))
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
    val table = banner.flatten(false).single { it is JTable } as JTable
    val cellRenderer = table.getCellRenderer(0, 0)
    var addedSecond = false
    // Add a listener that will change the banner in the middle of when it's being changed initially
    table.model.addTableModelListener {
      if (!addedSecond) {
        addedSecond = true
        service.addNotification("There is another error somewhere else",
                                listOf(ZoomToFitAction.getInstance(), ZoomActualAction.getInstance()))
      }
    }
    // Set the initial banner. It will be overridden by the second.
    service.addNotification("There is an error somewhere",
                            listOf(ZoomInAction.getInstance(), ZoomOutAction.getInstance()))
    invokeAndWaitIfNeeded {
      UIUtil.dispatchAllInvocationEvents()
    }
    // We now have 2 messages in the banner
    assertThat(table.model.rowCount).isEqualTo(2)
    val text1 = cellRenderer.getTableCellRendererComponent(table, null, false, false, 0, 0) as JLabel
    val actions1 = cellRenderer.getTableCellRendererComponent(table, null, false, false, 0, 1) as JPanel
    assertThat(actions1.components.filterIsInstance<HyperlinkLabel>().map(HyperlinkLabel::getText))
      .isEqualTo(listOf("Zoom In", "Zoom Out"))
    assertThat(text1.text).isEqualTo("There is an error somewhere")
    val text2 = cellRenderer.getTableCellRendererComponent(table, null, false, false, 1, 0) as JLabel
    val actions2 = cellRenderer.getTableCellRendererComponent(table, null, false, false, 1, 1) as JPanel
    assertThat(text2.text).isEqualTo("There is another error somewhere else")
    assertThat(actions2.components.filterIsInstance<HyperlinkLabel>().map(HyperlinkLabel::getText))
      .isEqualTo(listOf("Zoom to Fit Screen", "100%"))
  }

  @Test
  fun testMouseHandling() {
    val banner = InspectorBanner(projectRule.project)
    val service = InspectorBannerService.getInstance(projectRule.project) ?: error("no banner")
    val table = banner.flatten(false).single { it is JTable } as JTable
    service.addNotification("First error")
    service.addNotification("Second error")
    service.addNotification("Third error")
    banner.setSize(800, 400)
    val ui = FakeUi(banner, createFakeWindow = true)

    // Move mouse over message of 1st error
    val rect1 = table.getCellRect(0, 0, false)
    ui.mouse.moveTo(rect1.midPoint())
    assertThat(table.cursor).isSameAs(Cursor.getDefaultCursor())

    // Move mouse over actions of 1st error
    val rect2 = table.getCellRect(0, 1, false)
    ui.mouse.moveTo(rect2.midPoint())
    assertThat(table.cursor).isSameAs(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))

    // Click on the actions of the 2nd error, should remove that error from the banner service:
    val rect3 = table.getCellRect(1, 1, false)
    ui.mouse.click(rect3.midPoint().x, rect3.midPoint().y)
    assertThat(service.notifications).hasSize(2)
    assertThat(service.notifications.first().message).isEqualTo("First error")
    assertThat(service.notifications.last().message).isEqualTo("Third error")
  }

  private fun Rectangle.midPoint(): Point {
    return Point(x + width / 2, y + height / 2)
  }
}
