/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import org.fest.swing.core.Robot
import org.fest.swing.fixture.JListFixture
import java.awt.Container

open class ProductFlavorsFixture constructor(
  val robot: Robot,
  val container: Container
) : ConfigPanelFixture() {

  override fun target(): Container = container
  override fun robot(): Robot= robot

  fun clickAddFlavorDimension(): InputNameDialogFixture {
    clickToolButton("Add")
    val listFixture = JListFixture(robot(), getList())
    listFixture.dragAndClickItem("Add Flavor Dimension")
    return InputNameDialogFixture.find(robot, "Create New Flavor Dimension") {
      Thread.sleep(500) // MasterDetailsComponent has up to 500ms delay before acting on selection change.
      waitForIdle()
    }
  }

  fun clickAddProductFlavor(): InputNameDialogFixture {
    clickToolButton("Add")
    val listFixture = JListFixture(robot(), getList())
    listFixture.dragAndClickItem("Add Product Flavor")
    return InputNameDialogFixture.find(robot, "Create New Product Flavor") {
      Thread.sleep(500) // MasterDetailsComponent has up to 500ms delay before acting on selection change.
      waitForIdle()
    }
  }

  fun minSdkVersion(): PropertyEditorFixture = findEditor("Min SDK Version")
  fun targetSdkVersion(): PropertyEditorFixture = findEditor("Target SDK Version")
  fun versionCode(): PropertyEditorFixture = findEditor("Version Code")
  fun versionName(): PropertyEditorFixture = findEditor("Version Name")
}