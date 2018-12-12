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

import com.android.tools.idea.gradle.structure.configurables.MODULES_VIEW
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.android.tools.idea.tests.gui.framework.robot
import org.fest.swing.fixture.JTabbedPaneFixture
import java.awt.Container
import javax.swing.JTabbedPane

class ModulesPerspectiveConfigurableFixture(
  ideFrameFixture: IdeFrameFixture,
  container: Container
) : BasePerspectiveConfigurableFixture(ideFrameFixture, container) {

  fun selectPropertiesTab(): ModulePropertiesFixture =
    selectTab("Properties") { ModulePropertiesFixture(ideFrameFixture, it) }

  fun selectDefaultConfigTab(): ModuleDefaultConfigFixture =
    selectTab("Default Config") { ModuleDefaultConfigFixture(ideFrameFixture, it) }

  fun selectSigningConfigsTab(): SigningConfigsFixture =
    selectTab("Signing Configs") { SigningConfigsFixture(ideFrameFixture, it) }
}

fun ProjectStructureDialogFixture.selectModulesConfigurable(): ModulesPerspectiveConfigurableFixture {
  selectConfigurable("Modules")
  return ModulesPerspectiveConfigurableFixture(
      ideFrameFixture,
      findConfigurable(MODULES_VIEW))
}
