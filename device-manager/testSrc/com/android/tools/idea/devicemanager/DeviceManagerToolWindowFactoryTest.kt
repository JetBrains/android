/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevicePanel
import com.android.tools.idea.devicemanager.virtualtab.VirtualDevicePanel
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import org.junit.Rule
import org.junit.Test
import java.awt.Container
import javax.swing.JLabel

class DeviceManagerToolWindowFactoryTest {
  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun testCreateCustomTabs() {
    val failingTab = object: DeviceManagerTab {
      override fun getName() = "Failing Tab"
      override fun getPanel(project: Project, parentDisposable: Disposable) = throw Exception("it failed")
    }
    val table = JBTable()
    val successfulPanel = object : DevicePanel(projectRule.project) {
      override fun newTable() = table
      override fun newDetailsPanel() = DetailsPanel("my details")
    }
    val successfulTab = object: DeviceManagerTab {
      override fun getName() = "Success Tab"
      override fun getPanel(project: Project, parentDisposable: Disposable): DevicePanel {
        return successfulPanel
      }
    }
    DeviceManagerTab.EP_NAME.point.registerExtension(failingTab, disposableRule.disposable)
    DeviceManagerTab.EP_NAME.point.registerExtension(successfulTab, disposableRule.disposable)
    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(projectRule.project)

    DeviceManagerToolWindowFactory().createToolWindowContent(projectRule.project, toolWindow)

    val tabs = toolWindow.contentManager.contents[0].component as JBTabbedPane
    assertThat(tabs.tabCount).isEqualTo(4)
    assertThat((tabs.getTabComponentAt(0) as JLabel).text).isEqualTo("Virtual")
    assertThat(tabs.getComponentAt(0)).isInstanceOf(VirtualDevicePanel::class.java)
    assertThat((tabs.getTabComponentAt(1) as JLabel).text).isEqualTo("Physical")
    assertThat(tabs.getComponentAt(1)).isInstanceOf(PhysicalDevicePanel::class.java)
    assertThat((tabs.getTabComponentAt(2) as JLabel).text).isEqualTo("Failing Tab")
    assertThat(((tabs.getComponentAt(2) as Container).getComponent(0) as JLabel).text).isEqualTo("it failed")
    assertThat((tabs.getTabComponentAt(3) as JLabel).text).isEqualTo("Success Tab")
    assertThat(tabs.getComponentAt(3)).isSameAs(successfulPanel)
  }
}
