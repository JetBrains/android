/*
 * Copyright (C) 2017 The Android Open Source Project
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
import junit.framework.TestCase

class FmEscapeKotlinIdentifierMethodTest : TestCase() {
  private fun check(s: String, expected: String) {
    val method = FmEscapeKotlinIdentifierMethod()
    val list = listOf(SimpleScalar(s))
    TestCase.assertEquals(expected, (method.exec(list) as SimpleScalar).asString)
  }

  fun testLiteral() {
    check("foo", "foo")
  }

  fun testPackage() {
    check("foo.bar.baz", "foo.bar.baz")
  }

  fun testKotlinKeyword() {
    check("in", "`in`")
  }

  fun testPackageWithKeywords() {
    check("foo.in.bar.is", "foo.`in`.bar.`is`")
  }
}
