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
package com.android.tools.idea.tests.gui.framework.fixture.inspector

import com.android.tools.idea.appinspection.ide.ui.SelectProcessAction
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_TOOL_WINDOW_ID
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.tree.COMPONENT_TREE_NAME
import com.android.tools.idea.layoutinspector.ui.DEVICE_VIEW_ACTION_TOOLBAR_NAME
import com.android.tools.idea.layoutinspector.ui.LayerSpacingSliderAction
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture
import com.android.tools.idea.tests.gui.framework.fixture.ComponentTreeFixture
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture
import com.android.tools.idea.tests.gui.framework.fixture.properties.PropertiesPanelFixture
import com.android.tools.idea.tests.gui.framework.fixture.properties.SelectedComponentPanelFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.android.tools.idea.tests.gui.framework.waitUntilFound
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.runInEdtAndWait
import org.fest.swing.annotation.RunsInEDT
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JMenuItemFixture
import org.fest.swing.fixture.JPopupMenuFixture
import org.fest.swing.fixture.JSliderFixture
import org.fest.swing.timing.Wait
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSlider
import javax.swing.JTable
import javax.swing.MenuElement

/**
 * Fixture for the dynamic layout inspector tool window.
 */
class LayoutInspectorFixture(
  project: Project,
  private val robot: Robot
) : ToolWindowFixture(LAYOUT_INSPECTOR_TOOL_WINDOW_ID, project, robot) {

  /**
   * Lazily get the component tree of the layout inspector.
   */
  val tree: ComponentTreeFixture by lazy(LazyThreadSafetyMode.NONE) {
    val content = getContent("")
    val table: JTable = GuiTests.waitUntilFound(myRobot, content!!.component, Matchers.byName(JTable::class.java, COMPONENT_TREE_NAME))
    ComponentTreeFixture(robot, table)
  }

  /**
   * Lazily get the properties panel of the layout inspector.
   */
  val properties: PropertiesPanelFixture<InspectorPropertyItem> by lazy(LazyThreadSafetyMode.NONE) {
    val content = getContent("")
    PropertiesPanelFixture.findPropertiesPanelInContainer<InspectorPropertyItem>(content!!.component, robot)
  }

  /**
   * Lazily get the device view panel with the view content and view controls of the layout inspector.
   */
  val deviceView: DeviceViewPanelFixture by lazy(LazyThreadSafetyMode.NONE) {
    val content = getContent("")
    DeviceViewPanelFixture.findDeviceViewPanelInContainer(content!!.component, robot)
  }

  /**
   * Select a device and process from the select process action button in the toolbar of the layout inspector.
   */
  fun selectDevice(device: String, process: String) {
    val button = selectProcessActionButton
    runInEdtAndWait { button.target().click() }
    val popup = JPopupMenuFixture(robot, robot.findActivePopupMenu()!!)
    val deviceMenu = findSubItemInActivePopup(device)
    runInEdtAndWait { deviceMenu.target().doClick() }
    val processMenu =  findSubItemOfMenu(deviceMenu.target(), process)
    runInEdtAndWait { processMenu.target().doClick() }
    runInEdtAndWait { popup.target().isVisible = false }
  }

  /**
   * Select the "Stop Inspector" menu item from the select process action button in the toolbar of the layout inspector.
   */
  fun stop() {
    // For some reason this line avoids the popup of tooltip for the action button
    robot().focusAndWaitForFocusGain(toolbar)

    // Update the popup menu and click on the action button.
    robot().focusAndWaitForFocusGain(selectProcessActionButton.target())
    selectProcessActionButton.click()

    findSubItemInActivePopup("Stop Inspector").click()
  }

  /**
   * Wait until the component tree model have the specified number of components.
   *
   * The specified number does NOT include the invisible root node.
   */
  fun waitForComponentTreeToLoad(components: Int) {
    Wait.seconds(10)
      .expecting("component tree to load $components components")
      .until { tree.modelRowCount == components + 1 }
    robot().waitForIdle()
  }

  /**
   * Wait until the properties panel is posulated with the properties of the specified view id.
   */
  fun waitForPropertiesToPopulate(id: String) {
    Wait.seconds(10)
      .expecting("properties panel to populate for $id currently shown: ${getCurrentIdInPropertyPanel()}")
      .until { getCurrentIdInPropertyPanel() == id }
    robot().waitForIdle()
  }

  private fun getCurrentIdInPropertyPanel(): String? =
    (properties.findHeader()?.components?.firstOrNull() as? SelectedComponentPanelFixture)?.id

  private fun findSubItemInActivePopup(subTitle: String): JMenuItemFixture =
    JMenuItemFixture(robot(), waitUntilFound("the menu \"$subTitle\" to show up", { findSubItem(robot().findActivePopupMenu(), subTitle) }))

  private fun findSubItemOfMenu(element: JMenuItem?, subTitle: String): JMenuItemFixture =
    JMenuItemFixture(robot(), waitUntilFound("the menu \"$subTitle\" to show up", {
      element?.let { findSubItem(findPopupOfMenuItem(it), subTitle) }
    }, 10L))

  @RunsInEDT
  private fun findSubItem(element: MenuElement?, subTitle: String): JMenuItem? =
    element?.subElements?.filterIsInstance(JMenuItem::class.java)?.firstOrNull { it.text == subTitle && it.isShowing && it.isEnabled }

  @RunsInEDT
  private fun findPopupOfMenuItem(menuItem: JMenuItem): MenuElement? =
    menuItem.subElements.firstOrNull { it is JPopupMenu }

  private val selectProcessActionButton: ActionButtonFixture by lazy(LazyThreadSafetyMode.NONE) {
    ActionButtonFixture.findByActionClass(SelectProcessAction::class.java, myRobot, toolbar)
  }

  val layerSpacingSlider: JSliderFixture by lazy(LazyThreadSafetyMode.NONE) {
    findSliderByAction(LayerSpacingSliderAction)
  }

  fun waitUntilMode3dSliderEnabled(enabled: Boolean) {
    Wait.seconds(10).expecting("slider to be enabled: $enabled").until { layerSpacingSlider.isEnabled == enabled }
  }

  private fun findSliderByAction(action: AnAction): JSliderFixture {
    val actionMatcher = object : GenericTypeMatcher<JSlider>(JSlider::class.java) {
      override fun isMatching(slider: JSlider): Boolean {
        val panel = slider.parent as JPanel
        return panel.getClientProperty(CustomComponentAction.ACTION_KEY) == action
      }
    }
    val slider = GuiTests.waitUntilFound(myRobot, toolbar, Matchers.byType(JSlider::class.java).and(actionMatcher))
    return JSliderFixture(robot(), slider)
  }

  private val toolbar: JComponent by lazy(LazyThreadSafetyMode.NONE) {
    val content = getContent("")
    GuiTests.waitUntilFound(myRobot, content!!.component, Matchers.byName(JComponent::class.java, DEVICE_VIEW_ACTION_TOOLBAR_NAME))
  }
}
