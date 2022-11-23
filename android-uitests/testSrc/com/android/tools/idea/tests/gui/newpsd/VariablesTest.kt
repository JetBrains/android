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
package com.android.tools.idea.tests.gui.newpsd

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.clickNo
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectVariablesConfigurable
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class VariablesTest {

  @Rule
  @JvmField
  val guiTest = PsdGuiTestRule()

  @Test
  fun addAndEditMultipleVariables() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("PsdSimple")

    ide.openPsd().run {
      selectVariablesConfigurable().run {
        clickAddSimpleValue()
        enterText("simpleVariableA")
        tab()
        enterText("stringValue")
        tab()
        chooseSimpleValue()
        enterText("simpleVariableInt")
        tab()
        enterText("123")
        tab()
        chooseSimpleValue()
        enterText("referenceVariable")
        tab()
        selectValue("\$simpleVariableInt : 123")
        selectCell(":mylibrary")
        expandWithPlus()
        down()
        editWithF2()
        chooseList()
        enterText("listVariable")
        tab()
        enterText("one")
        tab()
        enterText("two")
        tab()
        enterText("three")
        tab()
        clickAddMap()
        enterText("mapVariable")
        enter()
        enterText("k11")
        enter()
        enterText("v1")
        enter()
        enterText("k2")
        enter()
        enterText("v2")
        enter()
        selectCell("mapVariable")
        editWithF2() // Exit the edit mode.
        right()
        enter() //We should be editing 'k11' cell.
        enterText("k1")
        tab()
        selectCell("k1")
      }
      clickOk()
    }
    ide.openPsd().run {
      selectVariablesConfigurable().run {
        selectCell(":mylibrary")
        expandAllWithStar()
        selectCell("2")
        right()
        editWithF2()
        selectValue("\$simpleVariableInt : 123", withKeyboard = true)
        selectCell("k1")
        editWithF2()  // Cancel editing.
        right()
        editWithF2()
        selectValue("\$simpleVariableA : stringValue")
      }
      clickOk()
    }
    ide.openPsd().run {
      selectVariablesConfigurable().run {
        selectCell(":mylibrary")
        expandAllWithStar()
        assertThat(contents()).containsExactly(
          "PsdSimple (build script)" to "",
          "PsdSimple (project)" to "",
          "simpleVariableA" to "stringValue",
          "simpleVariableInt" to "123",
          "referenceVariable" to "\$simpleVariableInt : 123",
          "" to "", // +New Variable
          ":app" to "",
          ":mylibrary" to "",
          "listVariable" to "",
          "0" to "one",
          "1" to "two",
          "2" to "\$simpleVariableInt : 123",
          "" to "", // +New Map Entry
          "mapVariable" to "",
          "k1" to "\$simpleVariableA : stringValue",
          "k2" to "v2",
          "" to "", // +New Item
          "" to "" // +New Variable
        )
      }
      clickCancel()
    }
  }

  @Test
  fun removingVariables() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("PsdSimple")

    ide.openPsd().run {
      selectVariablesConfigurable().run {
        clickAddSimpleValue()
        enterText("simpleVariable")
        tab()
        enterText("stringValue")
        tab()
        chooseList()
        enterText("listVariable")
        tab()
        enterText("one")
        tab()
        enterText("two")
        tab()
        clickAddMap()
        enterText("mapVariable")
        enter()
        enterText("k1")
        enter()
        enterText("v1")
        enter()
      }
      clickOk()
    }
    ide.openPsd().run {
      selectVariablesConfigurable().run {
        selectCell("simpleVariable")
        clickRemove().run {
          requireMessageContains("Remove variable 'simpleVariable' from the build script of project 'PsdSimple'?")
          clickNo()
        }
        selectCell("listVariable")
        editWithF2() // Cancel editing.
        expandWithPlus()
        selectCell("two")
        clickRemove().run {
          requireMessageContains("Remove list item 1 from 'listVariable'?")
          clickNo()
        }
        selectCell("mapVariable")
        editWithF2() // Cancel editing.
        expandWithPlus()
        selectCell("k1")
        clickRemove().run {
          requireMessageContains("Remove map entry 'k1' from 'mapVariable'?")
          clickNo()
        }
        selectCell("simpleVariable")
        selectCellWithCtrl("mapVariable")
        clickRemove(removesMultiple = true).run {
          requireMessageContains("Remove 2 items from project 'PsdSimple'?")
          clickYes()
        }
        // Assert the current state to make sure that deleting does not collapse nodes.
        assertThat(contents()).containsExactly(
          "PsdSimple (build script)" to "",
          "PsdSimple (project)" to "",
          "listVariable" to "",
          "0" to "one",
          "1" to "two",
          "" to "", // +New Map Entry
          "" to "", // +New Variable
          ":app" to "",
          ":mylibrary" to ""
        )
      }
      clickOk()
    }
    ide.openPsd().run {
      selectVariablesConfigurable().run {
        assertThat(contents()).containsExactly(
          "PsdSimple (build script)" to "",
          "PsdSimple (project)" to "",
          "listVariable" to "",
          "0" to "one",
          "1" to "two",
          "" to "", // +New Map Entry
          "" to "", // +New Variable
          ":app" to "",
          ":mylibrary" to ""
        )
      }
      clickCancel()
    }
  }

  @Test
  fun renamingVariables() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("PsdSimple")

    ide.openPsd().run {
      selectVariablesConfigurable().run {
        clickAddSimpleValue()
        enterText("simpleVariable")
        tab()
        enterText("stringValue")
        tab()
        chooseList()
        enterText("listVariable")
        tab()
        enterText("one")
        tab()
        enterText("two")
        tab()
        selectCell("simpleVariable")
        // Assert current state to make sure renaming does not collapse nodes.
        enterText("aVariable")
        tab()
        assertThat(contents()).containsExactly(
          "PsdSimple (build script)" to "",
          "aVariable" to "stringValue",
          "listVariable" to "",
          "0" to "one",
          "1" to "two",
          "" to "", // +New Map Entry
          "" to "", // +New Variable
          "PsdSimple (project)" to "",
          ":app" to "",
          ":mylibrary" to ""
        )
      }
      clickOk()
    }
    ide.openPsd().run {
      selectVariablesConfigurable().run {
        assertThat(contents()).containsExactly(
          "PsdSimple (build script)" to "",
          "aVariable" to "stringValue",
          "listVariable" to "[one, two]",
          "" to "", // +New Variable
          "PsdSimple (project)" to "",
          ":app" to "",
          ":mylibrary" to ""
        )
      }
      clickCancel()
    }
  }
}
