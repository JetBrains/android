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

class NlPropertyTypeTest {

  @Test
  fun testValidateBoolean() {
    assertThat(NlPropertyType.BOOLEAN.validateLiteral("")).isNull()
    assertThat(NlPropertyType.BOOLEAN.validateLiteral("true")).isNull()
    assertThat(NlPropertyType.BOOLEAN.validateLiteral("false")).isNull()
    assertThat(NlPropertyType.BOOLEAN.validateLiteral("wednesday"))
      .isEqualTo("Invalid bool value: 'wednesday'")
  }

  @Test
  fun testValidateThreeStateBoolean() {
    assertThat(NlPropertyType.THREE_STATE_BOOLEAN.validateLiteral("")).isNull()
    assertThat(NlPropertyType.THREE_STATE_BOOLEAN.validateLiteral("true")).isNull()
    assertThat(NlPropertyType.THREE_STATE_BOOLEAN.validateLiteral("false")).isNull()
    assertThat(NlPropertyType.THREE_STATE_BOOLEAN.validateLiteral("wednesday"))
      .isEqualTo("Invalid bool value: 'wednesday'")
  }

  @Test
  fun testValidateColor() {
    assertThat(NlPropertyType.COLOR.validateLiteral("")).isNull()
    assertThat(NlPropertyType.COLOR.validateLiteral("#123")).isNull()
    assertThat(NlPropertyType.COLOR.validateLiteral("#1BCCFF")).isNull()
    assertThat(NlPropertyType.COLOR.validateLiteral("#EE1BCCFF")).isNull()
    assertThat(NlPropertyType.COLOR.validateLiteral("wednesday"))
      .isEqualTo("Invalid color value: 'wednesday'")
  }

  @Test
  fun testValidateDimension() {
    assertThat(NlPropertyType.DIMENSION.validateLiteral("")).isNull()
    assertThat(NlPropertyType.DIMENSION.validateLiteral("20dp")).isNull()
    assertThat(NlPropertyType.DIMENSION.validateLiteral("15dip")).isNull()
    assertThat(NlPropertyType.DIMENSION.validateLiteral("12sp")).isNull()
    assertThat(NlPropertyType.DIMENSION.validateLiteral("200px")).isNull()
    assertThat(NlPropertyType.DIMENSION.validateLiteral("200pixels"))
      .isEqualTo("Unknown units 'pixels'")
    assertThat(NlPropertyType.DIMENSION.validateLiteral("wednesday"))
      .isEqualTo("Cannot resolve: 'wednesday'")
  }

  @Test
  fun testValidateDimensionUnitLess() {
    assertThat(NlPropertyType.DIMENSION_UNIT_LESS.validateLiteral("")).isNull()
    assertThat(NlPropertyType.DIMENSION_UNIT_LESS.validateLiteral("20dp")).isNull()
    assertThat(NlPropertyType.DIMENSION_UNIT_LESS.validateLiteral("15dip")).isNull()
    assertThat(NlPropertyType.DIMENSION_UNIT_LESS.validateLiteral("12sp")).isNull()
    assertThat(NlPropertyType.DIMENSION_UNIT_LESS.validateLiteral("200px")).isNull()
    assertThat(NlPropertyType.DIMENSION_UNIT_LESS.validateLiteral("200pixels"))
      .isEqualTo("Unknown units 'pixels'")
    assertThat(NlPropertyType.DIMENSION_UNIT_LESS.validateLiteral("1.25")).isNull()
    assertThat(NlPropertyType.DIMENSION_UNIT_LESS.validateLiteral(".875")).isNull()
    assertThat(NlPropertyType.DIMENSION_UNIT_LESS.validateLiteral("78")).isNull()
    assertThat(NlPropertyType.DIMENSION_UNIT_LESS.validateLiteral("wednesday"))
      .isEqualTo("Cannot resolve: 'wednesday'")
    assertThat(NlPropertyType.DIMENSION_UNIT_LESS.validateLiteral("any"))
      .isEqualTo("Cannot resolve: 'any'")
  }

  @Test
  fun testValidateFontSize() {
    assertThat(NlPropertyType.FONT_SIZE.validateLiteral("")).isNull()
    assertThat(NlPropertyType.FONT_SIZE.validateLiteral("20dp")).isNull()
    assertThat(NlPropertyType.FONT_SIZE.validateLiteral("15dip")).isNull()
    assertThat(NlPropertyType.FONT_SIZE.validateLiteral("12sp")).isNull()
    assertThat(NlPropertyType.FONT_SIZE.validateLiteral("200px")).isNull()
    assertThat(NlPropertyType.FONT_SIZE.validateLiteral("200pixels"))
      .isEqualTo("Unknown units 'pixels'")
    assertThat(NlPropertyType.FONT_SIZE.validateLiteral("wednesday"))
      .isEqualTo("Cannot resolve: 'wednesday'")
  }

  @Test
  fun testValidateEnum() {
    assertThat(NlPropertyType.ENUM.validateLiteral("")).isNull()
    assertThat(NlPropertyType.ENUM.validateLiteral("any")).isEqualTo("Invalid value: 'any'")
  }

  @Test
  fun testValidateFraction() {
    assertThat(NlPropertyType.FRACTION.validateLiteral("")).isNull()
    assertThat(NlPropertyType.FRACTION.validateLiteral("1.25")).isNull()
    assertThat(NlPropertyType.FRACTION.validateLiteral(".875")).isNull()
    assertThat(NlPropertyType.FRACTION.validateLiteral("78")).isNull()
    assertThat(NlPropertyType.FRACTION.validateLiteral("any")).isEqualTo("Invalid fraction: 'any'")
  }

  @Test
  fun testValidateFloat() {
    assertThat(NlPropertyType.FLOAT.validateLiteral("")).isNull()
    assertThat(NlPropertyType.FLOAT.validateLiteral("1.25")).isNull()
    assertThat(NlPropertyType.FLOAT.validateLiteral(".875")).isNull()
    assertThat(NlPropertyType.FLOAT.validateLiteral("78")).isNull()
    assertThat(NlPropertyType.FLOAT.validateLiteral("any")).isEqualTo("Invalid float: 'any'")
  }

  @Test
  fun testValidateInteger() {
    assertThat(NlPropertyType.INTEGER.validateLiteral("")).isNull()
    assertThat(NlPropertyType.INTEGER.validateLiteral("78")).isNull()
    assertThat(NlPropertyType.INTEGER.validateLiteral("-15")).isNull()
    assertThat(NlPropertyType.INTEGER.validateLiteral("1.5")).isEqualTo("Invalid integer: '1.5'")
    assertThat(NlPropertyType.INTEGER.validateLiteral("any")).isEqualTo("Invalid integer: 'any'")
  }

  @Test
  fun testValidateId() {
    assertThat(NlPropertyType.ID.validateLiteral("")).isNull()
    assertThat(NlPropertyType.ID.validateLiteral("any")).isEqualTo("Invalid id: 'any'")
  }
}
