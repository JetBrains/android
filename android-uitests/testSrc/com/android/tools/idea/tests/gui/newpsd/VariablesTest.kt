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
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectVariablesConfigurable
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRemoteRunner::class)
class VariablesTest {

  @Rule
  @JvmField
  val guiTest = PsdGuiTestRule()

  @Before
  fun setUp() {
    StudioFlags.NEW_PSD_ENABLED.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.NEW_PSD_ENABLED.clearOverride()
  }

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
        selectCell("mylibrary")
        right() // expand node.
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
        enterText("mapVaribale")
        enter()
        enterText("k1")
        enter()
        enterText("v1")
        enter()
        enterText("k2")
        enter()
        enterText("v2")
        enter()
      }
      clickOk()
    }
    ide.openPsd().run {
      selectVariablesConfigurable().run {
        selectCell("mylibrary")
        expandAllWithStar()
        selectCell("2")
        right()
        editWithF2()
        selectValue("\$simpleVariableInt : 123")
        selectCell("k1")
        right()
        editWithF2()
        selectValue("\$simpleVariableA : stringValue")
      }
      clickOk()
    }
    ide.openPsd().run {
      selectVariablesConfigurable().run {
        selectCell("mylibrary")
        expandAllWithStar()
        assertThat(contents()).containsExactly(
          "PsdSimple" to "",
          "simpleVariableA" to "stringValue",
          "simpleVariableInt" to "123",
          "referenceVariable" to "\$simpleVariableInt : 123",
          "" to "", // +New Variable
          "app" to "",
          "mylibrary" to "",
          "listVariable" to "",
          "0" to "one",
          "1" to "two",
          "2" to "\$simpleVariableInt : 123",
          "" to "", // +New Map Entry
          "mapVaribale" to "",
          "k1" to "\$simpleVariableA : stringValue",
          "k2" to "v2",
          "" to "", // +New Item
          "" to "" // +New Variable
        )
      }
      clickCancel()
    }
  }
}
