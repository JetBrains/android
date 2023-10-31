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
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectBuildVariantsConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectDependenciesConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectIdeSdksLocationConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectModulesConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectSuggestionsConfigurable
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ALL_MODULES_LABEL = "<All Modules>"
private const val ALL_MODULES_MINIMIZED_LIST_LABEL = "<All Modules>"
private const val APP_LABEL = "app"
private const val APP_MINIMIZED_LIST_LABEL = "<html><body>:<B>app</B></body></html>"
private const val MY_LIBRARY_LABEL = "mylibrary"
private const val MY_LIBRARY_MINIMIZED_LIST_LABEL = "<html><body>:<B>mylibrary</B></body></html>"

@RunWith(GuiTestRemoteRunner::class)
class BasePerspectiveConfigurableTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Test
  fun modulesListIsHiddenAndRestored() {
    val psd = guiTest
        .importProjectAndWaitForProjectSyncToFinish("PsdSimple")
        .openPsd()

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
        .openPsd()

    val dependenciesConfigurable = psd.selectDependenciesConfigurable();

    assertThat(dependenciesConfigurable.isModuleSelectorMinimized()).isFalse()
    var moduleSelector = dependenciesConfigurable.findModuleSelector()
    assertThat(moduleSelector.modules())
      .containsExactly(ALL_MODULES_LABEL, APP_LABEL, MY_LIBRARY_LABEL)
    moduleSelector.selectModule(APP_LABEL)
    assertThat(moduleSelector.selectedModule()).isEqualTo(APP_LABEL)

    dependenciesConfigurable.minimizeModulesList()
    assertThat(dependenciesConfigurable.isModuleSelectorMinimized()).isTrue()
    moduleSelector = dependenciesConfigurable.findModuleSelector()
    assertThat(moduleSelector.selectedModule()).isEqualTo(APP_LABEL)
    assertThat(moduleSelector.modules()).containsExactly(ALL_MODULES_MINIMIZED_LIST_LABEL, APP_MINIMIZED_LIST_LABEL, MY_LIBRARY_MINIMIZED_LIST_LABEL)
    moduleSelector.selectModule(MY_LIBRARY_MINIMIZED_LIST_LABEL)
    assertThat(moduleSelector.selectedModule()).isEqualTo(MY_LIBRARY_LABEL)

    dependenciesConfigurable.restoreModulesList()
    assertThat(dependenciesConfigurable.isModuleSelectorMinimized()).isFalse()
    moduleSelector = dependenciesConfigurable.findModuleSelector()
    assertThat(moduleSelector.selectedModule()).isEqualTo(MY_LIBRARY_LABEL)
    assertThat(moduleSelector.modules()).containsExactly(ALL_MODULES_LABEL, APP_LABEL, MY_LIBRARY_LABEL)
    moduleSelector.selectModule(ALL_MODULES_LABEL)
    assertThat(moduleSelector.selectedModule()).isEqualTo(ALL_MODULES_LABEL)

    psd.clickCancel()
  }

  @Test
  fun viewSelectionDoesNotResetdModuleSelectorModeAndModuleSelection() {
    val psd = guiTest
      .importProjectAndWaitForProjectSyncToFinish("PsdSimple")
      .openPsd()

    psd.selectModulesConfigurable().also { modulesConfigurable ->
      assertThat(modulesConfigurable.isModuleSelectorMinimized()).isFalse()
      val moduleSelector = modulesConfigurable.findModuleSelector()
      assertThat(moduleSelector.modules()).containsExactly(APP_LABEL, MY_LIBRARY_LABEL)
      moduleSelector.selectModule(MY_LIBRARY_LABEL)
      assertThat(moduleSelector.selectedModule()).isEqualTo(MY_LIBRARY_LABEL)
    }

    psd.selectBuildVariantsConfigurable().also { buildVariantsConfigurable ->
      assertThat(buildVariantsConfigurable.isModuleSelectorMinimized()).isFalse()
      buildVariantsConfigurable.minimizeModulesList()
      assertThat(buildVariantsConfigurable.isModuleSelectorMinimized()).isTrue()
      val moduleSelector = buildVariantsConfigurable.findModuleSelector()
      assertThat(moduleSelector.selectedModule()).isEqualTo(MY_LIBRARY_LABEL)
      assertThat(moduleSelector.modules()).containsExactly(APP_MINIMIZED_LIST_LABEL, MY_LIBRARY_MINIMIZED_LIST_LABEL)
      moduleSelector.selectModule(APP_MINIMIZED_LIST_LABEL)
      assertThat(moduleSelector.selectedModule()).isEqualTo(APP_LABEL)
    }

    psd.selectDependenciesConfigurable().also { dependenciesConfigurable ->
      assertThat(dependenciesConfigurable.isModuleSelectorMinimized()).isTrue()
      dependenciesConfigurable.restoreModulesList()
      assertThat(dependenciesConfigurable.isModuleSelectorMinimized()).isFalse()
      val moduleSelector = dependenciesConfigurable.findModuleSelector()
      assertThat(moduleSelector.selectedModule()).isEqualTo(APP_LABEL)
      assertThat(moduleSelector.modules()).containsExactly(ALL_MODULES_LABEL, APP_LABEL, MY_LIBRARY_LABEL)
      moduleSelector.selectModule(ALL_MODULES_LABEL)
      assertThat(moduleSelector.selectedModule()).isEqualTo(ALL_MODULES_LABEL)
    }

    psd.selectModulesConfigurable().also { modulesConfigurable ->
      val moduleSelector = modulesConfigurable.findModuleSelector()
      assertThat(moduleSelector.selectedModule()).isEqualTo(MY_LIBRARY_LABEL)
    }

    psd.selectSuggestionsConfigurable().also { suggestionsConfigurable ->
      val moduleSelector = suggestionsConfigurable.findModuleSelector()
      assertThat(moduleSelector.modules()).containsExactly(ALL_MODULES_LABEL, APP_LABEL, MY_LIBRARY_LABEL)
      assertThat(moduleSelector.selectedModule()).isEqualTo(ALL_MODULES_LABEL)
    }

    psd.clickCancel()
  }
}
