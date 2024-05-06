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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.adtui.actions.SEPARATOR_TEXT
import com.android.tools.adtui.actions.createTestActionEvent
import com.android.tools.adtui.actions.prettyPrintActions
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import java.lang.StringBuilder
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito

class ConfigurationSetMenuActionTest : AndroidTestCase() {

  private lateinit var form: VisualizationForm

  override fun setUp() {
    super.setUp()
    form = VisualizationForm(project, testRootDisposable, TestVisualizationFormInitializer)
  }

  override fun tearDown() {
    Disposer.dispose(form)
    super.tearDown()
  }

  fun testActions() {
    val menuGroups = ConfigurationSetProvider.getGroupedConfigurationSets()
    val firstOption = menuGroups.firstOrNull()?.firstOrNull() ?: return
    val menuAction = ConfigurationSetMenuAction(firstOption)
    // Call update(AnActionEvent) for updating text of menuAction.
    menuAction.update(
      createTestActionEvent(menuAction, dataContext = Mockito.mock(DataContext::class.java))
    )

    val actual = prettyPrintActions(menuAction)
    // The displayed text of dropdown action is the current selected option, which is Pixel Devices
    // in this case.
    val builder = StringBuilder("${firstOption.name}\n") // The current selection of dropdown action

    // Note: The options in dropdown menu have 4 spaces as indent.
    var isPreviousGroupEmpty = true
    for (group in menuGroups) {
      if (!isPreviousGroupEmpty) {
        builder.append("    $SEPARATOR_TEXT\n")
      }
      val groupItems = group.filter { it.visible }
      groupItems.forEach {
        if (it == firstOption) {
          builder.append("    ✔ ${it.name}\n")
        } else {
          builder.append("    ${it.name}\n")
        }
      }
      isPreviousGroupEmpty = groupItems.isEmpty()
    }

    val expected = builder.toString()
    assertEquals(expected, actual)
  }
}
