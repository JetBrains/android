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
import com.intellij.icons.AllIcons
import com.intellij.ui.SearchTextField
import org.fest.swing.core.Robot
import org.fest.swing.core.TypeMatcher
import org.fest.swing.exception.LocationUnavailableException
import org.fest.swing.fixture.JListFixture
import org.fest.swing.fixture.JPanelFixture
import org.fest.swing.fixture.JTabbedPaneFixture
import org.fest.swing.fixture.JTextComponentFixture
import java.util.regex.Pattern
import javax.swing.JPanel
import javax.swing.JTabbedPane

class ResourceExplorerFixture private constructor(robot: Robot, target: JPanel) : JPanelFixture(robot, target) {

  companion object {
    @JvmStatic
    fun find(robot: Robot): ResourceExplorerFixture {
      val explorer = GuiTests.waitUntilShowing(robot, Matchers.byType(ResourceExplorer::class.java))
      return ResourceExplorerFixture(robot, explorer)
    }
  }

  val searchField: JTextComponentFixture
    get() {
      val component = robot().finder().find(target(), TypeMatcher(SearchTextField::class.java)) as SearchTextField
      return JTextComponentFixture(robot(), component.textEditor)
    }

  fun clickAddButton(): ResourceExplorerFixture {
    ActionButtonFixture.findByIcon(AllIcons.General.Add, robot(), target()).click()
    return this
  }

  fun selectTab(resourceTypeName: String) {
    val component = robot().finder().find(target(), TypeMatcher(JTabbedPane::class.java)) as JTabbedPane
    JTabbedPaneFixture(robot(), component).selectTab(resourceTypeName)
  }

  fun selectResource(resourceName: String) {
    @Suppress("UNCHECKED_CAST")
    val listViews = robot().finder().findAll(target(), TypeMatcher(AssetListView::class.java)) as Collection<AssetListView>
    listViews.forEach {
      try {
        JListFixture(robot(), it).selectItem(Pattern.compile(".*(name=${resourceName},).*"))
        return
      }
      catch (e: LocationUnavailableException) {
        // Do nothing.
      }
    }
    throw Exception("Could not find resource: ${resourceName}")
  }
}
