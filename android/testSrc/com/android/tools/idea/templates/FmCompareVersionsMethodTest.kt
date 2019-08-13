/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.templates

import freemarker.template.SimpleNumber
import freemarker.template.SimpleScalar
import junit.framework.TestCase

/**
 * Tests for [FmCompareVersionsIgnoringQualifiersMethod].
 */
class FmCompareVersionsIgnoringQualifiersMethodTest : TestCase() {
  private fun check(lhs: String, rhs: String, expected: Int) {
    val method = FmCompareVersionsIgnoringQualifiersMethod()
    val list = listOf(SimpleScalar(lhs), SimpleScalar(rhs))
    val result = (method.exec(list) as SimpleNumber).asNumber
    assertEquals(expected, Integer.signum(result.toInt()))
  }

  fun testComparison() {
    check("1.2.3", "1.1.0", 1)
    check("1.1.0", "1.1.0", 0)
    check("1.0.0", "1.1.0", -1)
    check("1.3.0-alpha4", "1.1.0", 1)
    check("2.2.0-dev", "1.1.0", 1)
  }
}
