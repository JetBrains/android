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

import com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton
import com.android.tools.idea.ui.resourcemanager.ResourceExplorerDialog
import org.fest.swing.core.Robot

class ResourceExplorerDialogFixture private constructor(
  robot: Robot,
  dialogAndWrapper: DialogAndWrapper<ResourceExplorerDialog>
) : IdeaDialogFixture<ResourceExplorerDialog>(robot, dialogAndWrapper) {
  companion object {
    @JvmStatic
    fun find(robot: Robot): ResourceExplorerDialogFixture {
      return ResourceExplorerDialogFixture(robot, find(robot, ResourceExplorerDialog::class.java))
    }
  }

  val resourceExplorer: ResourceExplorerFixture = ResourceExplorerFixture.find(robot())

  fun clickOk() {
    findAndClickOkButton(this)
  }

}