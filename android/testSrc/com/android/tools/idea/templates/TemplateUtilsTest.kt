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

import com.android.tools.idea.templates.TemplateUtils.camelCaseToUnderlines
import com.android.tools.idea.templates.TemplateUtils.extractClassName
import com.android.tools.idea.templates.TemplateUtils.hasExtension
import com.android.tools.idea.templates.TemplateUtils.underlinesToCamelCase
import junit.framework.TestCase
import java.io.File

// TODO cover more functions

class TemplateUtilsTest : TestCase() {
  fun testExtractClassName() {
    mapOf("My Project" to "MyProject",
          "hello" to "Hello",
          "Java's" to "Javas",
          "testXML" to "TestXML"
    ).forEach { (arg, result) ->
      assertEquals(result, extractClassName(arg))
    }
  }

  fun testCamelCaseToUnderlines() {
    mapOf("" to "",
          "foo" to "foo",
          "Foo" to "foo",
          "FooBar" to "foo_bar",
          "testXML" to "test_x_m_l",
          "testFoo" to "test_foo"
    ).forEach { (arg, result) ->
      assertEquals(result, camelCaseToUnderlines(arg))
    }
  }

  fun testUnderlinesToCamelCase() {
    mapOf("" to "",
          "_" to "",
          "foo" to "Foo",
          "foo_bar" to "FooBar",
          "foo__bar" to "FooBar",
          "foo_" to "Foo"
    ).forEach { (arg, result) ->
      assertEquals(result, underlinesToCamelCase(arg))
    }
  }

  fun testHasExtension() {
    val ext = "sh"
    val fileWithExt = File("studio.$ext")
    val fileWithoutExt = File("studio")
    assertTrue(hasExtension(fileWithExt, ext))
    assertTrue(hasExtension(fileWithExt, ".$ext"))
    assertFalse(hasExtension(fileWithoutExt, ext))
  }
}
