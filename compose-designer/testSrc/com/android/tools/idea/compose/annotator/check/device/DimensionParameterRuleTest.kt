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
package com.android.tools.idea.compose.annotator.check.device

import com.android.tools.idea.compose.preview.pickers.properties.DimUnit
import com.intellij.openapi.actionSystem.DataProvider
import kotlin.test.assertFails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class DimensionParameterRuleTest {

  lateinit var dataProvider: DataProvider

  @Before
  fun setup() {
    val deviceSpecState = DeviceSpecCheckState()
    dataProvider = DataProvider {
      when (it) {
        DeviceSpecCheckStateKey.name -> deviceSpecState
        else -> null
      }
    }
  }

  @Test
  fun checkResults() {
    val rule = DimensionParameterRule(name = "foo", defaultNumber = 100)
    fun getRuleCheckResult(): ParameterCheckResult =
      DeviceSpecCheckStateKey.getData(dataProvider)?.getCheckResult("foo")!!

    // Non-number value
    assertFalse(rule.checkValue("abc", dataProvider))
    assertEquals(DimensionParameterCheckResult.BadStatement, getRuleCheckResult())

    // Value with unsupported number (more than one decimal) and missing unit
    assertFalse(rule.checkValue("100.00", dataProvider))
    assertEquals(DimensionParameterCheckResult.BadStatement, getRuleCheckResult())

    // Value with correct number but missing unit
    assertFalse(rule.checkValue("100.0", dataProvider))
    assertEquals(DimensionParameterCheckResult.MissingUnit, getRuleCheckResult())

    // Correct value, the unit is remembered
    assertNull(DeviceSpecCheckStateKey.getData(dataProvider)!!.commonUnit)
    assertTrue(rule.checkValue("100.0dp", dataProvider))
    assertEquals(DimUnit.dp, DeviceSpecCheckStateKey.getData(dataProvider)!!.commonUnit)

    // Force the unit to different value
    DeviceSpecCheckStateKey.getData(dataProvider)!!.commonUnit = DimUnit.px

    // Same value now doesn't match the expected unit
    assertFalse(rule.checkValue("100.0dp", dataProvider))
    assertEquals(DimensionParameterCheckResult.WrongUnit, getRuleCheckResult())

    // Contains expected unit, but not a valid number
    assertFalse(rule.checkValue("abc_px", dataProvider))
    assertEquals(DimensionParameterCheckResult.BadNumber, getRuleCheckResult())
    assertFalse(rule.checkValue("123.45px", dataProvider))
    assertEquals(DimensionParameterCheckResult.BadNumber, getRuleCheckResult())
  }

  @Test
  fun attemptFix() {
    val rule = DimensionParameterRule(name = "bar", defaultNumber = 100)

    // Calling fix before checkValue
    assertFails { rule.attemptFix("abc", dataProvider) }

    fun checkAndFix(valueToCheckAndFix: String): String? {
      rule.checkValue(valueToCheckAndFix, dataProvider)
      return rule.attemptFix(valueToCheckAndFix, dataProvider)
    }

    // Bad statement -> default number of parameter rule + default unit
    assertEquals("100dp", checkAndFix("abc"))

    // Missing unit -> add default unit
    assertEquals("200dp", checkAndFix("200"))

    // Bad number -> fix number and add default unit (common unit is only defined on valid values)
    assertEquals("300.1dp", checkAndFix("300.12px"))

    // Valid value -> no change, the common unit is now 'px'
    assertEquals("400px", checkAndFix("400px"))

    // Wrong unit -> substitute with common unit
    assertEquals("500px", checkAndFix("500dp"))

    // Missing unit -> add the common unit
    assertEquals("600px", checkAndFix("600"))

    // Nothing to fix, `700.` can be parsed to a float
    assertEquals("700.px", checkAndFix("700.px"))

    // Rounded to 1 decimal
    assertEquals("800.6px", checkAndFix("800.55px"))

    // Rounded to 1 decimal, simplified to an integer
    assertEquals("900px", checkAndFix("900.04px"))
    assertEquals("901px", checkAndFix("900.95px"))

    // Extract a valid number when possible
    assertEquals("1000.7px", checkAndFix("1000.74ABCpx"))

    // Bad statement -> default number + common unit
    assertEquals("100px", checkAndFix("abcdp"))
  }
}
