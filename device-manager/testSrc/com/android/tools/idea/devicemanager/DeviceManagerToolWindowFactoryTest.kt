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
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceWatcher
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class DeviceManagerToolWindowFactoryTest {
  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val projectRule = ProjectRule()

  private lateinit var factory: ToolWindowFactory

  @Before
  fun initFactory() {
    factory = DeviceManagerToolWindowFactory { _, _ ->
      VirtualDevicePanel(null, Mockito.mock(Disposable::class.java), Mockito.mock(VirtualDeviceWatcher::class.java))
    }
  }

  @Test
  fun testCreateCustomTabs() {
    val failingTab = object : DeviceManagerTab {
      override fun getName() = "Failing Tab"
      override fun getPanel(project: Project, parentDisposable: Disposable) = throw Exception("it failed")
    }
    val failingTab2 = object : DeviceManagerTab {
      override fun getName() = "Failing Tab with custom error"
      override fun getPanel(project: Project, parentDisposable: Disposable) = throw Exception("it failed")
      override fun getErrorComponent(throwable: Throwable): JComponent {
        return JPanel().apply { add(JLabel("${throwable.message} custom")) }
      }
    }
    val table = JBTable()
    val successfulPanel: DevicePanel = object : DevicePanel(projectRule.project) {
      override fun newTable() = table
      override fun newDetailsPanel() = DetailsPanel("my details")
    }
    val successfulTab = object : DeviceManagerTab {
      override fun getName() = "Success Tab"
      override fun getPanel(project: Project, parentDisposable: Disposable): DevicePanel {
        return successfulPanel
      }
    }
    DeviceManagerTab.EP_NAME.point.registerExtension(failingTab, disposableRule.disposable)
    DeviceManagerTab.EP_NAME.point.registerExtension(failingTab2, disposableRule.disposable)
    DeviceManagerTab.EP_NAME.point.registerExtension(successfulTab, disposableRule.disposable)
    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(projectRule.project)

    factory.createToolWindowContent(projectRule.project, toolWindow)

    val tabs = toolWindow.contentManager.contents[0].component as JBTabbedPane
    assertEquals(5, tabs.tabCount)
    assertEquals("Virtual", (tabs.getTabComponentAt(0) as JLabel).text)
    assertTrue(tabs.getComponentAt(0) is VirtualDevicePanel)
    assertEquals("Physical", (tabs.getTabComponentAt(1) as JLabel).text)
    assertTrue(tabs.getComponentAt(1) is PhysicalDevicePanel)
    assertEquals("Failing Tab", (tabs.getTabComponentAt(2) as JLabel).text)
    assertEquals("it failed", ((tabs.getComponentAt(2) as Container).getComponent(0) as JLabel).text)
    assertEquals("Failing Tab with custom error", (tabs.getTabComponentAt(3) as JLabel).text)
    assertEquals("it failed custom", ((tabs.getComponentAt(3) as Container).getComponent(0) as JLabel).text)
    assertEquals("Success Tab", (tabs.getTabComponentAt(4) as JLabel).text)
    assertSame(successfulPanel, tabs.getComponentAt(4))
  }


  @Test
  fun testReloadErrorTab() {
    lateinit var callback: Runnable
    val table = JBTable()
    val successfulPanel: DevicePanel = object : DevicePanel(projectRule.project) {
      override fun newTable() = table
      override fun newDetailsPanel() = DetailsPanel("my details")
    }

    var shouldFail = true
    val tab = object : DeviceManagerTab {
      override fun getName() = "My Tab"
      override fun getPanel(project: Project, parentDisposable: Disposable) =
        if (shouldFail) throw Exception("it failed") else successfulPanel

      override fun setRecreateCallback(runnable: Runnable, disposable: Disposable) {
        callback = runnable
      }
    }
    DeviceManagerTab.EP_NAME.point.registerExtension(tab, disposableRule.disposable)
    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(projectRule.project)

    factory.createToolWindowContent(projectRule.project, toolWindow)

    val tabs = toolWindow.contentManager.contents[0].component as JBTabbedPane
    assertEquals("My Tab", (tabs.getTabComponentAt(2) as JLabel).text)
    assertEquals("it failed", ((tabs.getComponentAt(2) as Container).getComponent(0) as JLabel).text)

    shouldFail = false
    callback.run()

    assertEquals("My Tab", (tabs.getTabComponentAt(2) as JLabel).text)
    assertSame(successfulPanel, tabs.getComponentAt(2))
  }
}
