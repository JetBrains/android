/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.newpsd

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTestRunner
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.ProjectStructureDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openFromMenu
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectDependenciesConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectIdeSdksLocationConfigurable
import org.fest.swing.timing.Pause
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRunner::class)
class BasePerspectiveConfigurableTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Before
  fun setUp() {
    StudioFlags.NEW_PSD_ENABLED.override(true)
  }

  @Test
  fun testModulesListIsHiddenAndRestored() {
    guiTest
        .importProjectAndWaitForProjectSyncToFinish("PsdSimple")
        .openFromMenu({ ProjectStructureDialogFixture.find(it) }, "File", "Project Structure...") {
          selectDependenciesConfigurable {
            requireVisibleModuleSelector()
            minimizeModulesList()
            requireMinimizedModuleSelector()
          }
          selectIdeSdksLocationConfigurable {}
          selectDependenciesConfigurable {
            requireMinimizedModuleSelector()
          }
          selectIdeSdksLocationConfigurable {}
          selectDependenciesConfigurable {
            requireMinimizedModuleSelector()
            restoreModulesList()
            requireVisibleModuleSelector()
          }
          clickCancel()
        }
  }

  @Test
  fun testModuleSelectorPreservesSelectionOnModeChanges() {
    guiTest
        .importProjectAndWaitForProjectSyncToFinish("PsdSimple")
        .openFromMenu({ ProjectStructureDialogFixture.find(it) }, "File", "Project Structure...") {
          selectDependenciesConfigurable {
            requireVisibleModuleSelector {
              requireModules("<All Modules>", "app", "mylibrary")
              selectModule("app")
            }
            minimizeModulesList()
            requireMinimizedModuleSelector {
              requireSelectedModule("app")
              requireModules("<All Modules>", "app", "mylibrary")
              selectModule("mylibrary")
            }
            restoreModulesList()
            requireVisibleModuleSelector {
              requireSelectedModule("mylibrary")
              requireModules("<All Modules>", "app", "mylibrary")
              selectModule("<All Modules>")
            }
          }
          clickCancel()
        }
  }
}
