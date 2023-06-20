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
package com.android.tools.idea.tests.gui.framework.fixture

import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.android.tools.idea.ui.resourcemanager.ResourceExplorer
import com.android.tools.idea.ui.resourcemanager.explorer.AssetListView
import com.android.tools.idea.ui.resourcemanager.widget.OverflowingTabbedPaneWrapper
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.labels.LinkLabel
import org.fest.swing.core.Robot
import org.fest.swing.core.TypeMatcher
import org.fest.swing.exception.LocationUnavailableException
import org.fest.swing.fixture.JListFixture
import org.fest.swing.fixture.JListItemFixture
import org.fest.swing.fixture.JPanelFixture
import org.fest.swing.fixture.JTabbedPaneFixture
import org.fest.swing.fixture.JTextComponentFixture
import org.fest.swing.timing.Wait
import java.util.regex.Pattern
import javax.swing.JPanel
import javax.swing.JTabbedPane

/**
 * Test Fixture for the ResourceExplorer. The main panel of the Resource Manager and the Resource Picker.
 */
class ResourceExplorerFixture private constructor(robot: Robot, target: JPanel) : JPanelFixture(robot, target) {

  companion object {
    @JvmStatic
    fun find(robot: Robot): ResourceExplorerFixture {
      val explorer = GuiTests.waitUntilShowing(robot, Matchers.byType(ResourceExplorer::class.java))
      return ResourceExplorerFixture(robot, explorer)
    }
  }

  /**
   * The number of resources visible in the explorer.
   */
  @Suppress("UNCHECKED_CAST")
  val resourcesCount: Int
    get() {
      val listViews = robot().finder().findAll(target(), TypeMatcher(AssetListView::class.java)) as Collection<AssetListView>
      return listViews.map { it.model.size }.reduce(Int::plus)
    }

  /**
   * Returns the search field Fixture in the Resource Explorer. Used to filter resources by name.
   */
  val searchField: JTextComponentFixture
    get() {
      val component = robot().finder().find(target(), TypeMatcher(SearchTextField::class.java)) as SearchTextField
      return JTextComponentFixture(robot(), component.textEditor)
    }

  /**
   * Clicks the add button in the Resource Explorer. Displays a popup-menu with options to create/add resources. Its contents depends on the
   * currently selected tab.
   */
  fun clickAddButton(): ResourceExplorerFixture {
    ActionButtonFixture.findByIcon(AllIcons.General.Add, robot(), target()).click()
    return this
  }

  /**
   * Selects the Resource Type tab by the given name. If the desired Resource Type is not visible in the tabs, it will also look for it in
   * the pop-up menu from the overflow button.
   *
   * @param resourceTypeDisplayName The resource type tab name. Has to match the displayed name exactly.
   */
  fun selectTab(resourceTypeDisplayName: String): ResourceExplorerFixture {
    val tabsWrapper = findByType<OverflowingTabbedPaneWrapper>(OverflowingTabbedPaneWrapper::class.java)
    val tabsPanel = finder().find(tabsWrapper, TypeMatcher(JTabbedPane::class.java)) as JTabbedPane
    val tabIsVisible = (0..tabsPanel.tabCount).any { index ->
      // Check if the desired tab is visible.
      ((tabsPanel.getBoundsAt(index))?.let { it.width > 0 } ?: false) && tabsPanel.getTitleAt(index) == resourceTypeDisplayName
    }
    if (tabIsVisible) {
      JTabbedPaneFixture(robot(), tabsPanel).selectTab(resourceTypeDisplayName)
    }
    else {
      // If the desired tab is not visible, click the overflow button and look it over in the pop-up menu.
      val overflowButton = finder().findByType(tabsWrapper, ActionButton::class.java)
      robot().click(overflowButton)
      IdeFrameFixture.find(robot()).invokeMenuPath(resourceTypeDisplayName)
    }
    return this
  }

  /**
   * Selects (by simulating a single click) the desired resource in the list of visible resources. Has to match the exact resource name.
   *
   * It's recommended to filter the resources by name using the [searchField], to make sure the desired resource will be visible.
   */
  fun selectResource(resourceName: String): ResourceExplorerFixture {
    findResource(resourceName).select()
    return this
  }

  /**
   * Drags the desired resource in to the Layout Editor's Design Surface.
   *
   * Note that the Layout Editor should be open with the Design view visible before calling this method.
   */
  fun dragResourceToLayoutEditor(resourceName: String): ResourceExplorerFixture {
    val layoutEditor = IdeFrameFixture.find(robot()).editor.getLayoutEditor()
    val surface = layoutEditor.surface
    val componentsCountBeforeDrop = surface.scene.sceneComponents.count()
    val rootComponent = surface.scene.sceneComponents.first()

    findResource(resourceName).drag()
    surface.drop(rootComponent.midPoint) // Drop in the middle of the surface.
    layoutEditor.waitForRenderToFinish()
    Wait.seconds(10L).expecting("Dragged resource in Layout Editor").until {
      surface.scene.sceneComponents.count() == componentsCountBeforeDrop + 1
    }
    return this
  }

  /**
   * Drags the desired resource in to the Text Editor, in the caret position defined by the [before] and [after] Strings.
   *
   * @see EditorFixture.moveBetween
   */
  fun dragResourceToXmlEditor(resourceName: String, before: String, after: String): ResourceExplorerFixture {
    findResource(resourceName).drag()

    IdeFrameFixture.find(robot()).editor.moveBetween(before, after)
    robot().releaseMouseButtons()
    return this
  }

  /**
   * Selects assert by name in the resource manager
   *
   */
  fun findResource(resourceName: String): JListItemFixture {
    @Suppress("UNCHECKED_CAST")
    val listViews = robot().finder().findAll(target(), TypeMatcher(AssetListView::class.java)) as Collection<AssetListView>
    listViews.forEach {
      try {
        return JListFixture(robot(), it).item(Pattern.compile(".*(name=${resourceName},).*"))
      }
      catch (e: LocationUnavailableException) {
        // Do nothing.
      }
    }
    throw Exception("Could not find resource: ${resourceName}")
  }


  fun findLinklabelByTextContains(text: String) : LinkLabelFixture{
    val listLinkLabels = robot().finder().findAll(target(), Matchers.byType(LinkLabel::class.java)) as Collection<LinkLabel<*>>
    listLinkLabels.forEach{
      if(it.text !== null && it.text.contains(text)){
        return LinkLabelFixture(robot(), it)
      }
    }
    throw Exception("Unable to find link with  $text")
  }
}
