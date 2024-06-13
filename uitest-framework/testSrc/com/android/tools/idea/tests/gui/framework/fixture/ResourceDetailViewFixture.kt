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
package com.android.tools.idea.tests.gui.framework.fixture

import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceDetailView
import com.android.tools.idea.ui.resourcemanager.widget.SingleAssetCard
import com.intellij.openapi.actionSystem.impl.ActionButton
import org.fest.swing.core.Robot
import org.fest.swing.core.TypeMatcher
import org.fest.swing.fixture.JPanelFixture
import javax.swing.JPanel

/**
 * Test Fixture for the ResourceExplorerDetail view.
 */
class ResourceDetailViewFixture private constructor(robot: Robot, target: JPanel) : JPanelFixture(robot, target) {

  companion object {
    @JvmStatic
    fun find(robot: Robot): ResourceDetailViewFixture {
      val explorer = GuiTests.waitUntilShowing(robot, Matchers.byType(ResourceDetailView::class.java))
      return ResourceDetailViewFixture(robot, explorer)
    }
  }

  /**
   * The number of resources visible in the explorer.
   */
  @Suppress("UNCHECKED_CAST")
  val resourcesCount: Int
    get() {
      val listViews = robot().finder().findAll(target(), TypeMatcher(SingleAssetCard::class.java)) as Collection<SingleAssetCard>
      return listViews.size
    }

  /**
   * Return back (<-) button in resource detail panel
   */
  fun goBackToResourceList(): ActionButtonFixture{
    // Verify 4 : A drawable named icon_category_entertainment should be showing
    val button = robot().finder().find<ActionButton>(
      target(), Matchers.byType(
        ActionButton::class.java
      )
    )
    return ActionButtonFixture(robot(), button)
  }
}
