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
package com.android.tools.idea.uibuilder.property

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NelePropertyTypeTest {

  @Test
  fun testValidateBoolean() {
    assertThat(NelePropertyType.BOOLEAN.validateLiteral("")).isNull()
    assertThat(NelePropertyType.BOOLEAN.validateLiteral("true")).isNull()
    assertThat(NelePropertyType.BOOLEAN.validateLiteral("false")).isNull()
    assertThat(NelePropertyType.BOOLEAN.validateLiteral("wednesday")).isEqualTo("Invalid bool value: 'wednesday'")
  }

  @Test
  fun testValidateThreeStateBoolean() {
    assertThat(NelePropertyType.THREE_STATE_BOOLEAN.validateLiteral("")).isNull()
    assertThat(NelePropertyType.THREE_STATE_BOOLEAN.validateLiteral("true")).isNull()
    assertThat(NelePropertyType.THREE_STATE_BOOLEAN.validateLiteral("false")).isNull()
    assertThat(NelePropertyType.THREE_STATE_BOOLEAN.validateLiteral("wednesday")).isEqualTo("Invalid bool value: 'wednesday'")
  }

  @Test
  fun testValidateColor() {
    assertThat(NelePropertyType.COLOR.validateLiteral("")).isNull()
    assertThat(NelePropertyType.COLOR.validateLiteral("#123")).isNull()
    assertThat(NelePropertyType.COLOR.validateLiteral("#1BCCFF")).isNull()
    assertThat(NelePropertyType.COLOR.validateLiteral("#EE1BCCFF")).isNull()
    assertThat(NelePropertyType.COLOR.validateLiteral("wednesday")).isEqualTo("Invalid color value: 'wednesday'")
  }

  @Test
  fun testValidateDimension() {
    assertThat(NelePropertyType.DIMENSION.validateLiteral("")).isNull()
    assertThat(NelePropertyType.DIMENSION.validateLiteral("20dp")).isNull()
    assertThat(NelePropertyType.DIMENSION.validateLiteral("15dip")).isNull()
    assertThat(NelePropertyType.DIMENSION.validateLiteral("12sp"))
    assertThat(NelePropertyType.DIMENSION.validateLiteral("200px"))
    assertThat(NelePropertyType.DIMENSION.validateLiteral("200pixels"))
    assertThat(NelePropertyType.DIMENSION.validateLiteral("wednesday")).isEqualTo("Cannot resolve: 'wednesday'")
  }

  @Test
  fun testValidateDimensionUnitLess() {
    assertThat(NelePropertyType.DIMENSION_UNIT_LESS.validateLiteral("")).isNull()
    assertThat(NelePropertyType.DIMENSION_UNIT_LESS.validateLiteral("20dp")).isNull()
    assertThat(NelePropertyType.DIMENSION_UNIT_LESS.validateLiteral("15dip")).isNull()
    assertThat(NelePropertyType.DIMENSION_UNIT_LESS.validateLiteral("12sp"))
    assertThat(NelePropertyType.DIMENSION_UNIT_LESS.validateLiteral("200px"))
    assertThat(NelePropertyType.DIMENSION_UNIT_LESS.validateLiteral("200pixels"))
    assertThat(NelePropertyType.DIMENSION_UNIT_LESS.validateLiteral("1.25")).isNull()
    assertThat(NelePropertyType.DIMENSION_UNIT_LESS.validateLiteral(".875")).isNull()
    assertThat(NelePropertyType.DIMENSION_UNIT_LESS.validateLiteral("78")).isNull()
    assertThat(NelePropertyType.DIMENSION_UNIT_LESS.validateLiteral("wednesday")).isEqualTo("Cannot resolve: 'wednesday'")
    assertThat(NelePropertyType.DIMENSION_UNIT_LESS.validateLiteral("any")).isEqualTo("Cannot resolve: 'any'")
  }

  @Test
  fun testValidateFontSize() {
    assertThat(NelePropertyType.FONT_SIZE.validateLiteral("")).isNull()
    assertThat(NelePropertyType.FONT_SIZE.validateLiteral("20dp")).isNull()
    assertThat(NelePropertyType.FONT_SIZE.validateLiteral("15dip")).isNull()
    assertThat(NelePropertyType.FONT_SIZE.validateLiteral("12sp"))
    assertThat(NelePropertyType.FONT_SIZE.validateLiteral("200px"))
    assertThat(NelePropertyType.FONT_SIZE.validateLiteral("200pixels"))
    assertThat(NelePropertyType.FONT_SIZE.validateLiteral("wednesday")).isEqualTo("Cannot resolve: 'wednesday'")
  }

  @Test
  fun testValidateEnum() {
    assertThat(NelePropertyType.ENUM.validateLiteral("")).isNull()
    assertThat(NelePropertyType.ENUM.validateLiteral("any")).isEqualTo("Invalid value: 'any'")
  }

  @Test
  fun testValidateFraction() {
    assertThat(NelePropertyType.FRACTION.validateLiteral("")).isNull()
    assertThat(NelePropertyType.FRACTION.validateLiteral("1.25")).isNull()
    assertThat(NelePropertyType.FRACTION.validateLiteral(".875")).isNull()
    assertThat(NelePropertyType.FRACTION.validateLiteral("78")).isNull()
    assertThat(NelePropertyType.FRACTION.validateLiteral("any")).isEqualTo("Invalid fraction: 'any'")
  }

  @Test
  fun testValidateFloat() {
    assertThat(NelePropertyType.FLOAT.validateLiteral("")).isNull()
    assertThat(NelePropertyType.FLOAT.validateLiteral("1.25")).isNull()
    assertThat(NelePropertyType.FLOAT.validateLiteral(".875")).isNull()
    assertThat(NelePropertyType.FLOAT.validateLiteral("78")).isNull()
    assertThat(NelePropertyType.FLOAT.validateLiteral("any")).isEqualTo("Invalid float: 'any'")
  }

  @Test
  fun testValidateInteger() {
    assertThat(NelePropertyType.INTEGER.validateLiteral("")).isNull()
    assertThat(NelePropertyType.INTEGER.validateLiteral("78")).isNull()
    assertThat(NelePropertyType.INTEGER.validateLiteral("-15")).isNull()
    assertThat(NelePropertyType.INTEGER.validateLiteral("1.5")).isEqualTo("Invalid integer: '1.5'")
    assertThat(NelePropertyType.INTEGER.validateLiteral("any")).isEqualTo("Invalid integer: 'any'")
  }

  @Test
  fun testValidateId() {
    assertThat(NelePropertyType.ID.validateLiteral("")).isNull()
    assertThat(NelePropertyType.ID.validateLiteral("any")).isEqualTo("Invalid id: 'any'")
  }
}
