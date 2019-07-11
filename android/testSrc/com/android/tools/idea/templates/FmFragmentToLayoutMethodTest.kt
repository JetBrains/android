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

import com.google.common.collect.Lists
import freemarker.template.SimpleScalar
import freemarker.template.TemplateModelException
import junit.framework.TestCase

class FmFragmentToLayoutMethodTest : TestCase() {
  
  @Throws(TemplateModelException::class)
  private fun check(s: String, expected: String) {
    val method = FmFragmentToLayoutMethod()
    val list = listOf(SimpleScalar(s))
    TestCase.assertEquals(expected, (method.exec(list) as SimpleScalar).asString)
  }

  @Throws(TemplateModelException::class)
  private fun check(fragment: String, layoutPrefix: String, expected: String) {
    val method = FmFragmentToLayoutMethod()
    val list = Lists.newArrayList(SimpleScalar(fragment), SimpleScalar(layoutPrefix))
    TestCase.assertEquals(expected, (method.exec(list) as SimpleScalar).asString)
  }

  fun testBasicConversion() {
    check("FooFragment", "fragment_foo")
  }

  fun testConvertPartialSuffix() {
    check("FooFragm", "fragment_foo")
  }

  fun testConvertDoubledSuffix() {
    check("FooFragmentFragment", "fragment_foo_fragment")
  }

  fun testConvertNoSuffix() {
    check("Foo", "fragment_foo")
  }

  fun testConvertSuffixOnly() {
    check("Fragment", "fragment_")
  }

  fun testConvertFragmentFragment() {
    check("FragmentFragment", "fragment_fragment")
  }

  fun testConvertFragmentAsMiddleWord() {
    check("MainFragmentTest1", "fragment_main_test1")
  }

  fun testConvertFragmentFragmentWithBaseName() {
    check("BaseNameFragmentFragm", "fragment", "fragment_base_name_fragment")
  }

  fun testConvertEmpty() {
    check("", "")
  }

  fun testConvertLowercaseCharFragmentName() {
    check("x", "fragment_x")
  }

  fun testConvertUppercaseCharFragmentName() {
    check("X", "fragment_x")
  }

  fun testFragmentNameSubsetOfTheWordFragment() {
    check("Fr", "fragment_")
  }

  fun testFragmentNameSubsetOfTheWordFragmentLowercase() {
    check("fr", "fragment_fr")
  }

  fun testConvertTrailingDigit() {
    check("FooFragment2", "fragment_foo2")
  }

  fun testConvertTrailingDigits() {
    check("FooFragment200", "fragment_foo200")
  }

  fun testConvertTrailingDigitsOnlyFragment() {
    check("Fragment200", "fragment_200")
  }

  fun testCustomPrefixSingleWord() {
    check("MainFragment", "simple", "simple_main")
  }

  fun testCustomPrefixMultipleWords() {
    check("FullScreenFragment", "content", "content_full_screen")
  }
}
