/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.strings

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.actions.BrowserHelpAction
import com.android.tools.idea.editors.strings.action.AddKeyAction
import com.android.tools.idea.editors.strings.action.AddLocaleAction
import com.android.tools.idea.editors.strings.action.FilterKeysAction
import com.android.tools.idea.editors.strings.action.FilterLocalesAction
import com.android.tools.idea.editors.strings.action.ReloadStringResourcesAction
import com.android.tools.idea.editors.strings.action.RemoveKeysAction
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StringResourceViewPanelFakeUiTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var stringResourceViewPanel: StringResourceViewPanel
  private lateinit var fakeUi: FakeUi

  @Before
  fun setUp() {
    stringResourceViewPanel = StringResourceViewPanel(projectRule.module.androidFacet, projectRule.testRootDisposable)
    invokeAndWaitIfNeeded {
      fakeUi = FakeUi(stringResourceViewPanel.loadingPanel)
      fakeUi.root.validate()
    }
  }

  @Test
  fun toolbarConstructedProperly() {
    val toolbar: ActionToolbar = stringResourceViewPanel.loadingPanel.getDescendant { it.component.name == "toolbar" }
    assertThat(toolbar.actions).hasSize(8)
    assertThat(toolbar.actions[0]).isInstanceOf(AddKeyAction::class.java)
    assertThat(toolbar.actions[1]).isInstanceOf(RemoveKeysAction::class.java)
    assertThat(toolbar.actions[2]).isInstanceOf(AddLocaleAction::class.java)
    assertThat(toolbar.actions[3]).isInstanceOf(FilterKeysAction::class.java)
    assertThat(toolbar.actions[4]).isInstanceOf(FilterLocalesAction::class.java)
    assertThat(toolbar.actions[5]).isInstanceOf(ReloadStringResourcesAction::class.java)
    assertThat(toolbar.actions[6]).isInstanceOf(BrowserHelpAction::class.java)
    assertThat(toolbar.actions[7]).isInstanceOf(Separator::class.java)
  }
}