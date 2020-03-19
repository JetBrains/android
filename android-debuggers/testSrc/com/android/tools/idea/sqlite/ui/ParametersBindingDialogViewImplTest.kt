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

import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.sqlite.controllers.SqliteParameter
import com.android.tools.idea.sqlite.controllers.SqliteParameterValue
import com.android.tools.idea.sqlite.ui.parametersBinding.ParametersBindingDialogView
import com.android.tools.idea.sqlite.ui.parametersBinding.ParametersBindingDialogViewImpl
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.awt.Dimension

class ParametersBindingDialogViewImplTest : LightPlatformTestCase() {
  private lateinit var view: ParametersBindingDialogViewImpl

  override fun setUp() {
    super.setUp()
    view = ParametersBindingDialogViewImpl(project, true)
    view.component.size = Dimension(600, 200)
  }

  fun testUserCanSetValueToNull() {
    // Prepare
    val mockListener = mock(ParametersBindingDialogView.Listener::class.java)
    view.addListener(mockListener)
    view.showNamedParameters(setOf(SqliteParameter("p1"), SqliteParameter("p2")))

    val checkBoxes = TreeWalker(view.component).descendants().filterIsInstance<JBCheckBox>().filter { it.name == "null-check-box" }
    val textFields = TreeWalker(view.component).descendants().filterIsInstance<JBTextField>().filter { it.name == "value-text-field" }

    // Act
    checkBoxes[0].isSelected = true
    textFields[1].text = "null"
    view.doOKAction()

    // Assert
    verify(mockListener).bindingCompletedInvoked(mapOf(
      Pair(SqliteParameter("p1"), SqliteParameterValue.fromAny(null)),
      Pair(SqliteParameter("p2"), SqliteParameterValue.fromAny("null"))
    ))
  }
}