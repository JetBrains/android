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
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.ProjectStructureDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectDependenciesConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectIdeSdksLocationConfigurable
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunIn(TestGroup.UNRELIABLE)
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
  fun modulesListIsHiddenAndRestored() {
    val psd = guiTest
        .importProjectAndWaitForProjectSyncToFinish("PsdSimple")
        .openFromMenu({ ProjectStructureDialogFixture.find(it) }, arrayOf("File", "Project Structure..."))

    var dependenciesConfigurable = psd.selectDependenciesConfigurable();
    assertThat(dependenciesConfigurable.isModuleSelectorMinimized()).isFalse()
    dependenciesConfigurable.minimizeModulesList()
    assertThat(dependenciesConfigurable.isModuleSelectorMinimized()).isTrue()

    psd.selectIdeSdksLocationConfigurable()

    dependenciesConfigurable = psd.selectDependenciesConfigurable()
    assertThat(dependenciesConfigurable.isModuleSelectorMinimized()).isTrue()

    psd.selectIdeSdksLocationConfigurable()

    dependenciesConfigurable = psd.selectDependenciesConfigurable()
    assertThat(dependenciesConfigurable.isModuleSelectorMinimized()).isTrue()
    dependenciesConfigurable.restoreModulesList()
    assertThat(dependenciesConfigurable.isModuleSelectorMinimized()).isFalse()

    psd.clickCancel()

  }

  @Test
  fun moduleSelectorPreservesSelectionOnModeChanges() {
    val psd = guiTest
        .importProjectAndWaitForProjectSyncToFinish("PsdSimple")
        .openFromMenu({ ProjectStructureDialogFixture.find(it) }, arrayOf("File", "Project Structure..."))

    val dependenciesConfigurable = psd.selectDependenciesConfigurable();

    assertThat(dependenciesConfigurable.isModuleSelectorMinimized()).isFalse()
    var moduleSelector = dependenciesConfigurable.findModuleSelector()
    assertThat(moduleSelector.modules()).containsExactly("<All Modules>", "app", "mylibrary")
    moduleSelector.selectModule("app")
    assertThat(moduleSelector.selectedModule()).isEqualTo("app")

    dependenciesConfigurable.minimizeModulesList()
    assertThat(dependenciesConfigurable.isModuleSelectorMinimized()).isTrue()
    moduleSelector = dependenciesConfigurable.findModuleSelector()
    assertThat(moduleSelector.selectedModule()).isEqualTo("app")
    assertThat(moduleSelector.modules()).containsExactly("<All Modules>", "app", "mylibrary")
    moduleSelector.selectModule("mylibrary")
    assertThat(moduleSelector.selectedModule()).isEqualTo("mylibrary")

    dependenciesConfigurable.restoreModulesList()
    assertThat(dependenciesConfigurable.isModuleSelectorMinimized()).isFalse()
    moduleSelector = dependenciesConfigurable.findModuleSelector()
    assertThat(moduleSelector.selectedModule()).isEqualTo("mylibrary")
    assertThat(moduleSelector.modules()).containsExactly("<All Modules>", "app", "mylibrary")
    moduleSelector.selectModule("<All Modules>")
    assertThat(moduleSelector.selectedModule()).isEqualTo("<All Modules>")

    psd.clickCancel()
  }

}
