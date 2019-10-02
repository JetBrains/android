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

import com.android.tools.adtui.actions.createTestActionEvent
import com.android.tools.adtui.actions.prettyPrintActions
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito

class ConfigurationSetMenuActionTest : AndroidTestCase() {

  fun testActions() {
    val form = VisualizationForm(project)

    val menuAction = ConfigurationSetMenuAction(form, ConfigurationSet.PIXEL_DEVICES)
    // Call update(AnActionEvent) for updating text of menuAction.
    menuAction.update(createTestActionEvent(menuAction, dataContext = Mockito.mock<DataContext>(DataContext::class.java)))

    val actual = prettyPrintActions(menuAction)
    // The displayed text of dropdown action is the current selected option, which is Pixel Devices in this case.
    // For now there is only one option "Pixel Devices" in the drop down menu.
    val expected = "Pixel Devices\n" + // The current selection of dropdown action
                   "    Pixel Devices\n" // The options in dropdown menu have  4 spaces as indent
    assertEquals(expected, actual)

    Disposer.dispose(form)
  }
}
