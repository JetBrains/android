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
package com.android.tools.idea.templates

import freemarker.template.SimpleScalar
import freemarker.template.TemplateModelException
import junit.framework.TestCase
import org.junit.Test

class FmLayoutToFragmentMethodTest {

  @Test
  fun testConvertWithoutPrefix() {
    check("foo", "FooFragment")
  }

  @Test
  fun testConvertWithPrefix() {
    check("fragment_foo", "FooFragment")
  }

  @Test
  fun testConvertPrefixOnly() {
    check("fragment_", "MainFragment")
  }

  @Test
  fun testConvertNameIsSubsetOfFragment() {
    check("fragmen", "FragmenFragment")
  }

  @Test
  fun testConvertEmptyString() {
    check("", "MainFragment")
  }

  @Test
  fun testConvertWithTrailingDigit() {
    check("fragment_foo2", "Foo2Fragment")
  }

  @Test
  fun testConvertWithTrailingDigits() {
    check("fragment_foo200", "Foo200Fragment")
  }

  @Test
  fun testConvertWithOnlyDigits() {
    check("fragment_200", "MainFragment")
  }

  @Throws(TemplateModelException::class)
  private fun check(s: String, expected: String) {
    val method = FmLayoutToFragmentMethod()
    val list = listOf(SimpleScalar(s))
    TestCase.assertEquals(expected, (method.exec(list) as SimpleScalar).asString)
  }
}