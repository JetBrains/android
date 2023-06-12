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
package com.android.tools.idea.tests.gui.naveditor

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class NavComponentTreeTest {
  @get:Rule
  val guiTest = GuiTestRule()

  @Test
  fun testSelectComponent() {
    val frame = guiTest.importProjectAndWaitForProjectSyncToFinish("Navigation")
    // Open file as XML and switch to design tab, wait for successful render
    val navEditor = frame
      .editor
      .open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN)
      .layoutEditor
      .waitForRenderToFinish()

    val componentTree = navEditor.navComponentTree()
    componentTree.selectRow(2)

    val selectedInTree = componentTree.selectedElements<NlComponent>()
    assertEquals(1, selectedInTree.size)
    val component = selectedInTree[0]
    assertEquals("first_screen", component.id)

    val selected = navEditor.selection
    assertThat(selected).containsExactly(component)
  }
}