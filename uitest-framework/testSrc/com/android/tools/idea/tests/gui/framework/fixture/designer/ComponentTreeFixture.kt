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
package com.android.tools.idea.tests.gui.framework.fixture.designer

import com.android.tools.componenttree.impl.TreeImpl
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JTreeFixture
import javax.swing.JTree

/*
 * A fixture wrapping the component tree in a visual designer
 * The generic parameter T represents the type of the underlying object held in the tree node
 */
class ComponentTreeFixture<T>(robot: Robot, tree: JTree) : JTreeFixture(robot, tree) {
  /*
   * Returns the objects associated with the selected nodes
   */
  fun selectedComponents(): List<T> {
    @Suppress("UNCHECKED_CAST")
    return target().selectionPaths?.map { it.lastPathComponent as T } ?: listOf()
  }

  companion object {
    /*
     * Get the fixture for the specified type and name
     */
    @JvmStatic
    fun <T> create(name: String, robot: Robot): ComponentTreeFixture<T> {
      val result = GuiTests.waitUntilShowing(robot, Matchers.byName(TreeImpl::class.java, name))
      return ComponentTreeFixture(robot, result)
    }
  }
}