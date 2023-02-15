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
package com.android.tools.idea.sqlite.ui

import com.android.tools.idea.sqlite.ui.parametersBinding.ParametersBindingDialogViewImpl
import com.intellij.testFramework.LightPlatformTestCase
import java.awt.Dimension

class ParametersBindingDialogViewImplTest : LightPlatformTestCase() {
  private lateinit var view: ParametersBindingDialogViewImpl

  override fun setUp() {
    super.setUp()
    view = ParametersBindingDialogViewImpl("SELECT * FROM tab", project, true)
    view.component.size = Dimension(600, 200)
  }

  override fun tearDown() {
    view.doOKAction()
    super.tearDown()
  }

  fun testUserCanSetValueToNull() {
    // TODO(b/151922426)
  }

  fun testCanAssignListToCollectionParameters() {
    // TODO(b/151922426)
  }

  fun testCanRemoveAdditionalValues() {
    // TODO(b/151922426)
  }

  fun testAddAdditionalValueIsDisabledForNonCollectionParameters() {
    // TODO(b/151922426)
  }

  fun testKeyboardShortcutSetsToNull() {
    // TODO(b/151922426)
  }

  fun testKeyboardShortcutAddsValue() {
    // TODO(b/151922426)
  }

  fun testKeyboardShortcutRemovesValue() {
    // TODO(b/151922426)
  }
}
