/*
 * Copyright (C) 2013 The Android Open Source Project
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

class FmCamelCaseToUnderscoreMethodTest : TestCase() {
  @Throws(TemplateModelException::class)
  private fun check(s: String, expected: String) {
    val method = FmCamelCaseToUnderscoreMethod()
    val list = listOf(SimpleScalar(s))
    assertEquals(expected, method.exec(list).toString())
  }

  fun test1() {
    check("", "")
  }

  fun test2() {
    check("foo", "foo")
  }

  fun test3() {
    check("Foo", "foo")
  }

  fun test4() {
    check("FooBar", "foo_bar")
  }

  fun test5() {
    check("testXML", "test_x_m_l")
  }

  fun test6() {
    check("testFoo", "test_foo")
  }
}
